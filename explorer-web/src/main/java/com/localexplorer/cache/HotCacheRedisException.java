package com.localexplorer.cache;

public class HotCacheRedisException extends RuntimeException {
    public HotCacheRedisException(String message, Throwable cause) {
        super(message, cause);
    }

    public HotCacheRedisException(String message) {
        super(message);
    }
}
