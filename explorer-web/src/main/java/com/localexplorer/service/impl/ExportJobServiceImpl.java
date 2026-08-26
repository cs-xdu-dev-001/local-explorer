package com.localexplorer.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.localexplorer.config.ExportJobProperties;
import com.localexplorer.constant.ErrorCode;
import com.localexplorer.context.BaseContext;
import com.localexplorer.domain.ExportFileFormat;
import com.localexplorer.domain.ExportJobStatus;
import com.localexplorer.domain.ExportType;
import com.localexplorer.dto.CreateExportJobDTO;
import com.localexplorer.dto.ExportJobPageQueryDTO;
import com.localexplorer.entity.ExportJob;
import com.localexplorer.exception.BaseException;
import com.localexplorer.mapper.ExportJobMapper;
import com.localexplorer.metrics.ExportJobMetrics;
import com.localexplorer.result.PageResult;
import com.localexplorer.service.AdminPermissionService;
import com.localexplorer.service.ExportJobService;
import com.localexplorer.service.ExportSnapshotPlan;
import com.localexplorer.service.ExportSnapshotService;
import com.localexplorer.storage.ExportDownload;
import com.localexplorer.storage.ExportFileStorage;
import com.localexplorer.storage.StoredExportFile;
import com.localexplorer.vo.ExportJobCreatedVO;
import com.localexplorer.vo.ExportJobStatsVO;
import com.localexplorer.vo.ExportJobVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
public class ExportJobServiceImpl implements ExportJobService {

    @Autowired private ExportJobMapper exportJobMapper;
    @Autowired private AdminPermissionService permissionService;
    @Autowired private ExportSnapshotService snapshotService;
    @Autowired private ExportFileStorage fileStorage;
    @Autowired private ExportJobProperties properties;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private Clock clock;
    @Autowired private ExportJobMetrics metrics;

    @Override
    @Transactional
    public ExportJobCreatedVO create(CreateExportJobDTO dto) {
        Long operatorId = requireOperator();
        ExportType type = parseType(dto.getExportType());
        parseFormat(dto.getFileFormat());
        boolean admin = permissionService.isAdmin();
        if (type.isSensitive() && !admin) {
            throw new BaseException(ErrorCode.FORBIDDEN, "仅ADMIN可创建敏感数据导出");
        }
        ExportJob existing = exportJobMapper.getByOperatorAndRequestId(operatorId, dto.getRequestId());
        if (existing != null) {
            metrics.record("idempotent", existing.getExportType(), existing.getFileFormat());
            return created(existing);
        }
        validateTimeRange(dto, now());
        if (exportJobMapper.countActiveByOperator(operatorId) >= properties.getMaxActivePerOperator()) {
            throw new BaseException(ErrorCode.EXPORT_JOB_LIMIT, "进行中的导出任务已达到上限");
        }
        LocalDateTime now = now();
        ExportSnapshotPlan plan = snapshotService.freeze(dto, now);
        if (plan.getTotalRows() > properties.getMaxRows()) {
            throw new BaseException(ErrorCode.EXPORT_JOB_LIMIT, "导出数据超过最大行数限制");
        }
        ExportJob job = ExportJob.builder()
                .jobId(compactUuid())
                .requestId(dto.getRequestId())
                .exportType(type.name())
                .fileFormat(dto.getFileFormat())
                .querySnapshot(writeSnapshot(plan))
                .status(ExportJobStatus.PENDING.name())
                .progress(0)
                .totalRows(plan.getTotalRows())
                .processedRows(0L)
                .retryCount(0)
                .nextRetryAt(now)
                .operatorId(operatorId)
                .createTime(now)
                .updateTime(now)
                .build();
        try {
            exportJobMapper.insert(job);
            metrics.record("created", job.getExportType(), job.getFileFormat());
            return created(job);
        } catch (DuplicateKeyException ex) {
            ExportJob winner = exportJobMapper.getByOperatorAndRequestId(operatorId, dto.getRequestId());
            if (winner != null) {
                metrics.record("idempotent", winner.getExportType(), winner.getFileFormat());
                return created(winner);
            }
            throw ex;
        }
    }

