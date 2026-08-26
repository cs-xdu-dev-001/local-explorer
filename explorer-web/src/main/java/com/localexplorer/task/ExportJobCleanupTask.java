package com.localexplorer.task;

import com.localexplorer.config.ExportJobProperties;
import com.localexplorer.entity.ExportJob;
import com.localexplorer.mapper.ExportJobMapper;
import com.localexplorer.metrics.ExportJobMetrics;
import com.localexplorer.storage.ExportFileStorage;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(prefix = "explorer.jobs", name = "enabled", havingValue = "true")
@Slf4j
public class ExportJobCleanupTask {

    @Autowired private ExportJobMapper exportJobMapper;
    @Autowired private ExportFileStorage fileStorage;
    @Autowired private ExportJobProperties properties;
    @Autowired private ExportJobMetrics metrics;
    @Autowired private Clock clock;

    @Scheduled(fixedDelayString = "${explorer.export.cleanup-delay-ms:60000}")
    @SchedulerLock(name = "exportJobCleanupTask", lockAtMostFor = "PT2M", lockAtLeastFor = "PT1S")
    public void cleanup() {
        String batchId = UUID.randomUUID().toString().replace("-", "");
        MDC.put("batchId", batchId);
        long started = System.nanoTime();
        try {
            LocalDateTime now = LocalDateTime.now(clock);
            List<String> ids = exportJobMapper.findFilesToDelete(now, properties.getScanBatchSize());
            for (String jobId : ids) {
                cleanupJob(jobId, now);
            }
            Instant tempCutoff = now.minusSeconds(properties.getMaxRuntimeSeconds() * 2)
                    .atZone(ZoneId.systemDefault()).toInstant();
            Instant orphanCutoff = now.minusHours(properties.getFileTtlHours())
                    .atZone(ZoneId.systemDefault()).toInstant();
            fileStorage.cleanupTemporaryFiles(tempCutoff);
            fileStorage.cleanupUnreferencedFiles(new HashSet<>(exportJobMapper.listReferencedFilePaths()), orphanCutoff);
            log.info("Export cleanup batchId={} scanned={} result=succeeded elapsedMs={}", batchId, ids.size(),
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
        } catch (IOException | SecurityException ex) {
            metrics.record("cleanup_failed", "UNKNOWN", "UNKNOWN");
            log.warn("Export cleanup batchId={} result=failed elapsedMs={}", batchId,
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
        } finally {
            MDC.remove("batchId");
        }
    }

    private void cleanupJob(String jobId, LocalDateTime now) {
        ExportJob job = exportJobMapper.getByJobId(jobId);
        if (job == null) return;
        if ("SUCCEEDED".equals(job.getStatus())) {
            exportJobMapper.markExpired(jobId, now);
            job = exportJobMapper.getByJobId(jobId);
        }
        if (job == null || !"EXPIRED".equals(job.getStatus()) || job.getFilePath() == null) return;
        try {
            fileStorage.delete(job.getFilePath());
            exportJobMapper.clearExpiredFile(jobId, now);
            metrics.record("expired", job.getExportType(), job.getFileFormat());
        } catch (IOException | SecurityException ex) {
            metrics.record("cleanup_failed", job.getExportType(), job.getFileFormat());
            log.warn("Export cleanup jobId={} result=retry_later", jobId);
        }
    }
}
