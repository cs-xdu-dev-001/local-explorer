package com.localexplorer.task;

import com.localexplorer.mapper.OrderEventOutboxMapper;
import com.localexplorer.metrics.OrderReliabilityMetrics;
import com.localexplorer.service.OrderEventOutboxService;
import com.localexplorer.service.OrderExpirationPolicy;
import com.localexplorer.service.impl.OutboxEventProcessor;
import com.localexplorer.vo.OutboxStatsVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxDispatchJobTest {

    private OutboxDispatchJob job;
    @Mock private OrderEventOutboxMapper outboxMapper;
    @Mock private OutboxEventProcessor processor;
    @Mock private OrderEventOutboxService outboxService;
    @Mock private OrderExpirationPolicy expirationPolicy;
    @Mock private OrderReliabilityMetrics metrics;

    @BeforeEach
    void setUp() {
        job = new OutboxDispatchJob();
        ReflectionTestUtils.setField(job, "outboxMapper", outboxMapper);
        ReflectionTestUtils.setField(job, "processor", processor);
        ReflectionTestUtils.setField(job, "outboxService", outboxService);
        ReflectionTestUtils.setField(job, "expirationPolicy", expirationPolicy);
        ReflectionTestUtils.setField(job, "metrics", metrics);
        ReflectionTestUtils.setField(job, "batchSize", 2);
        ReflectionTestUtils.setField(job, "lockSeconds", 60);
    }

    @Test
    void oneFailedEventDoesNotBlockRemainingReadyEvents() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 24, 9, 0);
        OutboxStatsVO stats = OutboxStatsVO.builder().pending(1L).dead(0L).build();
        when(expirationPolicy.now()).thenReturn(now);
        when(outboxMapper.findReadyIds(now, 2)).thenReturn(Arrays.asList(11L, 12L));
        doThrow(new IllegalStateException("claim failed")).when(processor).process(11L, now, 60);
        when(processor.process(12L, now, 60)).thenReturn(true);
        when(outboxService.stats()).thenReturn(stats);

        job.run();

        verify(processor).process(11L, now, 60);
        verify(processor).process(12L, now, 60);
        verify(metrics).recordOutboxResult("batch_item_failed");
        verify(metrics).updateBacklog(stats);
        assertThat(MDC.get("batchId")).isNull();
    }
}
