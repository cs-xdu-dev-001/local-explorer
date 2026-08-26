package com.localexplorer.health;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RedisFallbackHealthIndicatorTest {

    @Test
    void reportsUpWhenRedisResponds() {
        RedisConnectionFactory factory = mock(RedisConnectionFactory.class);
        RedisConnection connection = mock(RedisConnection.class);
        when(factory.getConnection()).thenReturn(connection);
        when(connection.ping()).thenReturn("PONG");

        Health health = new RedisFallbackHealthIndicator(factory).health();

        assertThat(health.getStatus().getCode()).isEqualTo("UP");
        assertThat(health.getDetails()).containsEntry("mode", "two-level");
    }

    @Test
    void reportsDegradedWithoutLeakingConnectionDetailsWhenRedisIsDown() {
        RedisConnectionFactory factory = mock(RedisConnectionFactory.class);
        when(factory.getConnection()).thenThrow(
                new RuntimeException("redis://secret@10.0.0.8:6379 connection refused"));

        Health health = new RedisFallbackHealthIndicator(factory).health();

        assertThat(health.getStatus().getCode()).isEqualTo("DEGRADED");
        assertThat(health.getDetails()).containsEntry("mode", "l1-mysql-fallback");
        assertThat(health.getDetails().toString()).doesNotContain("secret", "10.0.0.8", "6379");
    }
}
