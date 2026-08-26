package com.localexplorer.config;

import com.localexplorer.cache.HotCacheInvalidationListener;
import com.localexplorer.cache.HotCacheProperties;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RedisConfigurationTest {

    @Test
    void objectRedisTemplateKeepsStringKeysForUserInteractionData() {
        RedisConnectionFactory factory = mock(RedisConnectionFactory.class);

        RedisTemplate<String, Object> template = new RedisConfiguration().redisTemplate(factory);

        assertThat(template.getKeySerializer()).isInstanceOf(StringRedisSerializer.class);
        assertThat(template.getConnectionFactory()).isSameAs(factory);
    }

    @Test
    void invalidationListenerContainerIsConfiguredWithoutStartupPing() {
        RedisConnectionFactory factory = mock(RedisConnectionFactory.class);
        HotCacheInvalidationListener listener = mock(HotCacheInvalidationListener.class);
        HotCacheProperties properties = new HotCacheProperties();

        RedisMessageListenerContainer container = new RedisConfiguration()
                .hotCacheInvalidationContainer(factory, listener, properties);

        assertThat(container).isNotNull();
        assertThat(container.isRunning()).isFalse();
    }
}
