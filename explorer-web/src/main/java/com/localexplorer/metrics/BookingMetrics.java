package com.localexplorer.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@Component
public class BookingMetrics {

    private static final Set<String> RESOURCE_TYPES = new HashSet<>(
            Arrays.asList("item", "package"));
    private static final Set<String> FAILURE_REASONS = new HashSet<>(
            Arrays.asList("capacity", "shop_closed", "not_found", "disabled",
                    "validation", "database", "business", "internal"));

    private final MeterRegistry meterRegistry;

    @Autowired
    public BookingMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordCreated(String resourceType) {
        meterRegistry.counter("local.explorer.booking.created",
                "resource_type", safeResourceType(resourceType)).increment();
    }

    public void recordFailure(String reason) {
        meterRegistry.counter("local.explorer.booking.failed",
                "reason", safeFailureReason(reason)).increment();
    }

    public void recordIdempotentHit() {
        meterRegistry.counter("local.explorer.booking.idempotent").increment();
    }

    public void recordCapacityExhausted(String resourceType) {
        meterRegistry.counter("local.explorer.booking.capacity.exhausted",
                "resource_type", safeResourceType(resourceType)).increment();
    }

    private String safeResourceType(String resourceType) {
        return RESOURCE_TYPES.contains(resourceType) ? resourceType : "unknown";
    }

    private String safeFailureReason(String reason) {
        return FAILURE_REASONS.contains(reason) ? reason : "other";
    }
}
