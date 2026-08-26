package com.localexplorer.task;

import com.localexplorer.config.ExportJobProperties;
import com.localexplorer.mapper.ExportJobMapper;
import com.localexplorer.metrics.ExportJobMetrics;
import com.localexplorer.service.impl.ExportJobProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExportJobDispatchTaskTest {

    private ExportJobMapper mapper;
    private TaskExecutor executor;
    private ExportJobDispatchTask task;

    @BeforeEach
    void setUp() {
        mapper = mock(ExportJobMapper.class);
        executor = mock(TaskExecutor.class);
        task = new ExportJobDispatchTask();
        ReflectionTestUtils.setField(task, "exportJobMapper", mapper);
        ReflectionTestUtils.setField(task, "processor", mock(ExportJobProcessor.class));
        ReflectionTestUtils.setField(task, "properties", new ExportJobProperties());
        ReflectionTestUtils.setField(task, "metrics", mock(ExportJobMetrics.class));
        ReflectionTestUtils.setField(task, "clock", Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
        ReflectionTestUtils.setField(task, "taskExecutor", executor);
        when(mapper.findReadyJobIds(any(), any(Integer.class))).thenReturn(Collections.singletonList("job-1"));
    }

    @Test
    void doesNotQueueSameJobTwiceWhileFirstSubmissionIsStillInFlight() {
        task.scan();
        task.scan();

        verify(executor, times(1)).execute(any(Runnable.class));
    }
}
