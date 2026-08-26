package com.localexplorer.metrics;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BookingMetricsTest {

    @Test
    void recordsLowCardinalityBookingCounters() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        BookingMetrics metrics = new BookingMetrics(registry);

        metrics.recordCreated("item");
        metrics.recordFailure("capacity");
        metrics.recordIdempotentHit();
        metrics.recordCapacityExhausted("item");

        assertThat(registry.get("local.explorer.booking.created")
                .tag("resource_type", "item").counter().count()).isEqualTo(1);
        assertThat(registry.get("local.explorer.booking.failed")
                .tag("reason", "capacity").counter().count()).isEqualTo(1);
        assertThat(registry.get("local.explorer.booking.idempotent")
                .counter().count()).isEqualTo(1);
        assertThat(registry.get("local.explorer.booking.capacity.exhausted")
                .tag("resource_type", "item").counter().count()).isEqualTo(1);
    }

    @Test
    void collapsesUnexpectedTagsInsteadOfCreatingUnboundedSeries() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        BookingMetrics metrics = new BookingMetrics(registry);

        metrics.recordCreated("user-42");
        metrics.recordFailure("sql-state-23000-request-99");

        assertThat(registry.get("local.explorer.booking.created")
                .tag("resource_type", "unknown").counter().count()).isEqualTo(1);
        assertThat(registry.get("local.explorer.booking.failed")
                .tag("reason", "other").counter().count()).isEqualTo(1);
    }
}
