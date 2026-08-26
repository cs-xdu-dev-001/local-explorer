package com.localexplorer.service.impl;

import com.localexplorer.config.ExportJobProperties;
import com.localexplorer.domain.ExportFileFormat;
import com.localexplorer.entity.ExportJob;
import com.localexplorer.exception.ExportCanceledException;
import com.localexplorer.exception.ExportLimitExceededException;
import com.localexplorer.metrics.ExportJobMetrics;
import com.localexplorer.service.ExportFileGenerator;
import com.localexplorer.service.ExportJobTransactionService;
import com.localexplorer.storage.ExportFileStorage;
import com.localexplorer.storage.StoredExportFile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@Slf4j
public class ExportJobProcessor {

    @Autowired private ExportJobTransactionService transactionService;
    @Autowired private ExportFileGenerator generator;
    @Autowired private ExportFileStorage fileStorage;
    @Autowired private ExportJobProperties properties;
    @Autowired private Clock clock;
    @Autowired private ExportJobMetrics metrics;
    @Autowired @Qualifier("exportLeaseTaskScheduler") private TaskScheduler leaseTaskScheduler;
    private String leaseOwner = defaultLeaseOwner();

    public boolean process(String jobId) {
        long startedNanos = System.nanoTime();
        LocalDateTime now = now();
        ExportJob job = transactionService.claim(jobId, leaseOwner, now);
        if (job == null) return false;
        metrics.record(Boolean.TRUE.equals(job.getRecovered()) ? "recovered" : "claimed",
                job.getExportType(), job.getFileFormat());
        if (job.getCreateTime() != null) {
            metrics.recordQueueDelay(Duration.between(job.getCreateTime(), now).toMillis(), job.getExportType());
        }
        Path temp = null;
        StoredExportFile stored = null;
        AtomicBoolean leaseValid = new AtomicBoolean(true);
        ScheduledFuture<?> heartbeat = startHeartbeat(job, leaseValid);
        try {
            ExportFileFormat format = ExportFileFormat.valueOf(job.getFileFormat());
            temp = fileStorage.createTempFile(jobId, format.getExtension());
            Path target = temp;
            long rows = generator.generate(job, target,
                    processed -> {
                        if (!leaseValid.get()) {
                            throw new ExportCanceledException("导出任务租约已失效");
                        }
                        transactionService.checkpoint(job, leaseOwner, processed, now());
                    });
            stored = fileStorage.commit(temp, jobId, format.getExtension());
            temp = null;
            if (stored.getSize() > properties.getMaxFileBytes()) {
                fileStorage.delete(stored.getRelativePath());
                throw new ExportLimitExceededException(ExportLimitExceededException.FILE_TOO_LARGE,
                        "导出文件超过大小限制");
            }
            LocalDateTime finishedAt = now();
            boolean completed = transactionService.complete(job, leaseOwner, stored, rows, finishedAt,
                    finishedAt.plusHours(properties.getFileTtlHours()));
            if (!completed) {
                fileStorage.delete(stored.getRelativePath());
                return false;
            }
            metrics.record("succeeded", job.getExportType(), job.getFileFormat());
            metrics.recordFile(rows, stored.getSize(), System.nanoTime() - startedNanos,
                    job.getExportType(), job.getFileFormat());
            log.info("Export job jobId={} operatorId={} processedRows={} fileBytes={} result=succeeded elapsedMs={}",
                    jobId, job.getOperatorId(), rows, stored.getSize(),
                    java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos));
            return true;
        } catch (ExportCanceledException ex) {
            deleteQuietly(temp);
            deleteQuietly(stored);
            metrics.record("canceled", job.getExportType(), job.getFileFormat());
            log.info("Export job jobId={} operatorId={} result=canceled_or_lease_lost", jobId, job.getOperatorId());
            return false;
        } catch (RuntimeException | IOException ex) {
            deleteQuietly(temp);
            deleteQuietly(stored);
            transactionService.fail(job, leaseOwner, now(), ex);
            metrics.recordRetry((job.getRetryCount() == null ? 0 : job.getRetryCount()) + 1,
                    job.getExportType(), job.getFileFormat());
            metrics.record("failed", job.getExportType(), job.getFileFormat());
            log.warn("Export job jobId={} operatorId={} result=failed", jobId, job.getOperatorId());
            return false;
        } finally {
            heartbeat.cancel(false);
        }
    }

    private ScheduledFuture<?> startHeartbeat(ExportJob job, AtomicBoolean leaseValid) {
        long intervalMillis = Math.max(1000L, properties.getHeartbeatSeconds() * 1000L);
        Date firstRun = new Date(clock.millis() + intervalMillis);
        return leaseTaskScheduler.scheduleAtFixedRate(() -> {
            try {
                if (!transactionService.heartbeat(job.getJobId(), leaseOwner, now())) {
                    leaseValid.set(false);
                    log.warn("Export lease jobId={} operatorId={} result=lost", job.getJobId(), job.getOperatorId());
                }
            } catch (RuntimeException ex) {
                leaseValid.set(false);
                log.warn("Export lease jobId={} operatorId={} result=heartbeat_failed",
                        job.getJobId(), job.getOperatorId());
            }
        }, firstRun, intervalMillis);
    }

    private void deleteQuietly(Path path) {
        if (path == null) return;
        try {
            Files.deleteIfExists(path);
        } catch (IOException ex) {
            log.warn("Export temp cleanup failed jobTemp=true");
        }
    }

    private void deleteQuietly(StoredExportFile stored) {
        if (stored == null) return;
        try {
            fileStorage.delete(stored.getRelativePath());
        } catch (IOException | SecurityException ex) {
            log.warn("Export stored cleanup failed");
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private String defaultLeaseOwner() {
        return System.getProperty("user.name", "node") + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