    @Override
    public PageResult pageQuery(ExportJobPageQueryDTO dto) {
        Long operatorId = requireOperator();
        boolean admin = permissionService.isAdmin();
        if (!admin && dto.getOperatorId() != null && !operatorId.equals(dto.getOperatorId())) {
            throw new BaseException(ErrorCode.FORBIDDEN, "不能查询其他员工的导出任务");
        }
        PageHelper.startPage(dto.getPage(), dto.getPageSize());
        Page<ExportJobVO> page = exportJobMapper.pageQuery(dto, admin ? null : operatorId);
        return new PageResult(page.getTotal(), page.getResult());
    }

    @Override
    public ExportJobVO get(String jobId) {
        ExportJob job = requireVisible(jobId);
        return toVO(job);
    }

    @Override
    public void cancel(String jobId) {
        ExportJob job = requireVisible(jobId);
        ExportJobStatus status = ExportJobStatus.valueOf(job.getStatus());
        if (!status.canTransitionTo(ExportJobStatus.CANCELED)
                || exportJobMapper.cancel(jobId, now()) == 0) {
            throw new BaseException(ErrorCode.EXPORT_JOB_CONFLICT, "当前任务状态不能取消");
        }
        metrics.record("canceled", job.getExportType(), job.getFileFormat());
    }

    @Override
    public void retry(String jobId) {
        ExportJob job = requireVisible(jobId);
        if (ExportType.valueOf(job.getExportType()).isSensitive() && !permissionService.isAdmin()) {
            throw new BaseException(ErrorCode.FORBIDDEN, "仅ADMIN可重试敏感数据导出");
        }
        if (job.getStatus() == null || !ExportJobStatus.FAILED.name().equals(job.getStatus())
                || exportJobMapper.retryFailed(jobId, now()) == 0) {
            throw new BaseException(ErrorCode.EXPORT_JOB_CONFLICT, "只有失败任务可以重试");
        }
        metrics.record("retried", job.getExportType(), job.getFileFormat());
    }

    @Override
    public ExportDownload prepareDownload(String jobId) {
        ExportJob job = requireVisible(jobId);
        ExportJobStatus status = ExportJobStatus.valueOf(job.getStatus());
        if (!status.isDownloadable()) {
            ErrorCode code = status == ExportJobStatus.EXPIRED ? ErrorCode.EXPORT_FILE_GONE : ErrorCode.EXPORT_JOB_NOT_READY;
            throw new BaseException(code, status == ExportJobStatus.EXPIRED ? "导出文件已过期" : "导出文件尚不可下载");
        }
        if (job.getExpiresAt() != null && !job.getExpiresAt().isAfter(now())) {
            throw new BaseException(ErrorCode.EXPORT_FILE_GONE, "导出文件已过期");
        }
        try {
            StoredExportFile actual = fileStorage.inspect(job.getFilePath());
            if (!actual.getChecksum().equals(job.getChecksum()) || actual.getSize() != job.getFileSize()) {
                throw new BaseException(ErrorCode.EXPORT_FILE_GONE, "导出文件校验失败");
            }
            ExportFileFormat format = ExportFileFormat.valueOf(job.getFileFormat());
            return new ExportDownload(fileStorage.resolve(job.getFilePath()),
                    safeFileName(job.getFileName(), job.getFileFormat()),
                    format.getContentType(), actual.getSize());
        } catch (IOException | SecurityException ex) {
            throw new BaseException(ErrorCode.EXPORT_FILE_GONE, "导出文件不存在或不可访问");
        }
    }

