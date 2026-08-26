package com.localexplorer.task;

import com.localexplorer.mapper.ExploreOrderMapper;
import com.localexplorer.metrics.OrderReliabilityMetrics;
import com.localexplorer.service.OrderExpirationPolicy;
import com.localexplorer.service.impl.ExpiredOrderProcessor;
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
class OrderExpirationJobTest {

    private OrderExpirationJob job;
    @Mock private ExploreOrderMapper orderMapper;
    @Mock private ExpiredOrderProcessor processor;
    @Mock private OrderExpirationPolicy expirationPolicy;
    @Mock private OrderReliabilityMetrics metrics;

    @BeforeEach
    void setUp() {
        job = new OrderExpirationJob();
        ReflectionTestUtils.setField(job, "orderMapper", orderMapper);
        ReflectionTestUtils.setField(job, "processor", processor);
        ReflectionTestUtils.setField(job, "expirationPolicy", expirationPolicy);
        ReflectionTestUtils.setField(job, "metrics", metrics);
        ReflectionTestUtils.setField(job, "batchSize", 3);
    }

    @Test
    void oneFailedOrderDoesNotBlockRemainingExpiredOrders() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 24, 9, 0);
        when(expirationPolicy.now()).thenReturn(now);
        when(orderMapper.findExpiredIds(0, now, 3)).thenReturn(Arrays.asList(1L, 2L, 3L));
        when(processor.expire(1L, now)).thenReturn(true);
        doThrow(new IllegalStateException("database unavailable")).when(processor).expire(2L, now);
        when(processor.expire(3L, now)).thenReturn(false);

        job.run();

        verify(processor).expire(1L, now);
        verify(processor).expire(2L, now);
        verify(processor).expire(3L, now);
        verify(metrics).recordExpirationResult("expired");
        verify(metrics).recordExpirationResult("failed");
        verify(metrics).recordExpirationResult("cas_conflict");
        assertThat(MDC.get("batchId")).isNull();
    }
}
