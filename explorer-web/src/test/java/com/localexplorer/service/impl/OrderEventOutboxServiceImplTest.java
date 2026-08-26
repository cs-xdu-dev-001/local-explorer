package com.localexplorer.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.localexplorer.domain.OrderEventType;
import com.localexplorer.domain.OutboxStatus;
import com.localexplorer.entity.ExploreOrder;
import com.localexplorer.entity.OrderEventOutbox;
import com.localexplorer.mapper.OrderEventOutboxMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderEventOutboxServiceImplTest {

    private OrderEventOutboxServiceImpl service;
    @Mock private OrderEventOutboxMapper outboxMapper;
    @Mock private com.localexplorer.service.OrderExpirationPolicy expirationPolicy;

    @BeforeEach
    void setUp() {
        service = new OrderEventOutboxServiceImpl();
        ReflectionTestUtils.setField(service, "outboxMapper", outboxMapper);
        ReflectionTestUtils.setField(service, "objectMapper", new ObjectMapper());
        ReflectionTestUtils.setField(service, "expirationPolicy", expirationPolicy);
    }

    @Test
    void appendStoresSanitizedOrderSnapshotAsPendingEvent() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 24, 8, 0);
        ExploreOrder order = ExploreOrder.builder()
                .id(31L)
                .userId(7L)
                .orderNo("ORD31")
                .itemName("城市咖啡体验")
                .contactPhone("13800001111")
                .status(1)
                .build();

        service.append(order, OrderEventType.CONFIRMED, now);

        ArgumentCaptor<OrderEventOutbox> captor = ArgumentCaptor.forClass(OrderEventOutbox.class);
        verify(outboxMapper).insert(captor.capture());
        OrderEventOutbox event = captor.getValue();
        assertThat(event.getEventId()).isNotBlank();
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(event.getAggregateId()).isEqualTo(31L);
        assertThat(event.getNextRetryAt()).isEqualTo(now);
        assertThat(event.getPayload())
                .contains("ORD31", "城市咖啡体验")
                .doesNotContain("13800001111", "contactPhone", "password", "token");
    }

    @Test
    void retryDeadUsesInjectedClock() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 24, 9, 30);
        when(expirationPolicy.now()).thenReturn(now);
        when(outboxMapper.getById(41L)).thenReturn(OrderEventOutbox.builder()
                .id(41L)
                .status(OutboxStatus.DEAD)
                .build());
        when(outboxMapper.resetDead(41L, now)).thenReturn(1);

        service.retryDead(41L);

        verify(outboxMapper).resetDead(41L, now);
    }
}
