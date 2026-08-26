package com.localexplorer.cache;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

@Component
public class SpringHotCacheRedisClient implements HotCacheRedisClient {
    private static final DefaultRedisScript<Long> RESOLVE_NAMESPACE = new DefaultRedisScript<>(
            "local current=tonumber(redis.call('GET',KEYS[1]) or '0'); " +
                    "local minimum=tonumber(ARGV[1]); " +
                    "if current < minimum then redis.call('SET',KEYS[1],minimum); current=minimum; end; " +
                    "return current;", Long.class);
    private static final DefaultRedisScript<Long> INCREMENT_NAMESPACE = new DefaultRedisScript<>(
            "local current=tonumber(redis.call('GET',KEYS[1]) or '0'); " +
                    "local minimum=tonumber(ARGV[1]); " +
                    "if current < minimum then current=minimum; end; " +
                    "current=current+1; redis.call('SET',KEYS[1],current); return current;", Long.class);
    private static final DefaultRedisScript<Long> RELEASE_LOCK = new DefaultRedisScript<>(
            "if redis.call('GET',KEYS[1]) == ARGV[1] then " +
                    "return redis.call('DEL',KEYS[1]); else return 0; end;", Long.class);
    private static final DefaultRedisScript<Long> RENEW_LOCK = new DefaultRedisScript<>(
            "if redis.call('GET',KEYS[1]) == ARGV[1] then " +
                    "return redis.call('PEXPIRE',KEYS[1],ARGV[2]); else return 0; end;", Long.class);

    private final StringRedisTemplate redisTemplate;
    private final HotCacheProperties properties;

    @Autowired
    public SpringHotCacheRedisClient(StringRedisTemplate redisTemplate, HotCacheProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    @Override
    public long resolveNamespace(String domain, long minimumVersion) {
        Long value = execute(() -> redisTemplate.execute(RESOLVE_NAMESPACE,
                Collections.singletonList(namespaceKey(domain)), String.valueOf(minimumVersion)));
        return value == null ? minimumVersion : value;
    }

    @Override
    public long incrementNamespace(String domain, long minimumVersion) {
        Long value = execute(() -> redisTemplate.execute(INCREMENT_NAMESPACE,
                Collections.singletonList(namespaceKey(domain)), String.valueOf(minimumVersion)));
        if (value == null) {
            throw new HotCacheRedisException("Redis returned no namespace version");
        }
        return value;
    }

    @Override
    public String get(String key) {
        return execute(() -> redisTemplate.opsForValue().get(key));
    }

    @Override
    public void put(String key, String value, long ttlMillis) {
        execute(() -> {
            redisTemplate.opsForValue().set(key, value, ttlMillis, TimeUnit.MILLISECONDS);
            return null;
        });
    }

    @Override
    public void delete(String key) {
        execute(() -> redisTemplate.delete(key));
    }

    @Override
    public boolean tryLock(String key, String owner, long leaseMillis) {
        Boolean acquired = execute(() -> redisTemplate.opsForValue()
                .setIfAbsent(key, owner, leaseMillis, TimeUnit.MILLISECONDS));
        return Boolean.TRUE.equals(acquired);
    }

    @Override
    public boolean renewLock(String key, String owner, long leaseMillis) {
        Long renewed = execute(() -> redisTemplate.execute(RENEW_LOCK,
                Collections.singletonList(key), owner, String.valueOf(leaseMillis)));
        return Long.valueOf(1L).equals(renewed);
    }

    @Override
    public void unlock(String key, String owner) {
        execute(() -> redisTemplate.execute(RELEASE_LOCK, Collections.singletonList(key), owner));
    }

    @Override
    public void publish(String message) {
        execute(() -> {
            redisTemplate.convertAndSend(properties.getInvalidationChannel(), message);
            return null;
        });
    }

    private String namespaceKey(String domain) {
        return properties.getKeyPrefix() + ":namespace:" + domain;
    }

    private <T> T execute(RedisOperation<T> operation) {
        try {
            return operation.run();
        } catch (HotCacheRedisException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new HotCacheRedisException("Redis hot-cache operation failed", ex);
        }
    }

    @FunctionalInterface
    private interface RedisOperation<T> {
        T run();
    }
}
