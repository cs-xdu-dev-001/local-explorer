package com.localexplorer.cache;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class CacheSnapshot {
    long l1Hits;
    long l2Hits;
    long databaseLoads;
    long nullHits;
    long lockContentions;
    long singleFlightFollowers;
    long redisDegradations;
    long invalidations;
    long staleFallbacks;
    long corruptedEntries;
    long l1Entries;
    boolean redisCircuitOpen;
    long lastRedisDegradedAt;
    int pendingInvalidations;
}
