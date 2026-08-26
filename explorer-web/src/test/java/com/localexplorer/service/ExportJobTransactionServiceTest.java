package com.localexplorer.service;

import com.localexplorer.config.ExportJobProperties;
import com.localexplorer.entity.ExportJob;
import com.localexplorer.exception.ExportCanceledException;
import com.localexplorer.exception.ExportLimitExceededException;
import com.localexplorer.mapper.ExportJobMapper;
import com.localexplorer.storage.StoredExportFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExportJobTransactionServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 24, 20, 0);
    private ExportJobTransactionService service;
    @Mock private ExportJobMapper mapper;

    @BeforeEach
    void setUp() {
        ExportJobProperties properties = new ExportJobProperties();
        properties.setLeaseSeconds(45);
        service = new ExportJobTransactionService();
        ReflectionTestUtils.setField(service, "exportJobMapper", mapper);
        ReflectionTestUtils.setField(service, "properties", properties);
        ReflectionTestUtils.setField(service, "retryPolicy", new ExportRetryPolicy(3, Duration.ofSeconds(5)));
    }

    @Test
    void reclaimsExpiredRunningJobAndMarksRecovery() {
        ExportJob before = ExportJob.builder().jobId("job-1").status("RUNNING").build();
        ExportJob claimed = ExportJob.builder().jobId("job-1").status("RUNNING").build();
        when(mapper.getByJobId("job-1")).thenReturn(before, claimed);
        when(mapper.claim("job-1", "node-a", NOW, NOW.plusSeconds(45))).thenReturn(1);

        ExportJob result = service.claim("job-1", "node-a", NOW);

        assertThat(result.getRecovered()).isTrue();
    }

    @Test
    void checkpointFailsFastAfterCancelOrLeaseLoss() {
        ExportJob job = ExportJob.builder().jobId("job-1").totalRows(100L).build();
        when(mapper.updateProgress("job-1", "node-a", 20L, 20, NOW, NOW.plusSeconds(45))).thenReturn(0);

        assertThatThrownBy(() -> service.checkpoint(job, "node-a", 20L, NOW))
                .isInstanceOf(ExportCanceledException.class);
    }

    @Test
    void heartbeatRenewsOnlyTheCurrentLeaseOwner() {
        when(mapper.heartbeat("job-1", "node-a", NOW, NOW.plusSeconds(45))).thenReturn(1, 0);

        assertThat(service.heartbeat("job-1", "node-a", NOW)).isTrue();
        assertThat(service.heartbeat("job-1", "node-a", NOW)).isFalse();
    }

    @Test
    void retriesWithBackoffThenMovesToPermanentFailure() {
        ExportJob retrying = ExportJob.builder().jobId("job-1").retryCount(0).build();
        service.fail(retrying, "node-a", NOW, new IllegalStateException("token=secret"));

        verify(mapper).markRetry("job-1", "node-a", 1, NOW.plusSeconds(5),
                "EXPORT_GENERATION_FAILED", "token=***", NOW);

        ExportJob exhausted = ExportJob.builder().jobId("job-2").retryCount(2).build();
        service.fail(exhausted, "node-a", NOW, new IllegalStateException("failed"));

        verify(mapper).markFailed("job-2", "node-a", 3,
                "EXPORT_GENERATION_FAILED", "failed", NOW);
    }

    @Test
    void resourceLimitFailureIsPermanentAndKeepsSpecificCode() {
        ExportJob job = ExportJob.builder().jobId("job-limit").retryCount(0).build();

        service.fail(job, "node-a", NOW,
                new ExportLimitExceededException("EXPORT_FILE_TOO_LARGE", "导出文件超过大小限制"));

        verify(mapper).markFailed("job-limit", "node-a", 1,
                "EXPORT_FILE_TOO_LARGE", "导出文件超过大小限制", NOW);
        verify(mapper, never()).markRetry(eq("job-limit"), any(), any(Integer.class), any(), any(), any(), any());
    }

    @Test
    void completesOnlyThroughLeaseGuardedCas() {
        ExportJob job = ExportJob.builder().jobId("job-1").exportType("ORDER").fileFormat("CSV").build();
        StoredExportFile stored = new StoredExportFile("files/job.csv", 20L, repeat("a", 64));
        when(mapper.markSucceeded("job-1", "node-a", "files/job.csv", "订单导出_20260824_200000.csv",
                20L, repeat("a", 64), 2L, NOW, NOW.plusHours(24))).thenReturn(1);

        assertThat(service.complete(job, "node-a", stored, 2L, NOW, NOW.plusHours(24))).isTrue();
    }

    private String repeat(String value, int count) {
        StringBuilder builder = new StringBuilder(count);
        for (int i = 0; i < count; i++) builder.append(value);
        return builder.toString();
    }
}
