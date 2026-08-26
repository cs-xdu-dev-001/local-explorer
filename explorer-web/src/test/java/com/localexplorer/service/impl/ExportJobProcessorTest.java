package com.localexplorer.service.impl;

import com.localexplorer.config.ExportJobProperties;
import com.localexplorer.entity.ExportJob;
import com.localexplorer.exception.ExportCanceledException;
import com.localexplorer.service.ExportFileGenerator;
import com.localexplorer.service.ExportJobTransactionService;
import com.localexplorer.metrics.ExportJobMetrics;
import com.localexplorer.storage.ExportFileStorage;
import com.localexplorer.storage.StoredExportFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.scheduling.TaskScheduler;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.concurrent.ScheduledFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExportJobProcessorTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 24, 15, 0);

    private ExportJobProcessor processor;

    @Mock private ExportJobTransactionService transactionService;
    @Mock private ExportFileGenerator generator;
    @Mock private ExportFileStorage fileStorage;
    @Mock private ExportJobMetrics metrics;
    @Mock private TaskScheduler leaseTaskScheduler;
    @Mock private ScheduledFuture<?> heartbeatFuture;
    @TempDir Path tempDir;

    @BeforeEach
    void setUp() {
        processor = new ExportJobProcessor();
        ReflectionTestUtils.setField(processor, "transactionService", transactionService);
        ReflectionTestUtils.setField(processor, "generator", generator);
        ReflectionTestUtils.setField(processor, "fileStorage", fileStorage);
        ReflectionTestUtils.setField(processor, "properties", new ExportJobProperties());
        ReflectionTestUtils.setField(processor, "clock", Clock.fixed(
                Instant.parse("2026-08-24T07:00:00Z"), ZoneId.of("Asia/Shanghai")));
        ReflectionTestUtils.setField(processor, "metrics", metrics);
        ReflectionTestUtils.setField(processor, "leaseTaskScheduler", leaseTaskScheduler);
        ReflectionTestUtils.setField(processor, "leaseOwner", "node-a");
        org.mockito.Mockito.lenient().doReturn(heartbeatFuture).when(leaseTaskScheduler)
                .scheduleAtFixedRate(any(Runnable.class), any(Date.class), any(Long.class));
    }

    @Test
    void skipsGenerationWhenCasClaimLoses() throws Exception {
        when(transactionService.claim("job-1", "node-a", NOW)).thenReturn(null);

        assertThat(processor.process("job-1")).isFalse();

        verify(generator, never()).generate(any(), any(), any());
    }

    @Test
    void publishesOnlyAfterFileCommitAndLeaseGuardedCompletion() throws Exception {
        ExportJob job = runningJob();
        Path temp = Files.createTempFile(tempDir, "export", ".part");
        StoredExportFile stored = new StoredExportFile("files/job-1.csv", 18L, repeat("a", 64));
        when(transactionService.claim("job-1", "node-a", NOW)).thenReturn(job);
        when(fileStorage.createTempFile("job-1", "csv")).thenReturn(temp);
        when(generator.generate(eq(job), eq(temp), any())).thenReturn(3L);
        when(fileStorage.commit(temp, "job-1", "csv")).thenReturn(stored);
        when(transactionService.complete(eq(job), eq("node-a"), eq(stored), eq(3L), any(), any()))
                .thenReturn(true);

        assertThat(processor.process("job-1")).isTrue();

        verify(transactionService).complete(eq(job), eq("node-a"), eq(stored), eq(3L), any(), any());
        verify(heartbeatFuture).cancel(false);
    }

    @Test
    void deletesPublishedFileWhenLeaseWasLostBeforeFinalCas() throws Exception {
        ExportJob job = runningJob();
        Path temp = Files.createTempFile(tempDir, "export", ".part");
        StoredExportFile stored = new StoredExportFile("files/job-1.csv", 18L, repeat("b", 64));
        when(transactionService.claim("job-1", "node-a", NOW)).thenReturn(job);
        when(fileStorage.createTempFile("job-1", "csv")).thenReturn(temp);
        when(generator.generate(eq(job), eq(temp), any())).thenReturn(3L);
        when(fileStorage.commit(temp, "job-1", "csv")).thenReturn(stored);
        when(transactionService.complete(eq(job), eq("node-a"), eq(stored), eq(3L), any(), any()))
                .thenReturn(false);

        assertThat(processor.process("job-1")).isFalse();

        verify(fileStorage).delete("files/job-1.csv");
    }

    @Test
    void recordsBoundedFailureAndRemovesTempFile() throws Exception {
        ExportJob job = runningJob();
        Path temp = Files.createTempFile(tempDir, "export", ".part");
        when(transactionService.claim("job-1", "node-a", NOW)).thenReturn(job);
        when(fileStorage.createTempFile("job-1", "csv")).thenReturn(temp);
        when(generator.generate(eq(job), eq(temp), any())).thenThrow(new IllegalStateException("token=secret"));

        assertThat(processor.process("job-1")).isFalse();

        assertThat(Files.exists(temp)).isFalse();
        verify(transactionService).fail(eq(job), eq("node-a"), any(), any(IllegalStateException.class));
    }

    @Test
    void stopsAtNextCheckpointWhenHeartbeatLosesLease() throws Exception {
        ExportJob job = runningJob();
        Path temp = Files.createTempFile(tempDir, "export", ".part");
        when(transactionService.claim("job-1", "node-a", NOW)).thenReturn(job);
        when(transactionService.heartbeat(eq("job-1"), eq("node-a"), any())).thenReturn(false);
        when(fileStorage.createTempFile("job-1", "csv")).thenReturn(temp);
        when(leaseTaskScheduler.scheduleAtFixedRate(any(Runnable.class), any(Date.class), any(Long.class)))
                .thenAnswer(invocation -> {
                    invocation.getArgument(0, Runnable.class).run();
                    return heartbeatFuture;
                });
        when(generator.generate(eq(job), eq(temp), any())).thenAnswer(invocation -> {
            com.localexplorer.service.ExportExecutionControl control = invocation.getArgument(2);
            control.checkpoint(1L);
            return 1L;
        });

        assertThat(processor.process("job-1")).isFalse();

        verify(transactionService, never()).complete(any(), any(), any(), any(Long.class), any(), any());
        verify(heartbeatFuture).cancel(false);
    }

    @Test
    void cancelDuringGenerationRemovesTempWithoutPublishingOrRetrying() throws Exception {
        ExportJob job = runningJob();
        Path temp = Files.createTempFile(tempDir, "export", ".part");
        when(transactionService.claim("job-1", "node-a", NOW)).thenReturn(job);
        when(fileStorage.createTempFile("job-1", "csv")).thenReturn(temp);
        doThrow(new ExportCanceledException("导出任务已取消"))
                .when(transactionService).checkpoint(eq(job), eq("node-a"), eq(1L), any());
        when(generator.generate(eq(job), eq(temp), any())).thenAnswer(invocation -> {
            com.localexplorer.service.ExportExecutionControl control = invocation.getArgument(2);
            control.checkpoint(1L);
            return 1L;
        });

        assertThat(processor.process("job-1")).isFalse();

        assertThat(Files.exists(temp)).isFalse();
        verify(fileStorage, never()).commit(any(), any(), any());
        verify(transactionService, never()).complete(any(), any(), any(), any(Long.class), any(), any());
        verify(transactionService, never()).fail(any(), any(), any(), any());
    }

    private ExportJob runningJob() {
        return ExportJob.builder().jobId("job-1").fileFormat("CSV").exportType("ORDER")
                .status("RUNNING").totalRows(3L).retryCount(0).operatorId(9L).build();
    }

    private String repeat(String value, int count) {
        StringBuilder builder = new StringBuilder(count);
        for (int i = 0; i < count; i++) builder.append(value);
        return builder.toString();
    }
}
