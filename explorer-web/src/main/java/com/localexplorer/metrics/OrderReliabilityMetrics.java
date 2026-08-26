package com.localexplorer.metrics;

import com.localexplorer.vo.OutboxStatsVO;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class OrderReliabilityMetrics {

    private final MeterRegistry registry;
    private final AtomicLong pendingEvents = new AtomicLong();
    private final AtomicLong deadEvents = new AtomicLong();

    @Autowired
    public OrderReliabilityMetrics(MeterRegistry registry) {
        this.registry = registry;
        registry.gauge("local.explorer.outbox.pending", pendingEvents);
        registry.gauge("local.explorer.outbox.dead", deadEvents);
    }

    public void recordExpirationScanned(int count) {
        registry.counter("local.explorer.order.expiration.scanned").increment(count);
    }

    public void recordExpirationResult(String result) {
        registry.counter("local.explorer.order.expiration.result", "result", result).increment();
    }

    public void recordExpirationBatch(long nanos) {
        Timer.builder("local.explorer.order.expiration.batch")
                .register(registry).record(nanos, TimeUnit.NANOSECONDS);
    }

    public void recordOutboxResult(String result) {
        registry.counter("local.explorer.outbox.result", "result", result).increment();
    }

    public void recordOutboxBatch(long nanos) {
        Timer.builder("local.explorer.outbox.batch")
                .register(registry).record(nanos, TimeUnit.NANOSECONDS);
    }

    public void updateBacklog(OutboxStatsVO stats) {
        pendingEvents.set(stats.getPending() == null ? 0 : stats.getPending());
        deadEvents.set(stats.getDead() == null ? 0 : stats.getDead());
    }
}
