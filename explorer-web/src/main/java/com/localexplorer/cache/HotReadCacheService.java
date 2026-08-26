package com.localexplorer.cache;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@Slf4j
public class HotReadCacheService {
    private static final String MODE_ALL = "all";
    private static final String MODE_KEY = "key";
    private static final int INVALIDATION_PROTOCOL_VERSION = 1;
    private static final ScheduledExecutorService LOCK_WATCHDOG = Executors.newSingleThreadScheduledExecutor(
            new ThreadFactory() {
                @Override
                public Thread newThread(Runnable runnable) {
                    Thread thread = new Thread(runnable, "hot-cache-lock-watchdog");
                    thread.setDaemon(true);
                    return thread;
                }
            });

    private final ObjectMapper objectMapper;
    private final HotCacheProperties properties;
    private final HotCacheRedisClient redis;
    private final CacheMetrics metrics;
    private final Clock clock;
    private final Cache<String, LocalEntry> l1;
    private final Map<HotCacheDomain, AtomicLong> namespaces = new EnumMap<>(HotCacheDomain.class);
    private final Map<String, CompletableFuture<Object>> inFlight = new ConcurrentHashMap<>();
    private final Set<HotCacheDomain> pendingAll = ConcurrentHashMap.newKeySet();
    private final Set<PendingKeyInvalidation> pendingKeys = ConcurrentHashMap.newKeySet();
    private final AtomicLong redisCircuitUntil = new AtomicLong();
    private final AtomicLong lastRedisDegradedAt = new AtomicLong();
    private final AtomicBoolean redisWasDegraded = new AtomicBoolean();

