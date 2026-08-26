package com.localexplorer.service;

import com.localexplorer.config.ExportJobProperties;
import com.localexplorer.entity.ExportJob;
import com.localexplorer.exception.ExportCanceledException;
import com.localexplorer.exception.ExportLimitExceededException;
import com.localexplorer.mapper.ExportJobMapper;
import com.localexplorer.storage.StoredExportFile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class ExportJobTransactionService {

    @Autowired private ExportJobMapper exportJobMapper;
    @Autowired private ExportJobProperties properties;
    @Autowired private ExportRetryPolicy retryPolicy;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ExportJob claim(String jobId, String leaseOwner, LocalDateTime now) {
        ExportJob before = exportJobMapper.getByJobId(jobId);
        boolean recovered = before != null && "RUNNING".equals(before.getStatus());
        LocalDateTime leaseUntil = now.plusSeconds(properties.getLeaseSeconds());
        if (exportJobMapper.claim(jobId, leaseOwner, now, leaseUntil) == 0) return null;
        ExportJob claimed = exportJobMapper.getByJobId(jobId);
        if (claimed != null) claimed.setRecovered(recovered);
        return claimed;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void checkpoint(ExportJob job, String leaseOwner, long processedRows, LocalDateTime now) {
        long total = job.getTotalRows() == null ? 0 : job.getTotalRows();
        int progress = total == 0 ? 100 : (int) Math.min(99, processedRows * 100 / total);
        int updated = exportJobMapper.updateProgress(job.getJobId(), leaseOwner, processedRows, progress,
                now, now.plusSeconds(properties.getLeaseSeconds()));
        if (updated == 0) {
            throw new ExportCanceledException("导出任务已取消或租约已失效");
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean heartbeat(String jobId, String leaseOwner, LocalDateTime now) {
        return exportJobMapper.heartbeat(jobId, leaseOwner, now,
                now.plusSeconds(properties.getLeaseSeconds())) == 1;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean complete(ExportJob job, String leaseOwner, StoredExportFile stored, long processedRows,
                            LocalDateTime finishedAt, LocalDateTime expiresAt) {
        String fileName = fileName(job, finishedAt);
        return exportJobMapper.markSucceeded(job.getJobId(), leaseOwner, stored.getRelativePath(), fileName,
                stored.getSize(), stored.getChecksum(), processedRows, finishedAt, expiresAt) == 1;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(ExportJob job, String leaseOwner, LocalDateTime now, Exception error) {
        int retryCount = (job.getRetryCount() == null ? 0 : job.getRetryCount()) + 1;
        String message = retryPolicy.sanitizeError(error.getMessage());
        boolean limitExceeded = error instanceof ExportLimitExceededException;
        String errorCode = limitExceeded
                ? ((ExportLimitExceededException) error).getErrorCode()
                : "EXPORT_GENERATION_FAILED";
        if (limitExceeded || retryPolicy.shouldFailPermanently(retryCount)) {
            exportJobMapper.markFailed(job.getJobId(), leaseOwner, retryCount,
                    errorCode, message, now);
        } else {
            exportJobMapper.markRetry(job.getJobId(), leaseOwner, retryCount,
                    retryPolicy.nextRetryAt(now, retryCount), errorCode, message, now);
        }
    }

    private String fileName(ExportJob job, LocalDateTime now) {
        String type;
        switch (job.getExportType()) {
            case "ORDER": type = "订单"; break;
            case "USER": type = "用户"; break;
            case "REVIEW": type = "评价"; break;
            case "OPERATION_LOG": type = "操作日志"; break;
            default: type = "数据";
        }
        String timestamp = java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").format(now);
        return type + "导出_" + timestamp + "." + job.getFileFormat().toLowerCase();
    }
}
