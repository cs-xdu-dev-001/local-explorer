package com.localexplorer.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HotReadCacheServiceTest {

    private HotCacheProperties properties;
    private FakeRedisClient redis;
    private MutableClock clock;
    private CacheMetrics metrics;

    @BeforeEach
    void setUp() {
        properties = new HotCacheProperties();
        properties.setL1MaximumSize(1000);
        properties.setL1TtlMillis(1000);
        properties.setL2TtlMillis(60000);
        properties.setNullTtlMillis(500);
        properties.setStaleTtlMillis(5000);
        properties.setTtlJitterMillis(0);
        properties.setLockLeaseMillis(2000);
        properties.setLockWaitMillis(1000);
        properties.setLockPollMillis(5);
        properties.setSingleFlightWaitMillis(3000);
        properties.setRedisCircuitOpenMillis(50);
        redis = new FakeRedisClient();
        clock = new MutableClock(Instant.parse("2026-08-24T00:00:00Z"));
        metrics = new CacheMetrics(new SimpleMeterRegistry());
    }

    @Test
    void readsDatabaseOnceThenServesL1() {
        HotReadCacheService service = newService();
        AtomicInteger databaseLoads = new AtomicInteger();

        Integer first = service.get(HotCacheDomain.SHOP_STATUS, "current", () -> {
            databaseLoads.incrementAndGet();
            return 1;
        });
        Integer second = service.get(HotCacheDomain.SHOP_STATUS, "current", () -> {
            databaseLoads.incrementAndGet();
            return 0;
        });

        assertThat(first).isEqualTo(1);
        assertThat(second).isEqualTo(1);
        assertThat(databaseLoads).hasValue(1);
        assertThat(service.snapshot().getL1Hits()).isEqualTo(1);
        assertThat(service.snapshot().getDatabaseLoads()).isEqualTo(1);
    }

    @Test
    void aSecondInstanceReadsL2WithoutCallingDatabase() {
        HotReadCacheService first = newService();
        first.get(HotCacheDomain.SHOP_STATUS, "current", () -> 1);

        HotReadCacheService second = newService();
        AtomicInteger databaseLoads = new AtomicInteger();
        Integer value = second.get(HotCacheDomain.SHOP_STATUS, "current", () -> {
            databaseLoads.incrementAndGet();
            return 0;
        });

        assertThat(value).isEqualTo(1);
        assertThat(databaseLoads).hasValue(0);
        assertThat(second.snapshot().getL2Hits()).isEqualTo(1);
    }

    @Test
    void cachesNullBrieflyToStopPenetration() {
        HotReadCacheService service = newService();
        AtomicInteger databaseLoads = new AtomicInteger();

        Object first = service.get(HotCacheDomain.ITEM_DETAIL, "404", () -> {
            databaseLoads.incrementAndGet();
            return null;
        });
        Object second = service.get(HotCacheDomain.ITEM_DETAIL, "404", () -> {
            databaseLoads.incrementAndGet();
            return new Object();
        });

        assertThat(first).isNull();
        assertThat(second).isNull();
        assertThat(databaseLoads).hasValue(1);
        assertThat(service.snapshot().getNullHits()).isEqualTo(1);
    }

    @Test
    void corruptedOrOldSchemaL2DataIsRemovedAndReloaded() {
        HotReadCacheService first = newService();
        first.get(HotCacheDomain.SHOP_STATUS, "current", () -> 1);
        String key = redis.onlyDataKey();

        redis.values.put(key, "not-json");
        HotReadCacheService corruptedReader = newService();
        AtomicInteger corruptedLoads = new AtomicInteger();
        assertThat(corruptedReader.get(HotCacheDomain.SHOP_STATUS, "current", () -> {
            corruptedLoads.incrementAndGet();
            return 2;
        })).isEqualTo(2);
        assertThat(corruptedLoads).hasValue(1);

        String currentKey = redis.onlyDataKey();
        redis.values.put(currentKey, redis.values.get(currentKey).replace("\"schemaVersion\":1", "\"schemaVersion\":0"));
        HotReadCacheService oldSchemaReader = newService();
        AtomicInteger oldSchemaLoads = new AtomicInteger();
        assertThat(oldSchemaReader.get(HotCacheDomain.SHOP_STATUS, "current", () -> {
            oldSchemaLoads.incrementAndGet();
            return 3;
        })).isEqualTo(3);
        assertThat(oldSchemaLoads).hasValue(1);
    }

    @Test
    void redisFailureFallsBackQuicklyAndRecoveryRefillsL2() throws Exception {
        HotReadCacheService service = newService();
        redis.available = false;

        assertThat(service.get(HotCacheDomain.SHOP_STATUS, "first", () -> 1))
                .isEqualTo(1);
        assertThat(service.snapshot().getRedisDegradations()).isGreaterThanOrEqualTo(1);

        redis.available = true;
        clock.advanceMillis(properties.getRedisCircuitOpenMillis() + 20);
        assertThat(service.get(HotCacheDomain.SHOP_STATUS, "second", () -> 0))
                .isEqualTo(0);
        assertThat(redis.values).isNotEmpty();
    }

    @Test
    void returnsStaleL1OnlyInsideConfiguredWindowWhenDatabaseFails() throws Exception {
        HotReadCacheService service = newService();
        assertThat(service.get(HotCacheDomain.SHOP_STATUS, "current", () -> 1))
                .isEqualTo(1);

        clock.advanceMillis(1500);
        redis.available = false;
        Integer staleValue = service.get(HotCacheDomain.SHOP_STATUS, "current", () -> {
            throw new IllegalStateException("database down");
        });
        assertThat(staleValue).isEqualTo(1);

        clock.advanceMillis(properties.getL2TtlMillis() + properties.getStaleTtlMillis());
        assertThatThrownBy(() -> service.get(HotCacheDomain.SHOP_STATUS, "current", () -> {
            throw new IllegalStateException("database still down");
        })).isInstanceOf(IllegalStateException.class)
                .hasMessage("database still down");
    }

    @Test
    void l2RemainsFreshUntilItsAbsoluteDeadlineAndCannotBeExtendedByReading() {
        HotReadCacheService first = newService();
        first.get(HotCacheDomain.SHOP_STATUS, "absolute-age", () -> 1);

        clock.advanceMillis(59000);
        HotReadCacheService second = newService();
        assertThat(second.get(HotCacheDomain.SHOP_STATUS, "absolute-age", () -> 0)).isEqualTo(1);

        clock.advanceMillis(1500);
        AtomicInteger databaseLoads = new AtomicInteger();
        assertThat(second.get(HotCacheDomain.SHOP_STATUS, "absolute-age", () -> {
            databaseLoads.incrementAndGet();
            return 2;
        })).isEqualTo(2);
        assertThat(databaseLoads).hasValue(1);
    }

    @Test
    void lockWaitTimeoutAllowsBoundedDatabaseFallbackButNeverWritesSharedL2() {
        properties.setLockWaitMillis(20);
        redis.locks.put("preheld", "other-instance");
        redis.rejectAllLocks = true;
        HotReadCacheService service = newService();

        assertThat(service.get(HotCacheDomain.SHOP_STATUS, "lock-timeout", () -> 1)).isEqualTo(1);

        assertThat(redis.values).isEmpty();
        assertThat(service.snapshot().getLockContentions()).isEqualTo(1);
    }

    @Test
    void malformedInvalidationMessageCannotDiscardValidL1() {
        HotReadCacheService service = newService();
        assertThat(service.get(HotCacheDomain.SHOP_STATUS, "current", () -> 1)).isEqualTo(1);
        redis.values.clear();

        service.acceptInvalidation("{\"domain\":\"SHOP_STATUS\",\"mode\":\"all\"}");

        assertThat(service.get(HotCacheDomain.SHOP_STATUS, "current", () -> 0)).isEqualTo(1);
    }

    @Test
    void oneHundredConcurrentColdReadsUseOneDatabaseLoad() throws Exception {
        HotReadCacheService service = newService();
        AtomicInteger databaseLoads = new AtomicInteger();
        int threads = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            futures.add(executor.submit(() -> {
                ready.countDown();
                start.await(3, TimeUnit.SECONDS);
                return service.get(HotCacheDomain.SHOP_STATUS, "hot", () -> {
                    databaseLoads.incrementAndGet();
                    Thread.sleep(50);
                    return 1;
                });
            }));
        }
        assertThat(ready.await(3, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        for (Future<Integer> future : futures) {
            assertThat(future.get(5, TimeUnit.SECONDS)).isEqualTo(1);
        }
        executor.shutdownNow();

        assertThat(databaseLoads).hasValue(1);
        assertThat(service.snapshot().getSingleFlightFollowers()).isEqualTo(threads - 1);
    }

    @Test
    void twoInstancesUseDistributedLockToLimitColdDatabaseLoad() throws Exception {
        HotReadCacheService first = newService();
        HotReadCacheService second = newService();
        AtomicInteger databaseLoads = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);

        Future<Integer> firstResult = executor.submit(() -> {
            start.await();
            return first.get(HotCacheDomain.SHOP_STATUS, "distributed", () -> {
                databaseLoads.incrementAndGet();
                Thread.sleep(80);
                return 1;
            });
        });
        Future<Integer> secondResult = executor.submit(() -> {
            start.await();
            return second.get(HotCacheDomain.SHOP_STATUS, "distributed", () -> {
                databaseLoads.incrementAndGet();
                Thread.sleep(80);
                return 0;
            });
        });
        start.countDown();

        Integer firstValue = firstResult.get(3, TimeUnit.SECONDS);
        Integer secondValue = secondResult.get(3, TimeUnit.SECONDS);
        assertThat(firstValue).isIn(0, 1);
        assertThat(secondValue).isEqualTo(firstValue);
        executor.shutdownNow();
        assertThat(databaseLoads).hasValue(1);
    }

    @Test
    void slowDatabaseLoadRenewsDistributedLockLease() {
        properties.setLockLeaseMillis(60);
        HotReadCacheService service = newService();

        assertThat(service.get(HotCacheDomain.SHOP_STATUS, "slow-load", () -> {
            Thread.sleep(180);
            return 1;
        })).isEqualTo(1);

        assertThat(redis.renewals).hasValueGreaterThanOrEqualTo(1);
    }

    @Test
    void namespaceMessageInvalidatesAnotherInstancesL1() {
        HotReadCacheService first = newService();
        HotReadCacheService second = newService();
        first.get(HotCacheDomain.SHOP_STATUS, "current", () -> 1);
        assertThat(second.get(HotCacheDomain.SHOP_STATUS, "current", () -> 0)).isEqualTo(1);

        first.invalidateAll(HotCacheDomain.SHOP_STATUS);
        second.acceptInvalidation(redis.lastMessage);

        assertThat(second.get(HotCacheDomain.SHOP_STATUS, "current", () -> 0)).isEqualTo(0);
    }

    private HotReadCacheService newService() {
        return new HotReadCacheService(new ObjectMapper(), properties, redis, metrics, clock);
    }

    private static final class FakeRedisClient implements HotCacheRedisClient {
        private final Map<String, String> values = new ConcurrentHashMap<>();
        private final Map<String, Long> namespaces = new ConcurrentHashMap<>();
        private final Map<String, String> locks = new ConcurrentHashMap<>();
        private final AtomicInteger renewals = new AtomicInteger();
        private volatile boolean available = true;
        private volatile boolean rejectAllLocks;
        private volatile String lastMessage;

        @Override
        public long resolveNamespace(String domain, long minimumVersion) {
            requireAvailable();
            return namespaces.merge(domain, minimumVersion, Math::max);
        }

        @Override
        public long incrementNamespace(String domain, long minimumVersion) {
            requireAvailable();
            return namespaces.compute(domain, (key, value) -> Math.max(value == null ? 0 : value, minimumVersion) + 1);
        }

        @Override
        public String get(String key) {
            requireAvailable();
            return values.get(key);
        }

        @Override
        public void put(String key, String value, long ttlMillis) {
            requireAvailable();
            values.put(key, value);
        }

        @Override
        public void delete(String key) {
            requireAvailable();
            values.remove(key);
        }

        @Override
        public boolean tryLock(String key, String owner, long leaseMillis) {
            requireAvailable();
            if (rejectAllLocks) {
                return false;
            }
            return locks.putIfAbsent(key, owner) == null;
        }

        @Override
        public boolean renewLock(String key, String owner, long leaseMillis) {
            requireAvailable();
            renewals.incrementAndGet();
            return owner.equals(locks.get(key));
        }

        @Override
        public void unlock(String key, String owner) {
            requireAvailable();
            locks.remove(key, owner);
        }

        @Override
        public void publish(String message) {
            requireAvailable();
            lastMessage = message;
        }

        private void requireAvailable() {
            if (!available) {
                throw new HotCacheRedisException("redis unavailable");
            }
        }

        private String onlyDataKey() {
            return values.keySet().stream()
                    .filter(key -> key.contains(":data:"))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("missing cache data key"));
        }
    }

    private static final class MutableClock extends Clock {
        private volatile Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advanceMillis(long millis) {
            instant = instant.plusMillis(millis);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