    @Autowired
    public HotReadCacheService(ObjectMapper objectMapper, HotCacheProperties properties,
                               HotCacheRedisClient redis, CacheMetrics metrics, Clock clock) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.redis = redis;
        this.metrics = metrics;
        this.clock = clock;
        this.l1 = Caffeine.newBuilder()
                .maximumSize(Math.max(1, properties.getL1MaximumSize()))
                .expireAfterWrite(Math.max(properties.getL1TtlMillis(), properties.getStaleTtlMillis()),
                        TimeUnit.MILLISECONDS)
                .build();
        metrics.bindL1Size(l1::estimatedSize);
        Arrays.stream(HotCacheDomain.values()).forEach(domain -> namespaces.put(domain, new AtomicLong(1)));
    }

    public <T> T get(HotCacheDomain domain, String businessKey, CacheLoader<T> loader) {
        if (!properties.isEnabled()) {
            return loadDirect(loader);
        }
        long started = System.nanoTime();
        long localNamespace = namespaces.get(domain).get();
        String dataKey = dataKey(domain, localNamespace, businessKey);
        LocalEntry stale = l1.getIfPresent(dataKey);
        if (isFresh(stale)) {
            metrics.hit(domain, "l1", stale.envelope.nullValue, System.nanoTime() - started);
            logAccess(domain, dataKey, "l1", stale.envelope.nullValue ? "null_hit" : "hit", started);
            return value(stale.envelope);
        }

        long namespace = resolveNamespace(domain, localNamespace);
        if (namespace != localNamespace) {
            dataKey = dataKey(domain, namespace, businessKey);
            LocalEntry current = l1.getIfPresent(dataKey);
            if (isFresh(current)) {
                metrics.hit(domain, "l1", current.envelope.nullValue, System.nanoTime() - started);
                logAccess(domain, dataKey, "l1", current.envelope.nullValue ? "null_hit" : "hit", started);
                return value(current.envelope);
            }
            if (current != null) {
                stale = current;
            }
        }

        Envelope l2Value = readL2(domain, dataKey);
        if (l2Value != null) {
            putL1(dataKey, l2Value);
            metrics.hit(domain, "l2", l2Value.nullValue, System.nanoTime() - started);
            logAccess(domain, dataKey, "l2", l2Value.nullValue ? "null_hit" : "hit", started);
            return value(l2Value);
        }
        return singleFlight(domain, businessKey, dataKey, stale, loader);
    }

    public void invalidateAll(HotCacheDomain domain) {
        invalidateLocalDomain(domain);
        if (!properties.isRedisEnabled()) {
            namespaces.get(domain).incrementAndGet();
            metrics.invalidated(domain, MODE_ALL);
            return;
        }
        long current = namespaces.get(domain).get();
        try {
            long next = redisCall(domain, () -> redis.incrementNamespace(domain.getCode(), current));
            namespaces.get(domain).accumulateAndGet(next, Math::max);
            redisCall(domain, () -> {
                redis.publish(message(domain, MODE_ALL, null, next));
                return null;
            });
            pendingAll.remove(domain);
            metrics.invalidated(domain, MODE_ALL);
        } catch (HotCacheRedisException ex) {
            namespaces.get(domain).incrementAndGet();
            pendingAll.add(domain);
            metrics.invalidated(domain, "failure");
        }
    }

    public void invalidate(HotCacheDomain domain, String businessKey) {
        String keyHash = hash(businessKey);
        invalidateLocalKey(domain, keyHash);
        if (!properties.isRedisEnabled()) {
            metrics.invalidated(domain, MODE_KEY);
            return;
        }
        long namespace = namespaces.get(domain).get();
        String key = dataKey(domain, namespace, businessKey);
        try {
            redisCall(domain, () -> {
                redis.delete(key);
                redis.publish(message(domain, MODE_KEY, keyHash, namespace));
                return null;
            });
            pendingKeys.remove(new PendingKeyInvalidation(domain, businessKey));
            metrics.invalidated(domain, MODE_KEY);
        } catch (HotCacheRedisException ex) {
            pendingKeys.add(new PendingKeyInvalidation(domain, businessKey));
            metrics.invalidated(domain, "failure");
        }
    }

    public void acceptInvalidation(String serializedMessage) {
        try {
            JsonNode message = objectMapper.readTree(serializedMessage);
            if (message.path("protocolVersion").asInt(-1) != INVALIDATION_PROTOCOL_VERSION) {
                throw new IllegalArgumentException("unsupported protocol version");
            }
            HotCacheDomain domain = HotCacheDomain.valueOf(message.path("domain").asText());
            String mode = message.path("mode").asText();
            if (MODE_ALL.equals(mode)) {
                long version = message.path("namespaceVersion").asLong(-1);
                if (version < 1) {
                    throw new IllegalArgumentException("missing namespace version");
                }
                namespaces.get(domain).accumulateAndGet(version, Math::max);
                invalidateLocalDomain(domain);
            } else if (MODE_KEY.equals(mode)) {
                String keyHash = message.path("keyHash").asText();
                if (!keyHash.matches("[0-9a-f]{24}")) {
                    throw new IllegalArgumentException("invalid key hash");
                }
                invalidateLocalKey(domain, keyHash);
            } else {
                throw new IllegalArgumentException("unsupported invalidation mode");
            }
        } catch (Exception ex) {
            log.warn("Ignored malformed cache invalidation message");
        }
    }

    public void flushPendingInvalidations() {
        if (!properties.isRedisEnabled()) {
            return;
        }
        for (HotCacheDomain domain : pendingAll.toArray(new HotCacheDomain[0])) {
            invalidateAll(domain);
        }
        for (PendingKeyInvalidation pending : pendingKeys.toArray(new PendingKeyInvalidation[0])) {
            invalidate(pending.domain, pending.businessKey);
        }
    }

    public CacheSnapshot snapshot() {
        return metrics.snapshot(l1.estimatedSize(), isRedisCircuitOpen(), lastRedisDegradedAt.get(),
                pendingAll.size() + pendingKeys.size());
    }

    public void clearLocal() {
        l1.invalidateAll();
    }

    private <T> T singleFlight(HotCacheDomain domain, String businessKey, String dataKey,
                               LocalEntry stale, CacheLoader<T> loader) {
        CompletableFuture<Object> mine = new CompletableFuture<>();
        CompletableFuture<Object> existing = inFlight.putIfAbsent(dataKey, mine);
        if (existing != null) {
            metrics.singleFlightFollower(domain);
            try {
                @SuppressWarnings("unchecked")
                T value = (T) existing.get(properties.getSingleFlightWaitMillis(), TimeUnit.MILLISECONDS);
                return value;
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for cache load", ex);
            } catch (ExecutionException ex) {
                throw propagate(ex.getCause());
            } catch (TimeoutException ex) {
                return loadWithFallback(domain, dataKey, stale, loader, false);
            }
        }
        try {
            T value = loadWithDistributedLock(domain, businessKey, dataKey, stale, loader);
            mine.complete(value);
            return value;
        } catch (RuntimeException ex) {
            mine.completeExceptionally(ex);
            throw ex;
        } finally {
            inFlight.remove(dataKey, mine);
        }
    }

    private <T> T loadWithDistributedLock(HotCacheDomain domain, String businessKey, String dataKey,
                                          LocalEntry stale, CacheLoader<T> loader) {
        Envelope secondCheck = readL2(domain, dataKey);
        if (secondCheck != null) {
            putL1(dataKey, secondCheck);
            metrics.hit(domain, "l2", secondCheck.nullValue, 0);
            return value(secondCheck);
        }
        String lockKey = dataKey + ":lock";
        String owner = UUID.randomUUID().toString();
        boolean acquired = false;
        if (properties.isRedisEnabled() && !isRedisCircuitOpen()) {
            try {
                acquired = redisCall(domain,
                        () -> redis.tryLock(lockKey, owner, properties.getLockLeaseMillis()));
            } catch (HotCacheRedisException ignored) {
                acquired = false;
            }
        }
        if (!acquired && properties.isRedisEnabled() && !isRedisCircuitOpen()) {
            metrics.lockContention(domain);
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(properties.getLockWaitMillis());
            while (System.nanoTime() < deadline) {
                sleep(properties.getLockPollMillis());
                Envelope waited = readL2(domain, dataKey);
                if (waited != null) {
                    putL1(dataKey, waited);
                    metrics.hit(domain, "l2", waited.nullValue, 0);
                    return value(waited);
                }
            }
        }
        LockLease lease = acquired ? new LockLease(domain, lockKey, owner) : null;
        try {
            if (acquired) {
                Envelope lockedCheck = readL2(domain, dataKey);
                if (lockedCheck != null) {
                    putL1(dataKey, lockedCheck);
                    metrics.hit(domain, "l2", lockedCheck.nullValue, 0);
                    return value(lockedCheck);
                }
            }
            return loadWithFallback(domain, dataKey, stale, loader, acquired, lease);
        } finally {
            if (acquired) {
                lease.close();
                try {
                    redisCall(domain, () -> {
                        redis.unlock(lockKey, owner);
                        return null;
                    });
                } catch (HotCacheRedisException ignored) {
                    log.debug("Cache lock lease will expire: domain={}", domain.getCode());
                }
            }
        }
    }

    private <T> T loadWithFallback(HotCacheDomain domain, String dataKey, LocalEntry stale,
                                   CacheLoader<T> loader, boolean populateL2) {
        return loadWithFallback(domain, dataKey, stale, loader, populateL2, null);
    }

    private <T> T loadWithFallback(HotCacheDomain domain, String dataKey, LocalEntry stale,
                                   CacheLoader<T> loader, boolean populateL2, LockLease lease) {
        long started = System.nanoTime();
        try {
            T loaded = loader.load();
            Envelope envelope = envelope(loaded);
            putL1(dataKey, envelope);
            boolean sharedWriteAllowed = populateL2 && (lease == null || lease.confirmOwnership());
            if (sharedWriteAllowed && properties.isRedisEnabled() && !isRedisCircuitOpen()) {
                try {
                    long ttl = Math.max(1, envelope.l2FreshUntil - now());
                    String serialized = serialize(envelope);
                    redisCall(domain, () -> {
                        redis.put(dataKey, serialized, ttl);
                        return null;
                    });
                } catch (HotCacheRedisException ignored) {
                    // L1 and MySQL remain available.
                }
            }
            metrics.databaseLoad(domain, "success", System.nanoTime() - started);
            logAccess(domain, dataKey, "database", "success", started);
            return loaded;
        } catch (Throwable ex) {
            metrics.databaseLoad(domain, "failure", System.nanoTime() - started);
            if (isStaleUsable(stale)) {
                metrics.staleFallback(domain);
                log.warn("Serving bounded stale cache after database failure: domain={}, cacheKeyHash={}",
                        domain.getCode(), shortHash(dataKey));
                logAccess(domain, dataKey, "l1", "stale_fallback", started);
                return value(stale.envelope);
            }
            logAccess(domain, dataKey, "database", "failure", started);
            throw propagate(ex);
        }
    }

    private Envelope readL2(HotCacheDomain domain, String dataKey) {
        if (!properties.isRedisEnabled() || isRedisCircuitOpen()) {
            return null;
        }
        try {
            String serialized = redisCall(domain, () -> redis.get(dataKey));
            if (serialized == null) {
                return null;
            }
            Envelope envelope = deserialize(domain, serialized);
            if (envelope == null) {
                try {
                    redisCall(domain, () -> {
                        redis.delete(dataKey);
                        return null;
                    });
                } catch (HotCacheRedisException ignored) {
                    // The circuit breaker already records the degraded state.
                }
                metrics.corrupted(domain);
            }
            return envelope;
        } catch (HotCacheRedisException ex) {
            return null;
        }
    }

    private long resolveNamespace(HotCacheDomain domain, long localVersion) {
        if (!properties.isRedisEnabled() || isRedisCircuitOpen()) {
            return localVersion;
        }
        try {
            long resolved = redisCall(domain,
                    () -> redis.resolveNamespace(domain.getCode(), localVersion));
            namespaces.get(domain).accumulateAndGet(resolved, Math::max);
            return Math.max(localVersion, resolved);
        } catch (HotCacheRedisException ex) {
            return localVersion;
        }
    }

    private Envelope envelope(Object value) {
        long cachedAt = now();
        boolean nullValue = value == null;
        long l1FreshTtl = nullValue ? properties.getNullTtlMillis() : jittered(properties.getL1TtlMillis());
        long l2FreshTtl = nullValue ? properties.getNullTtlMillis() : jittered(properties.getL2TtlMillis());
        long l2FreshUntil = deadline(cachedAt, l2FreshTtl);
        long staleUntil = nullValue ? l2FreshUntil : deadline(l2FreshUntil, properties.getStaleTtlMillis());
        return new Envelope(properties.getSchemaVersion(), nullValue, cachedAt,
                deadline(cachedAt, l1FreshTtl), l2FreshUntil, staleUntil, value);
    }

    private String serialize(Envelope envelope) {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("schemaVersion", envelope.schemaVersion);
            root.put("nullValue", envelope.nullValue);
            root.put("cachedAt", envelope.cachedAt);
            root.put("freshUntil", envelope.freshUntil);
            root.put("l2FreshUntil", envelope.l2FreshUntil);
            root.put("staleUntil", envelope.staleUntil);
            root.set("payload", objectMapper.valueToTree(envelope.value));
            return objectMapper.writeValueAsString(root);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to serialize hot cache value", ex);
        }
    }

    private Envelope deserialize(HotCacheDomain domain, String serialized) {
        try {
            JsonNode root = objectMapper.readTree(serialized);
            if (root.path("schemaVersion").asInt(-1) != properties.getSchemaVersion()) {
                return null;
            }
            boolean nullValue = root.path("nullValue").asBoolean(false);
            Object value = nullValue ? null : objectMapper.readerFor(domain.javaType(objectMapper))
                    .readValue(root.path("payload"));
            long cachedAt = root.path("cachedAt").asLong(-1);
            long l2FreshUntil = root.path("l2FreshUntil").asLong(-1);
            long staleUntil = root.path("staleUntil").asLong(-1);
            if (cachedAt < 0 || l2FreshUntil < cachedAt || staleUntil < l2FreshUntil || now() > l2FreshUntil) {
                return null;
            }
            long freshTtl = nullValue ? properties.getNullTtlMillis() : properties.getL1TtlMillis();
            return new Envelope(properties.getSchemaVersion(), nullValue, cachedAt,
                    Math.min(deadline(now(), freshTtl), l2FreshUntil), l2FreshUntil, staleUntil, value);
        } catch (Exception ex) {
            return null;
        }
    }

    private void putL1(String dataKey, Envelope envelope) {
        l1.put(dataKey, new LocalEntry(envelope));
    }

    private boolean isFresh(LocalEntry entry) {
        return entry != null && now() <= entry.envelope.freshUntil;
    }

    private boolean isStaleUsable(LocalEntry entry) {
        return entry != null && now() <= entry.envelope.staleUntil;
    }

    private void invalidateLocalDomain(HotCacheDomain domain) {
        String marker = ":data:" + domain.getCode() + ":";
        l1.asMap().keySet().removeIf(key -> key.contains(marker));
    }

    private void invalidateLocalKey(HotCacheDomain domain, String keyHash) {
        String marker = ":data:" + domain.getCode() + ":";
        String suffix = ":k" + keyHash;
        l1.asMap().keySet().removeIf(key -> key.contains(marker) && key.endsWith(suffix));
    }

    private String dataKey(HotCacheDomain domain, long namespace, String businessKey) {
        return properties.getKeyPrefix() + ":data:" + domain.getCode() + ":s" + properties.getSchemaVersion()
                + ":n" + namespace + ":k" + hash(businessKey);
    }

    private String message(HotCacheDomain domain, String mode, String keyHash, long namespaceVersion) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("protocolVersion", INVALIDATION_PROTOCOL_VERSION);
        node.put("domain", domain.name());
        node.put("mode", mode);
        node.put("namespaceVersion", namespaceVersion);
        if (keyHash != null) {
            node.put("keyHash", keyHash);
        }
        return node.toString();
    }

    private String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(String.valueOf(value).getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (int i = 0; i < 12; i++) {
                result.append(String.format("%02x", digest[i]));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private String shortHash(String key) {
        return hash(key).substring(0, 12);
    }

    private long jittered(long baseMillis) {
        long jitter = Math.min(Math.max(0, properties.getTtlJitterMillis()), Math.max(0, baseMillis / 2));
        return jitter == 0 ? baseMillis : baseMillis + ThreadLocalRandom.current().nextLong(jitter + 1);
    }

    private long deadline(long start, long ttlMillis) {
        long ttl = Math.max(1, ttlMillis);
        return start > Long.MAX_VALUE - ttl ? Long.MAX_VALUE : start + ttl;
    }

    private <T> T redisCall(HotCacheDomain domain, RedisCall<T> call) {
        if (isRedisCircuitOpen()) {
            throw new HotCacheRedisException("Redis hot-cache circuit is open");
        }
        try {
            T result = call.execute();
            if (redisWasDegraded.compareAndSet(true, false)) {
                l1.invalidateAll();
                log.info("Hot cache Redis recovered; local cache was cleared for reconciliation");
            }
            return result;
        } catch (HotCacheRedisException ex) {
            markRedisDegraded(domain);
            throw ex;
        } catch (RuntimeException ex) {
            markRedisDegraded(domain);
            throw new HotCacheRedisException("Redis hot-cache operation failed", ex);
        }
    }

    private void markRedisDegraded(HotCacheDomain domain) {
        long timestamp = now();
        lastRedisDegradedAt.set(timestamp);
        redisCircuitUntil.set(timestamp + properties.getRedisCircuitOpenMillis());
        redisWasDegraded.set(true);
        metrics.redisDegraded(domain);
        log.warn("Hot cache Redis degraded: domain={}, requestId={}", domain.getCode(),
                MDC.get("requestId"));
    }

    private boolean isRedisCircuitOpen() {
        return now() < redisCircuitUntil.get();
    }

    private long now() {
        return clock.millis();
    }

    private void logAccess(HotCacheDomain domain, String dataKey, String layer, String result, long startedNanos) {
        log.debug("Hot cache access: requestId={}, cacheKeyHash={}, domain={}, layer={}, result={}, elapsedMs={}",
                MDC.get("requestId"), shortHash(dataKey), domain.getCode(), layer, result,
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos));
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(Math.max(1, millis));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for distributed cache lock", ex);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T value(Envelope envelope) {
        return (T) envelope.value;
    }

    private <T> T loadDirect(CacheLoader<T> loader) {
        try {
            return loader.load();
        } catch (Throwable ex) {
            throw propagate(ex);
        }
    }

    private RuntimeException propagate(Throwable throwable) {
        if (throwable instanceof RuntimeException) {
            return (RuntimeException) throwable;
        }
        return new IllegalStateException("Cache loader failed", throwable);
    }

    @FunctionalInterface
    public interface CacheLoader<T> {
        T load() throws Exception;
    }

    private interface RedisCall<T> {
        T execute();
    }

    private static final class Envelope {
        private final int schemaVersion;
        private final boolean nullValue;
        private final long cachedAt;
        private final long freshUntil;
        private final long l2FreshUntil;
        private final long staleUntil;
        private final Object value;

        private Envelope(int schemaVersion, boolean nullValue, long cachedAt,
                         long freshUntil, long l2FreshUntil, long staleUntil, Object value) {
            this.schemaVersion = schemaVersion;
            this.nullValue = nullValue;
            this.cachedAt = cachedAt;
            this.freshUntil = freshUntil;
            this.l2FreshUntil = l2FreshUntil;
            this.staleUntil = staleUntil;
            this.value = value;
        }
    }

    private static final class LocalEntry {
        private final Envelope envelope;

        private LocalEntry(Envelope envelope) {
            this.envelope = envelope;
        }
    }

    private final class LockLease implements AutoCloseable {
        private final HotCacheDomain domain;
        private final String lockKey;
        private final String owner;
        private final AtomicBoolean held = new AtomicBoolean(true);
        private final ScheduledFuture<?> renewal;

        private LockLease(HotCacheDomain domain, String lockKey, String owner) {
            this.domain = domain;
            this.lockKey = lockKey;
            this.owner = owner;
            long interval = Math.max(10, properties.getLockLeaseMillis() / 3);
            this.renewal = LOCK_WATCHDOG.scheduleAtFixedRate(this::renew,
                    interval, interval, TimeUnit.MILLISECONDS);
        }

        private void renew() {
            if (!held.get()) {
                return;
            }
            try {
                if (!redisCall(domain,
                        () -> redis.renewLock(lockKey, owner, properties.getLockLeaseMillis()))) {
                    held.set(false);
                }
            } catch (RuntimeException ex) {
                held.set(false);
            }
        }

        private boolean confirmOwnership() {
            renew();
            return held.get();
        }

        @Override
        public void close() {
            held.set(false);
            renewal.cancel(false);
        }
    }

    private static final class PendingKeyInvalidation {
        private final HotCacheDomain domain;
        private final String businessKey;

        private PendingKeyInvalidation(HotCacheDomain domain, String businessKey) {
            this.domain = domain;
            this.businessKey = businessKey;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof PendingKeyInvalidation)) {
                return false;
            }
            PendingKeyInvalidation that = (PendingKeyInvalidation) other;
            return domain == that.domain && businessKey.equals(that.businessKey);
        }

        @Override
        public int hashCode() {
            return 31 * domain.hashCode() + businessKey.hashCode();
        }
    }
}
