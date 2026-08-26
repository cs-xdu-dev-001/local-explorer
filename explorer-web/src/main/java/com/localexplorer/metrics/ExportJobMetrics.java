package com.localexplorer.metrics;

import com.localexplorer.vo.ExportJobStatsVO;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class ExportJobMetrics {

    private static final Set<String> TYPES = new HashSet<>(Arrays.asList("ORDER", "USER", "REVIEW", "OPERATION_LOG"));
    private static final Set<String> FORMATS = new HashSet<>(Arrays.asList("CSV", "XLSX"));
    private static final Set<String> RESULTS = new HashSet<>(Arrays.asList(
            "created", "idempotent", "claimed", "recovered", "succeeded", "failed", "canceled", "retried",
            "expired", "rejected", "cleanup_failed"));

    private final MeterRegistry registry;
    private final AtomicLong pending = new AtomicLong();
    private final AtomicLong running = new AtomicLong();
    private final AtomicLong failed = new AtomicLong();

    @Autowired
    public ExportJobMetrics(MeterRegistry registry) {
        this.registry = registry;
        registry.gauge("local.explorer.export.pending", pending);
        registry.gauge("local.explorer.export.running", running);
        registry.gauge("local.explorer.export.failed", failed);
    }

    public void record(String result, String exportType, String format) {
        registry.counter("local.explorer.export.result",
                "result", RESULTS.contains(result) ? result : "other",
                "export_type", TYPES.contains(exportType) ? exportType : "UNKNOWN",
                "format", FORMATS.contains(format) ? format : "UNKNOWN").increment();
    }

    public void recordExecution(long nanos, String result) {
        Timer.builder("local.explorer.export.execution")
                .tag("result", RESULTS.contains(result) ? result : "other")
                .register(registry).record(nanos, TimeUnit.NANOSECONDS);
    }

    public void recordQueueDelay(long millis, String exportType) {
        Timer.builder("local.explorer.export.queue.delay")
                .tag("export_type", safeType(exportType))
                .register(registry).record(Math.max(0L, millis), TimeUnit.MILLISECONDS);
    }

    public void recordRetry(int retryCount, String exportType, String format) {
        registry.summary("local.explorer.export.retry.count",
                "export_type", safeType(exportType), "format", safeFormat(format))
                .record(Math.max(0, retryCount));
    }

    public void recordFile(long rows, long bytes, long nanos, String exportType, String format) {
        registry.summary("local.explorer.export.rows", "export_type", safeType(exportType)).record(rows);
        registry.summary("local.explorer.export.file.bytes", "format", safeFormat(format)).record(bytes);
        double seconds = Math.max(0.001d, nanos / 1_000_000_000d);
        registry.summary("local.explorer.export.rows.per.second", "export_type", safeType(exportType))
                .record(rows / seconds);
    }

    public void updateBacklog(ExportJobStatsVO stats) {
        if (stats == null) return;
        pending.set(value(stats.getPending()));
        running.set(value(stats.getRunning()));
        failed.set(value(stats.getFailed()));
    }

    private String safeType(String value) { return TYPES.contains(value) ? value : "UNKNOWN"; }
    private String safeFormat(String value) { return FORMATS.contains(value) ? value : "UNKNOWN"; }
    private long value(Long value) { return value == null ? 0 : value; }
}
