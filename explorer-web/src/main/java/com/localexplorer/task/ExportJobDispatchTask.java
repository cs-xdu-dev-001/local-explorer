package com.localexplorer.task;

import com.localexplorer.config.ExportJobProperties;
import com.localexplorer.mapper.ExportJobMapper;
import com.localexplorer.metrics.ExportJobMetrics;
import com.localexplorer.service.impl.ExportJobProcessor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ConditionalOnProperty(prefix = "explorer.jobs", name = "enabled", havingValue = "true")
@Slf4j
public class ExportJobDispatchTask {

    @Autowired private ExportJobMapper exportJobMapper;
    @Autowired private ExportJobProcessor processor;
    @Autowired private ExportJobProperties properties;
    @Autowired private ExportJobMetrics metrics;
    @Autowired private Clock clock;
    @Autowired @Qualifier("exportTaskExecutor") private TaskExecutor taskExecutor;
    private final Set<String> inFlight = ConcurrentHashMap.newKeySet();

    @Scheduled(fixedDelayString = "${explorer.export.scan-delay-ms:3000}")
    @SchedulerLock(name = "exportJobDispatchTask", lockAtMostFor = "PT10S", lockAtLeastFor = "PT0.1S")
    public void scan() {
        String batchId = compactUuid();
        List<String> ids = exportJobMapper.findReadyJobIds(LocalDateTime.now(clock), properties.getScanBatchSize());
        for (String id : ids) {
            if (!inFlight.add(id)) continue;
            try {
                taskExecutor.execute(() -> runOne(id, batchId));
            } catch (TaskRejectedException ex) {
                inFlight.remove(id);
                metrics.record("rejected", "UNKNOWN", "UNKNOWN");
                log.warn("Export dispatch batchId={} jobId={} result=rejected", batchId, id);
            }
        }
        metrics.updateBacklog(exportJobMapper.stats(LocalDateTime.now(clock)));
        log.info("Export dispatch batchId={} scanned={} result=submitted", batchId, ids.size());
    }

    private void runOne(String jobId, String batchId) {
        MDC.put("batchId", batchId);
        long started = System.nanoTime();
        try {
            boolean result = processor.process(jobId);
            metrics.recordExecution(System.nanoTime() - started, result ? "succeeded" : "failed");
            log.info("Export execution batchId={} jobId={} result={} elapsedMs={}", batchId, jobId,
                    result ? "succeeded" : "not_completed",
                    java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
        } catch (RuntimeException ex) {
            metrics.recordExecution(System.nanoTime() - started, "failed");
            log.error("Export execution batchId={} jobId={} result=failed", batchId, jobId, ex);
        } finally {
            inFlight.remove(jobId);
            MDC.remove("batchId");
        }
    }

    private String compactUuid() { return UUID.randomUUID().toString().replace("-", ""); }
}
