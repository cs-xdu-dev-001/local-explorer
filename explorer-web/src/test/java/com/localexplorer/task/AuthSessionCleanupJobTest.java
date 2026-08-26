package com.localexplorer.task;

import com.localexplorer.metrics.AuthenticationMetrics;
import com.localexplorer.service.AuthSessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthSessionCleanupJobTest {

    @Mock private AuthSessionService service;
    @Mock private AuthenticationMetrics metrics;
    private AuthSessionCleanupJob job;

    @BeforeEach
    void setUp() {
        job = new AuthSessionCleanupJob(service, metrics);
    }

    @Test
    void successfulCleanupRecordsSuccessMetric() {
        when(service.cleanup()).thenReturn(4);

        job.run();

        verify(metrics).cleanup(org.mockito.ArgumentMatchers.eq("success"), anyLong());
    }

    @Test
    void failedCleanupIsIsolatedAndRecordsErrorMetric() {
        doThrow(new IllegalStateException("database unavailable")).when(service).cleanup();

        job.run();

        verify(metrics).cleanup(org.mockito.ArgumentMatchers.eq("error"), anyLong());
    }
}
