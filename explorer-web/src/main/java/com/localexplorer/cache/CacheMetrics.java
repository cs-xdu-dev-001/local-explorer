package com.localexplorer.cache;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Gauge;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Supplier;

@Component
public class CacheMetrics {
    private final MeterRegistry registry;
    private final LongAdder l1Hits = new LongAdder();
    private final LongAdder l2Hits = new LongAdder();
    private final LongAdder databaseLoads = new LongAdder();
    private final LongAdder nullHits = new LongAdder();
    private final LongAdder lockContentions = new LongAdder();
    private final LongAdder singleFlightFollowers = new LongAdder();
    private final LongAdder redisDegradations = new LongAdder();
    private final LongAdder invalidations = new LongAdder();
    private final LongAdder staleFallbacks = new LongAdder();
    private final LongAdder corruptedEntries = new LongAdder();

    @Autowired
    public CacheMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void bindL1Size(Supplier<Number> sizeSupplier) {
        Gauge.builder("local.explorer.cache.l1.entries", sizeSupplier,
                        supplier -> supplier.get().doubleValue())
                .register(registry);
    }

    public void hit(HotCacheDomain domain, String layer, boolean nullValue, long elapsedNanos) {
        if ("l1".equals(layer)) {
            l1Hits.increment();
        } else if ("l2".equals(layer)) {
            l2Hits.increment();
        }
        if (nullValue) {
            nullHits.increment();
        }
        registry.counter("local.explorer.cache.access", "domain", domain.getCode(),
                "layer", layer, "result", nullValue ? "null_hit" : "hit").increment();
        timer(domain, layer).record(elapsedNanos, TimeUnit.NANOSECONDS);
    }

    public void databaseLoad(HotCacheDomain domain, String result, long elapsedNanos) {
        databaseLoads.increment();
        registry.counter("local.explorer.cache.access", "domain", domain.getCode(),
                "layer", "database", "result", safeResult(result)).increment();
        timer(domain, "database").record(elapsedNanos, TimeUnit.NANOSECONDS);
    }

    public void lockContention(HotCacheDomain domain) {
        lockContentions.increment();
        registry.counter("local.explorer.cache.lock.contention", "domain", domain.getCode()).increment();
    }

    public void singleFlightFollower(HotCacheDomain domain) {
        singleFlightFollowers.increment();
        registry.counter("local.explorer.cache.singleflight", "domain", domain.getCode(),
                "result", "follower").increment();
    }

    public void redisDegraded(HotCacheDomain domain) {
        redisDegradations.increment();
        registry.counter("local.explorer.cache.redis.degraded", "domain", domain.getCode()).increment();
    }

    public void invalidated(HotCacheDomain domain, String result) {
        invalidations.increment();
        registry.counter("local.explorer.cache.invalidation", "domain", domain.getCode(),
                "result", safeResult(result)).increment();
    }

    public void staleFallback(HotCacheDomain domain) {
        staleFallbacks.increment();
        registry.counter("local.explorer.cache.access", "domain", domain.getCode(),
                "layer", "l1", "result", "stale_fallback").increment();
    }

    public void corrupted(HotCacheDomain domain) {
        corruptedEntries.increment();
        registry.counter("local.explorer.cache.access", "domain", domain.getCode(),
                "layer", "l2", "result", "corrupted").increment();
    }

    public CacheSnapshot snapshot(long l1Entries, boolean circuitOpen, long lastDegradedAt,
                                  int pendingInvalidations) {
        return CacheSnapshot.builder()
                .l1Hits(l1Hits.sum())
                .l2Hits(l2Hits.sum())
                .databaseLoads(databaseLoads.sum())
                .nullHits(nullHits.sum())
                .lockContentions(lockContentions.sum())
                .singleFlightFollowers(singleFlightFollowers.sum())
                .redisDegradations(redisDegradations.sum())
                .invalidations(invalidations.sum())
                .staleFallbacks(staleFallbacks.sum())
                .corruptedEntries(corruptedEntries.sum())
                .l1Entries(l1Entries)
                .redisCircuitOpen(circuitOpen)
                .lastRedisDegradedAt(lastDegradedAt)
                .pendingInvalidations(pendingInvalidations)
                .build();
    }

    private Timer timer(HotCacheDomain domain, String layer) {
        return Timer.builder("local.explorer.cache.load.duration")
                .tag("domain", domain.getCode())
                .tag("layer", layer)
                .register(registry);
    }

    private String safeResult(String result) {
        return "success".equals(result) || "failure".equals(result)
                || "all".equals(result) || "key".equals(result) ? result : "other";
    }
}
