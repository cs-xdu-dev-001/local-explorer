package com.localexplorer.cache;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
public class HotCacheInvalidationListener implements MessageListener {
    private final HotReadCacheService cache;

    @Autowired
    public HotCacheInvalidationListener(HotReadCacheService cache) {
        this.cache = cache;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        cache.acceptInvalidation(new String(message.getBody(), StandardCharsets.UTF_8));
    }
}
