package com.localexplorer.service.impl;

import com.localexplorer.domain.OrderEventType;
import com.localexplorer.entity.OrderEventOutbox;
import com.localexplorer.entity.UserNotification;
import com.localexplorer.exception.BaseException;
import com.localexplorer.mapper.OrderEventOutboxMapper;
import com.localexplorer.mapper.UserNotificationMapper;
import com.localexplorer.service.OutboxRetryPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxEventTransactionServiceTest {

    private static final String LOCK_TOKEN = "lease-token-1";

    private OutboxEventTransactionService service;
    @Mock private OrderEventOutboxMapper outboxMapper;
    @Mock private UserNotificationMapper notificationMapper;

    @BeforeEach
    void setUp() {
        service = new OutboxEventTransactionService();
        ReflectionTestUtils.setField(service, "outboxMapper", outboxMapper);
        ReflectionTestUtils.setField(service, "notificationMapper", notificationMapper);
        ReflectionTestUtils.setField(service, "retryPolicy",
                new OutboxRetryPolicy(3, Duration.ofSeconds(10)));
    }

    @Test
    void deliveryCreatesIdempotentNotificationAndMarksEventProcessed() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 24, 8, 0);
        OrderEventOutbox event = event(1, OrderEventType.CONFIRMED);
        when(outboxMapper.getById(1L)).thenReturn(event);
        when(outboxMapper.markProcessed(1L, LOCK_TOKEN, now)).thenReturn(1);

        service.deliver(1L, LOCK_TOKEN, now);

        ArgumentCaptor<UserNotification> captor = ArgumentCaptor.forClass(UserNotification.class);
        verify(notificationMapper).insertIgnore(captor.capture());
        assertThat(captor.getValue().getEventId()).isEqualTo("evt-1");
        assertThat(captor.getValue().getTitle()).contains("确认");
        verify(outboxMapper).markProcessed(1L, LOCK_TOKEN, now);
    }

    @Test
    void failureSchedulesRetryBeforeMaxAttempts() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 24, 8, 0);
        OrderEventOutbox event = event(2, OrderEventType.EXPIRED);
        event.setRetryCount(1);
        when(outboxMapper.getById(2L)).thenReturn(event);
        when(outboxMapper.markRetry(eq(2L), eq(LOCK_TOKEN), eq(2),
                eq(now.plusSeconds(20)), eq("token=***"), eq(now))).thenReturn(1);

        service.recordFailure(2L, LOCK_TOKEN, new IllegalStateException("token=secret"), now);

        verify(outboxMapper).markRetry(eq(2L), eq(LOCK_TOKEN), eq(2), eq(now.plusSeconds(20)),
                eq("token=***"), eq(now));
        verify(outboxMapper, never()).markDead(any(), any(), any(Integer.class), any(), any());
    }

    @Test
    void failureAtMaxAttemptsMovesEventToDead() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 24, 8, 0);
        OrderEventOutbox event = event(3, OrderEventType.EXPIRED);
        event.setRetryCount(2);
        when(outboxMapper.getById(3L)).thenReturn(event);
        when(outboxMapper.markDead(3L, LOCK_TOKEN, 3, "failed", now)).thenReturn(1);

        service.recordFailure(3L, LOCK_TOKEN, new IllegalStateException("failed"), now);

        verify(outboxMapper).markDead(3L, LOCK_TOKEN, 3, "failed", now);
        verify(outboxMapper, never()).markRetry(any(), any(), any(Integer.class), any(), any(), any());
    }

    @Test
    void staleLeaseCannotRecordFailureAfterAnotherWorkerReclaimsEvent() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 24, 8, 0);
        OrderEventOutbox event = event(4, OrderEventType.EXPIRED);
        when(outboxMapper.getById(4L)).thenReturn(event);
        when(outboxMapper.markRetry(eq(4L), eq("stale-token"), eq(1),
                eq(now.plusSeconds(10)), any(), eq(now))).thenReturn(0);

        assertThatThrownBy(() -> service.recordFailure(
                4L, "stale-token", new IllegalStateException("late failure"), now))
                .isInstanceOf(BaseException.class)
                .hasMessageContaining("租约");
    }

    private OrderEventOutbox event(long id, String type) {
        return OrderEventOutbox.builder()
                .id(id)
                .eventId("evt-" + id)
                .eventType(type)
                .aggregateId(31L)
                .userId(7L)
                .payload("{\"orderNo\":\"ORD31\",\"itemName\":\"城市咖啡体验\"}")
                .retryCount(0)
                .build();
    }
}