    @Override
    public ExportJobStatsVO stats() {
        permissionService.requireAdmin();
        ExportJobStatsVO stats = exportJobMapper.stats(now());
        return stats == null ? ExportJobStatsVO.builder()
                .pending(0L).running(0L).succeeded(0L).failed(0L).canceled(0L).expired(0L)
                .expiredLeases(0L).build() : stats;
    }

    private ExportJob requireVisible(String jobId) {
        Long operatorId = requireOperator();
        ExportJob job = exportJobMapper.getByJobId(jobId);
        if (job == null) {
            throw new BaseException(ErrorCode.NOT_FOUND, "导出任务不存在");
        }
        if (!operatorId.equals(job.getOperatorId()) && !permissionService.isAdmin()) {
            throw new BaseException(ErrorCode.FORBIDDEN, "不能访问其他员工的导出任务");
        }
        return job;
    }

    private ExportJobVO toVO(ExportJob job) {
        return ExportJobVO.builder()
                .jobId(job.getJobId()).requestId(job.getRequestId()).exportType(job.getExportType())
                .fileFormat(job.getFileFormat()).status(job.getStatus()).progress(job.getProgress())
                .totalRows(job.getTotalRows()).processedRows(job.getProcessedRows()).fileName(job.getFileName())
                .fileSize(job.getFileSize()).checksum(job.getChecksum()).retryCount(job.getRetryCount())
                .errorCode(job.getErrorCode()).errorMessage(job.getErrorMessage()).operatorId(job.getOperatorId())
                .startedAt(job.getStartedAt()).finishedAt(job.getFinishedAt()).expiresAt(job.getExpiresAt())
                .createTime(job.getCreateTime()).updateTime(job.getUpdateTime()).build();
    }

    private ExportJobCreatedVO created(ExportJob job) {
        return ExportJobCreatedVO.builder().jobId(job.getJobId()).requestId(job.getRequestId())
                .status(job.getStatus()).build();
    }

    private String writeSnapshot(ExportSnapshotPlan plan) {
        try {
            return objectMapper.writeValueAsString(plan.getSnapshot());
        } catch (JsonProcessingException ex) {
            throw new BaseException(ErrorCode.INTERNAL_ERROR, "导出查询快照序列化失败");
        }
    }

    private void validateTimeRange(CreateExportJobDTO dto, LocalDateTime now) {
        LocalDateTime start = dto.getStartTime() == null ? now.minusDays(properties.getMaxRangeDays()) : dto.getStartTime();
        LocalDateTime end = dto.getEndTime() == null ? now : dto.getEndTime();
        if (end.isBefore(start) || Duration.between(start, end).toDays() > properties.getMaxRangeDays()) {
            throw new BaseException(ErrorCode.INVALID_REQUEST, "导出时间范围不正确或超过限制");
        }
    }

    private ExportType parseType(String value) {
        try {
            return ExportType.valueOf(value);
        } catch (RuntimeException ex) {
            throw new BaseException(ErrorCode.INVALID_REQUEST, "导出类型不正确");
        }
    }

    private ExportFileFormat parseFormat(String value) {
        try {
            return ExportFileFormat.valueOf(value);
        } catch (RuntimeException ex) {
            throw new BaseException(ErrorCode.INVALID_REQUEST, "文件格式不正确");
        }
    }

    private Long requireOperator() {
        Long operatorId = BaseContext.getCurrentId();
        if (operatorId == null) {
            throw new BaseException(ErrorCode.AUTHENTICATION_FAILED, "登录状态无效");
        }
        return operatorId;
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private String compactUuid() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private String safeFileName(String value, String format) {
        String extension = "XLSX".equals(format) ? ".xlsx" : ".csv";
        String fallback = "export_" + DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").format(now()) + extension;
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        String sanitized = value.replaceAll("[\\r\\n\\\\/:*?\"<>|]", "_");
        return sanitized.length() > 180 ? sanitized.substring(0, 180) : sanitized;
    }
}
