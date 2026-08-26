package com.localexplorer.health;

import com.localexplorer.cache.CacheSnapshot;
import com.localexplorer.cache.HotReadCacheService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

@Component("redisFallbackHealthIndicator")
public class RedisFallbackHealthIndicator implements HealthIndicator {

    private final RedisConnectionFactory connectionFactory;
    private final HotReadCacheService cache;

    @Autowired
    public RedisFallbackHealthIndicator(RedisConnectionFactory connectionFactory, HotReadCacheService cache) {
        this.connectionFactory = connectionFactory;
        this.cache = cache;
    }

    RedisFallbackHealthIndicator(RedisConnectionFactory connectionFactory) {
        this(connectionFactory, null);
    }

    @Override
    public Health health() {
        try (RedisConnection connection = connectionFactory.getConnection()) {
            connection.ping();
            Health.Builder health = Health.up().withDetail("mode", "two-level");
            addCacheDetails(health);
            return health.build();
        } catch (RuntimeException ex) {
            Health.Builder health = Health.status("DEGRADED")
                    .withDetail("mode", "l1-mysql-fallback");
            addCacheDetails(health);
            return health.build();
        }
    }

    private void addCacheDetails(Health.Builder health) {
        if (cache == null) {
            return;
        }
        CacheSnapshot snapshot = cache.snapshot();
        health.withDetail("l1Entries", snapshot.getL1Entries())
                .withDetail("redisCircuitOpen", snapshot.isRedisCircuitOpen())
                .withDetail("lastDegradedAt", snapshot.getLastRedisDegradedAt())
                .withDetail("pendingInvalidations", snapshot.getPendingInvalidations());
    }
}
