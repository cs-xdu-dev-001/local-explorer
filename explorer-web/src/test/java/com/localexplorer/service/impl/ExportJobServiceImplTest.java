package com.localexplorer.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.localexplorer.config.ExportJobProperties;
import com.localexplorer.constant.ErrorCode;
import com.localexplorer.context.BaseContext;
import com.localexplorer.domain.ExportJobStatus;
import com.localexplorer.domain.ExportQuerySnapshot;
import com.localexplorer.dto.CreateExportJobDTO;
import com.localexplorer.entity.ExportJob;
import com.localexplorer.exception.BaseException;
import com.localexplorer.mapper.ExportJobMapper;
import com.localexplorer.metrics.ExportJobMetrics;
import com.localexplorer.service.AdminPermissionService;
import com.localexplorer.service.ExportSnapshotPlan;
import com.localexplorer.service.ExportSnapshotService;
import com.localexplorer.storage.ExportFileStorage;
import com.localexplorer.vo.ExportJobCreatedVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExportJobServiceImplTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 24, 14, 0);

    private ExportJobServiceImpl service;
    private ExportJobProperties properties;

    @Mock private ExportJobMapper exportJobMapper;
    @Mock private AdminPermissionService permissionService;
    @Mock private ExportSnapshotService snapshotService;
    @Mock private ExportFileStorage fileStorage;
    @Mock private ExportJobMetrics metrics;

    @BeforeEach
    void setUp() {
        properties = new ExportJobProperties();
        service = new ExportJobServiceImpl();
        ReflectionTestUtils.setField(service, "exportJobMapper", exportJobMapper);
        ReflectionTestUtils.setField(service, "permissionService", permissionService);
        ReflectionTestUtils.setField(service, "snapshotService", snapshotService);
        ReflectionTestUtils.setField(service, "fileStorage", fileStorage);
        ReflectionTestUtils.setField(service, "properties", properties);
        ReflectionTestUtils.setField(service, "objectMapper", new ObjectMapper().findAndRegisterModules());
        ReflectionTestUtils.setField(service, "clock", Clock.fixed(
                Instant.parse("2026-08-24T06:00:00Z"), ZoneId.of("Asia/Shanghai")));
        ReflectionTestUtils.setField(service, "metrics", metrics);
        BaseContext.setCurrentId(9L);
    }

    @AfterEach
    void tearDown() {
        BaseContext.removeCurrentId();
    }

    @Test
    void returnsExistingJobForSameOperatorRequestIdWithoutRebuildingSnapshot() {
        CreateExportJobDTO dto = request("ORDER");
        ExportJob existing = ExportJob.builder().jobId("existing").requestId("req-1")
                .status(ExportJobStatus.PENDING.name()).operatorId(9L).build();
        when(exportJobMapper.getByOperatorAndRequestId(9L, "req-1")).thenReturn(existing);

        ExportJobCreatedVO result = service.create(dto);

        assertThat(result.getJobId()).isEqualTo("existing");
        verify(snapshotService, never()).freeze(any(), any());
        verify(exportJobMapper, never()).insert(any());
    }

    @Test
    void resolvesUniqueIndexRaceAsIdempotentSuccess() {
        CreateExportJobDTO dto = request("ORDER");
        ExportJob winner = ExportJob.builder().jobId("winner").requestId("req-1")
                .status(ExportJobStatus.PENDING.name()).operatorId(9L).build();
        when(exportJobMapper.getByOperatorAndRequestId(9L, "req-1")).thenReturn(null, winner);
        when(exportJobMapper.countActiveByOperator(9L)).thenReturn(0L);
        when(permissionService.isAdmin()).thenReturn(false);
        when(snapshotService.freeze(eq(dto), eq(NOW))).thenReturn(plan(20));
        org.mockito.Mockito.doThrow(new DuplicateKeyException("duplicate"))
                .when(exportJobMapper).insert(any(ExportJob.class));

        ExportJobCreatedVO result = service.create(dto);

        assertThat(result.getJobId()).isEqualTo("winner");
    }

    @Test
    void rejectsSensitiveExportForStaff() {
        when(permissionService.isAdmin()).thenReturn(false);

        assertThatThrownBy(() -> service.create(request("USER")))
                .isInstanceOf(BaseException.class)
                .extracting(ex -> ((BaseException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);

        verify(snapshotService, never()).freeze(any(), any());
    }

    @Test
    void rejectsWhenOperatorAlreadyHasTooManyActiveJobs() {
        properties.setMaxActivePerOperator(2);
        when(permissionService.isAdmin()).thenReturn(false);
        when(exportJobMapper.countActiveByOperator(9L)).thenReturn(2L);

        assertThatThrownBy(() -> service.create(request("ORDER")))
                .isInstanceOf(BaseException.class)
                .extracting(ex -> ((BaseException) ex).getErrorCode())
                .isEqualTo(ErrorCode.EXPORT_JOB_LIMIT);
    }

    @Test
    void staffCannotCancelAnotherEmployeesJob() {
        when(permissionService.isAdmin()).thenReturn(false);
        when(exportJobMapper.getByJobId("job-2")).thenReturn(ExportJob.builder()
                .jobId("job-2").operatorId(10L).status(ExportJobStatus.RUNNING.name()).build());

        assertThatThrownBy(() -> service.cancel("job-2"))
                .isInstanceOf(BaseException.class)
                .extracting(ex -> ((BaseException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    void ownerCanRetryFailedNonSensitiveJob() {
        when(exportJobMapper.getByJobId("job-3")).thenReturn(ExportJob.builder()
                .jobId("job-3").operatorId(9L).exportType("REVIEW")
                .status(ExportJobStatus.FAILED.name()).build());
        when(exportJobMapper.retryFailed("job-3", NOW)).thenReturn(1);

        service.retry("job-3");

        verify(exportJobMapper).retryFailed("job-3", NOW);
    }

    @Test
    void staffCannotDownloadAnotherEmployeesFile() throws Exception {
        when(permissionService.isAdmin()).thenReturn(false);
        when(exportJobMapper.getByJobId("job-other")).thenReturn(ExportJob.builder()
                .jobId("job-other").operatorId(10L).status(ExportJobStatus.SUCCEEDED.name()).build());

        assertThatThrownBy(() -> service.prepareDownload("job-other"))
                .isInstanceOf(BaseException.class)
                .extracting(ex -> ((BaseException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);

        verify(fileStorage, never()).inspect(any());
    }

    @Test
    void rejectsTraversalOrMissingPhysicalFileAsGone() throws Exception {
        ExportJob job = ExportJob.builder().jobId("job-bad-path").operatorId(9L)
                .status(ExportJobStatus.SUCCEEDED.name()).fileFormat("XLSX")
                .filePath("../secret.txt").fileSize(1L).checksum("abc").expiresAt(NOW.plusHours(1)).build();
        when(exportJobMapper.getByJobId("job-bad-path")).thenReturn(job);
        when(fileStorage.inspect("../secret.txt")).thenThrow(new SecurityException("path traversal"));

        assertThatThrownBy(() -> service.prepareDownload("job-bad-path"))
                .isInstanceOf(BaseException.class)
                .extracting(ex -> ((BaseException) ex).getErrorCode())
                .isEqualTo(ErrorCode.EXPORT_FILE_GONE);
    }

    private CreateExportJobDTO request(String type) {
        CreateExportJobDTO dto = new CreateExportJobDTO();
        dto.setRequestId("req-1");
        dto.setExportType(type);
        dto.setFileFormat("CSV");
        return dto;
    }

    private ExportSnapshotPlan plan(long rows) {
        return new ExportSnapshotPlan(ExportQuerySnapshot.builder()
                .exportType("ORDER").fileFormat("CSV").maxId(50L).snapshotAt(NOW).build(), rows);
    }
}
