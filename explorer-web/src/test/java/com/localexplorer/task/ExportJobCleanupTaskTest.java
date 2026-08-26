package com.localexplorer.task;

import com.localexplorer.config.ExportJobProperties;
import com.localexplorer.entity.ExportJob;
import com.localexplorer.mapper.ExportJobMapper;
import com.localexplorer.metrics.ExportJobMetrics;
import com.localexplorer.storage.ExportFileStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExportJobCleanupTaskTest {

    private static final LocalDateTime NOW = LocalDateTime.of(1970, 1, 1, 0, 0);

    private ExportJobMapper mapper;
    private ExportFileStorage storage;
    private ExportJobMetrics metrics;
    private ExportJobCleanupTask task;

    @BeforeEach
    void setUp() throws Exception {
        mapper = mock(ExportJobMapper.class);
        storage = mock(ExportFileStorage.class);
        metrics = mock(ExportJobMetrics.class);
        task = new ExportJobCleanupTask();
        ReflectionTestUtils.setField(task, "exportJobMapper", mapper);
        ReflectionTestUtils.setField(task, "fileStorage", storage);
        ReflectionTestUtils.setField(task, "properties", new ExportJobProperties());
        ReflectionTestUtils.setField(task, "metrics", metrics);
        ReflectionTestUtils.setField(task, "clock", Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
        when(mapper.listReferencedFilePaths()).thenReturn(Collections.emptyList());
        when(mapper.findFilesToDelete(any(), anyInt())).thenReturn(Collections.singletonList("job-1"));
    }

    @Test
    void expiresThenDeletesFileBeforeClearingDatabaseReference() throws Exception {
        ExportJob succeeded = ExportJob.builder().jobId("job-1").status("SUCCEEDED")
                .exportType("ORDER").fileFormat("CSV").filePath("files/job.csv").build();
        ExportJob expired = ExportJob.builder().jobId("job-1").status("EXPIRED")
                .exportType("ORDER").fileFormat("CSV").filePath("files/job.csv").build();
        when(mapper.getByJobId("job-1")).thenReturn(succeeded, expired);

        task.cleanup();

        org.mockito.InOrder order = inOrder(mapper, storage);
        order.verify(mapper).markExpired("job-1", NOW);
        order.verify(storage).delete("files/job.csv");
        order.verify(mapper).clearExpiredFile("job-1", NOW);
        verify(metrics).record("expired", "ORDER", "CSV");
        verify(storage).cleanupTemporaryFiles(any());
        verify(storage).cleanupUnreferencedFiles(anySet(), any());
    }

    @Test
    void keepsFileReferenceAndRecordsRetryableFailureWhenDeleteFails() throws Exception {
        ExportJob expired = ExportJob.builder().jobId("job-1").status("EXPIRED")
                .exportType("ORDER").fileFormat("XLSX").filePath("files/job.xlsx").build();
        when(mapper.getByJobId("job-1")).thenReturn(expired);
        doThrow(new IOException("disk busy")).when(storage).delete("files/job.xlsx");

        task.cleanup();

        verify(mapper, never()).clearExpiredFile(any(), any());
        verify(metrics).record("cleanup_failed", "ORDER", "XLSX");
    }
}
