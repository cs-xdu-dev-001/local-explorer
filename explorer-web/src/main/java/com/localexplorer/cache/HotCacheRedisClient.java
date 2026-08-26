package com.localexplorer.cache;

public interface HotCacheRedisClient {
    long resolveNamespace(String domain, long minimumVersion);

    long incrementNamespace(String domain, long minimumVersion);

    String get(String key);

    void put(String key, String value, long ttlMillis);

    void delete(String key);

    boolean tryLock(String key, String owner, long leaseMillis);

    boolean renewLock(String key, String owner, long leaseMillis);

    void unlock(String key, String owner);

    void publish(String message);
}
