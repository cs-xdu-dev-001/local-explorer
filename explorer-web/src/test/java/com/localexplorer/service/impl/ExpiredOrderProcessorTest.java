package com.localexplorer.service.impl;

import com.localexplorer.cache.CacheInvalidation;
import com.localexplorer.cache.CacheInvalidationCoordinator;
import com.localexplorer.domain.ExploreOrderStatus;
import com.localexplorer.domain.OrderCancelType;
import com.localexplorer.domain.OrderEventType;
import com.localexplorer.entity.ExploreOrder;
import com.localexplorer.exception.BaseException;
import com.localexplorer.mapper.ExploreItemMapper;
import com.localexplorer.mapper.ExploreOrderMapper;
import com.localexplorer.mapper.ExplorePackageMapper;
import com.localexplorer.service.OrderEventOutboxService;
import com.localexplorer.service.OrderExpirationPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
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
class ExpiredOrderProcessorTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 24, 8, 0);

    private ExpiredOrderProcessor processor;
    @Mock private ExploreOrderMapper orderMapper;
    @Mock private ExploreItemMapper itemMapper;
    @Mock private ExplorePackageMapper packageMapper;
    @Mock private OrderEventOutboxService outboxService;
    @Mock private CacheInvalidationCoordinator cacheInvalidationCoordinator;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-24T00:00:00Z"), ZoneId.of("Asia/Shanghai"));
        processor = new ExpiredOrderProcessor();
        ReflectionTestUtils.setField(processor, "orderMapper", orderMapper);
        ReflectionTestUtils.setField(processor, "itemMapper", itemMapper);
        ReflectionTestUtils.setField(processor, "packageMapper", packageMapper);
        ReflectionTestUtils.setField(processor, "outboxService", outboxService);
        ReflectionTestUtils.setField(processor, "cacheInvalidationCoordinator", cacheInvalidationCoordinator);
        ReflectionTestUtils.setField(processor, "expirationPolicy",
                new OrderExpirationPolicy(clock, Duration.ofMinutes(30)));
    }

    @Test
    void expiresPendingOrderReleasesCapacityAndAppendsEventOnce() {
        ExploreOrder order = pendingItemOrder();
        when(orderMapper.getById(30L)).thenReturn(order);
        when(orderMapper.expireIfDue(eq(30L), eq(0), eq(4),
                eq(OrderCancelType.TIMEOUT), any(), eq(NOW))).thenReturn(1);
        when(itemMapper.releaseCapacity(1001L, 2)).thenReturn(1);
        assertThat(processor.expire(30L, NOW)).isTrue();

        verify(itemMapper).releaseCapacity(1001L, 2);
        verify(outboxService).append(order, OrderEventType.EXPIRED, NOW);
        verify(cacheInvalidationCoordinator).invalidate(any(CacheInvalidation.class));
    }

    @Test
    void concurrentCasLossDoesNotReleaseCapacityOrAppendEvent() {
        ExploreOrder order = pendingItemOrder();
        when(orderMapper.getById(30L)).thenReturn(order);
        when(orderMapper.expireIfDue(eq(30L), eq(0), eq(4),
                eq(OrderCancelType.TIMEOUT), any(), eq(NOW))).thenReturn(0);

        assertThat(processor.expire(30L, NOW)).isFalse();

        verify(itemMapper, never()).releaseCapacity(any(), any());
        verify(outboxService, never()).append(any(), any(), any());
        verify(cacheInvalidationCoordinator, never()).invalidate(any());
    }

    @Test
    void capacityReleaseFailureAbortsEventCreation() {
        ExploreOrder order = pendingItemOrder();
        when(orderMapper.getById(30L)).thenReturn(order);
        when(orderMapper.expireIfDue(eq(30L), eq(0), eq(4),
                eq(OrderCancelType.TIMEOUT), any(), eq(NOW))).thenReturn(1);
        when(itemMapper.releaseCapacity(1001L, 2)).thenReturn(0);

        assertThatThrownBy(() -> processor.expire(30L, NOW))
                .isInstanceOf(BaseException.class)
                .hasMessageContaining("释放");
        verify(outboxService, never()).append(any(), any(), any());
    }

    private ExploreOrder pendingItemOrder() {
        return ExploreOrder.builder()
                .id(30L)
                .userId(7L)
                .orderNo("ORD30")
                .orderType(1)
                .itemId(1001L)
                .itemName("城市咖啡体验")
                .peopleCount(2)
                .status(ExploreOrderStatus.PENDING.getCode())
                .expireAt(NOW)
                .build();
    }
}
