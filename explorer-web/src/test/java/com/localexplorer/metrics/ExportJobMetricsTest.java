package com.localexplorer.metrics;

import com.localexplorer.vo.ExportJobStatsVO;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExportJobMetricsTest {

    @Test
    void boundsDynamicTagsAndPublishesBacklogGauges() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ExportJobMetrics metrics = new ExportJobMetrics(registry);

        metrics.record("user-123", "job-456", "pdf");
        metrics.recordQueueDelay(125L, "ORDER");
        metrics.recordRetry(2, "ORDER", "CSV");
        metrics.updateBacklog(ExportJobStatsVO.builder().pending(3L).running(2L).failed(1L).build());

        assertThat(registry.get("local.explorer.export.result").tags(
                "result", "other", "export_type", "UNKNOWN", "format", "UNKNOWN").counter().count())
                .isEqualTo(1D);
        assertThat(registry.get("local.explorer.export.pending").gauge().value()).isEqualTo(3D);
        assertThat(registry.get("local.explorer.export.running").gauge().value()).isEqualTo(2D);
        assertThat(registry.get("local.explorer.export.failed").gauge().value()).isEqualTo(1D);
        assertThat(registry.get("local.explorer.export.queue.delay").tag("export_type", "ORDER")
                .timer().count()).isEqualTo(1L);
        assertThat(registry.get("local.explorer.export.retry.count").tags(
                "export_type", "ORDER", "format", "CSV").summary().totalAmount()).isEqualTo(2D);
    }
}
