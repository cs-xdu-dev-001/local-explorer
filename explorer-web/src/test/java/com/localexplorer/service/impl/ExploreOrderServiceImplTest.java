package com.localexplorer.service.impl;

import com.localexplorer.cache.CacheInvalidationCoordinator;
import com.localexplorer.constant.StatusConstant;
import com.localexplorer.dto.ExploreOrderDTO;
import com.localexplorer.entity.ExploreItem;
import com.localexplorer.entity.ExploreOrder;
import com.localexplorer.entity.ExplorePackage;
import com.localexplorer.exception.BaseException;
import com.localexplorer.mapper.ExploreItemMapper;
import com.localexplorer.mapper.ExploreOrderMapper;
import com.localexplorer.mapper.ExplorePackageMapper;
import com.localexplorer.metrics.BookingMetrics;
import com.localexplorer.service.RuntimeSettingService;
import com.localexplorer.service.OrderEventOutboxService;
import com.localexplorer.service.OrderExpirationPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.dao.DuplicateKeyException;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.Clock;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExploreOrderServiceImplTest {

    private ExploreOrderServiceImpl orderService;

    @Mock
    private ExploreOrderMapper orderMapper;
    @Mock
    private ExploreItemMapper itemMapper;
    @Mock
    private ExplorePackageMapper packageMapper;
    @Mock
    private RuntimeSettingService runtimeSettingService;
    @Mock
    private BookingMetrics bookingMetrics;
    @Mock
    private OrderEventOutboxService outboxService;

    @BeforeEach
    void setUp() {
        orderService = new ExploreOrderServiceImpl();
        ReflectionTestUtils.setField(orderService, "orderMapper", orderMapper);
        ReflectionTestUtils.setField(orderService, "itemMapper", itemMapper);
        ReflectionTestUtils.setField(orderService, "packageMapper", packageMapper);
        ReflectionTestUtils.setField(orderService, "runtimeSettingService", runtimeSettingService);
        ReflectionTestUtils.setField(orderService, "bookingMetrics", bookingMetrics);
        ReflectionTestUtils.setField(orderService, "outboxService", outboxService);
        ReflectionTestUtils.setField(orderService, "cacheInvalidationCoordinator",
                org.mockito.Mockito.mock(CacheInvalidationCoordinator.class));
        ReflectionTestUtils.setField(orderService, "expirationPolicy",
                new OrderExpirationPolicy(Clock.systemDefaultZone(), Duration.ofMinutes(30)));
    }

    @Test
    void getByIdRejectsMissingOrder() {
        assertThatThrownBy(() -> orderService.getById(404L))
                .isInstanceOf(BaseException.class)
                .hasMessage("预约不存在");
    }

    @Test
    void createItemOrderDoesNotUseReadModifyWriteForCapacity() {
        givenShopOpen();
        ExploreItem item = ExploreItem.builder()
                .id(1001L)
                .name("城市咖啡体验")
                .price(new BigDecimal("39.00"))
                .status(StatusConstant.ENABLE)
                .capacity(10)
                .booked(4)
                .build();
        when(itemMapper.getById(1001L)).thenReturn(item);
        when(itemMapper.reserveCapacity(1001L, 2)).thenReturn(1);

        ExploreOrderDTO dto = new ExploreOrderDTO();
        dto.setOrderType(1);
        dto.setItemId(1001L);
        dto.setItemName("前端传来的名称");
        dto.setAmount(new BigDecimal("0.01"));
        dto.setPeopleCount(2);
        dto.setContactName("张三");
        dto.setContactPhone("13800001111");
        dto.setReserveTime(LocalDateTime.now().plusDays(1));

        orderService.create(dto, 7L);

        ArgumentCaptor<com.localexplorer.entity.ExploreOrder> orderCaptor =
                ArgumentCaptor.forClass(com.localexplorer.entity.ExploreOrder.class);
        verify(orderMapper).insert(orderCaptor.capture());
        assertThat(orderCaptor.getValue().getItemName()).isEqualTo("城市咖啡体验");
        assertThat(orderCaptor.getValue().getAmount()).isEqualByComparingTo("39.00");

        verify(itemMapper, times(1)).getById(1001L);
        verify(itemMapper).reserveCapacity(1001L, 2);
        verify(itemMapper, never()).update(any());
        verify(bookingMetrics).recordCreated("item");
    }

    @Test
    void createItemOrderRejectsDisabledItem() {
        givenShopOpen();
        ExploreItem item = ExploreItem.builder()
                .id(1001L)
                .name("城市咖啡体验")
                .price(new BigDecimal("39.00"))
                .status(StatusConstant.DISABLE)
                .capacity(10)
                .booked(4)
                .build();
        when(itemMapper.getById(1001L)).thenReturn(item);

        ExploreOrderDTO dto = new ExploreOrderDTO();
        dto.setOrderType(1);
        dto.setItemId(1001L);
        dto.setPeopleCount(1);

        assertThatThrownBy(() -> orderService.create(dto, 7L))
                .isInstanceOf(BaseException.class)
                .hasMessageContaining("已停用");

        verify(orderMapper, never()).insert(any());
    }

    @Test
    void createItemOrderRejectsInsufficientCapacity() {
        givenShopOpen();
        ExploreItem item = ExploreItem.builder()
                .id(1001L)
                .name("城市咖啡体验")
                .price(new BigDecimal("39.00"))
                .status(StatusConstant.ENABLE)
                .capacity(5)
                .booked(4)
                .build();
        when(itemMapper.getById(1001L)).thenReturn(item);

        ExploreOrderDTO dto = new ExploreOrderDTO();
        dto.setOrderType(1);
        dto.setItemId(1001L);
        dto.setPeopleCount(2);

        assertThatThrownBy(() -> orderService.create(dto, 7L))
                .isInstanceOf(BaseException.class)
                .hasMessageContaining("名额不足");

        verify(orderMapper, never()).insert(any());
        verify(bookingMetrics).recordCapacityExhausted("item");
        verify(bookingMetrics).recordFailure("capacity");
    }

    @Test
    void createItemOrderRejectsWhenAtomicReservationLosesRace() {
        givenShopOpen();
        ExploreItem item = ExploreItem.builder()
                .id(1001L)
                .name("城市咖啡体验")
                .price(new BigDecimal("39.00"))
                .status(StatusConstant.ENABLE)
                .capacity(5)
                .booked(4)
                .build();
        when(itemMapper.getById(1001L)).thenReturn(item);
        when(itemMapper.reserveCapacity(1001L, 1)).thenReturn(0);

        ExploreOrderDTO dto = new ExploreOrderDTO();
        dto.setOrderType(1);
        dto.setItemId(1001L);
        dto.setPeopleCount(1);

        assertThatThrownBy(() -> orderService.create(dto, 7L))
                .isInstanceOf(BaseException.class)
                .hasMessageContaining("刷新后重试");

        verify(orderMapper, never()).insert(any());
        verify(itemMapper, never()).update(any());
    }

    @Test
    void createOrderWithSameRequestIdReturnsExistingOrderWithoutReservingAgain() {
        ExploreOrder existing = ExploreOrder.builder()
                .id(9001L)
                .userId(7L)
                .requestId("req-001")
                .build();
        when(orderMapper.getByUserIdAndRequestId(7L, "req-001")).thenReturn(existing);

        ExploreOrderDTO dto = new ExploreOrderDTO();
        dto.setOrderType(1);
        dto.setItemId(1001L);
        dto.setPeopleCount(1);
        dto.setRequestId("req-001");

        Long result = orderService.create(dto, 7L);

        assertThat(result).isEqualTo(9001L);
        verify(runtimeSettingService, never()).getShopStatus();
        verify(itemMapper, never()).reserveCapacity(any(), any());
        verify(orderMapper, never()).insert(any());
        verify(bookingMetrics).recordIdempotentHit();
    }

    @Test
    void createOrderReleasesReservedCapacityWhenRequestIdHitsUniqueRace() {
        givenShopOpen();
        ExploreItem item = ExploreItem.builder()
                .id(1001L)
                .name("城市咖啡体验")
                .price(new BigDecimal("39.00"))
                .status(StatusConstant.ENABLE)
                .capacity(10)
                .booked(4)
                .build();
        ExploreOrder existing = ExploreOrder.builder()
                .id(9002L)
                .userId(7L)
                .requestId("req-race")
                .build();
        when(orderMapper.getByUserIdAndRequestId(7L, "req-race")).thenReturn(null, existing);
        when(itemMapper.getById(1001L)).thenReturn(item);
        when(itemMapper.reserveCapacity(1001L, 2)).thenReturn(1);
        when(itemMapper.releaseCapacity(1001L, 2)).thenReturn(1);
        org.mockito.Mockito.doThrow(new DuplicateKeyException("duplicate request"))
                .when(orderMapper).insert(any());

        ExploreOrderDTO dto = new ExploreOrderDTO();
        dto.setOrderType(1);
        dto.setItemId(1001L);
        dto.setPeopleCount(2);
        dto.setContactName("张三");
        dto.setContactPhone("13800001111");
        dto.setReserveTime(LocalDateTime.now().plusDays(1));
        dto.setRequestId("req-race");

        Long result = orderService.create(dto, 7L);

        assertThat(result).isEqualTo(9002L);
        verify(itemMapper).releaseCapacity(1001L, 2);
        verify(bookingMetrics).recordIdempotentHit();
    }

    @Test
    void createOrderRejectsWhenShopIsClosed() {
        when(runtimeSettingService.getShopStatus()).thenReturn(0);

        ExploreOrderDTO dto = new ExploreOrderDTO();
        dto.setOrderType(1);
        dto.setItemId(1001L);
        dto.setPeopleCount(1);

        assertThatThrownBy(() -> orderService.create(dto, 7L))
                .isInstanceOf(BaseException.class)
                .hasMessageContaining("门店休息中");

        verify(orderMapper, never()).insert(any());
        verify(itemMapper, never()).update(any());
        verify(packageMapper, never()).update(any());
        verify(bookingMetrics).recordFailure("shop_closed");
    }

    @Test
    void createPackageOrderUsesDatabasePriceAndIncrementsBooked() {
        givenShopOpen();
        ExplorePackage packageEntity = ExplorePackage.builder()
                .id(2001L)
                .name("Weekend Explorer")
                .price(new BigDecimal("168.00"))
                .status(StatusConstant.ENABLE)
                .capacity(8)
                .booked(3)
                .build();
        when(packageMapper.getById(2001L)).thenReturn(packageEntity);
        when(packageMapper.reserveCapacity(2001L, 2)).thenReturn(1);

        ExploreOrderDTO dto = new ExploreOrderDTO();
        dto.setOrderType(2);
        dto.setPackageId(2001L);
        dto.setItemName("tampered package");
        dto.setAmount(new BigDecimal("0.01"));
        dto.setPeopleCount(2);
        dto.setContactName("Li Si");
        dto.setContactPhone("13900001111");
        dto.setReserveTime(LocalDateTime.now().plusDays(2));

        orderService.create(dto, 8L);

        ArgumentCaptor<com.localexplorer.entity.ExploreOrder> orderCaptor =
                ArgumentCaptor.forClass(com.localexplorer.entity.ExploreOrder.class);
        verify(orderMapper).insert(orderCaptor.capture());
        assertThat(orderCaptor.getValue().getItemName()).isEqualTo("Weekend Explorer");
        assertThat(orderCaptor.getValue().getAmount()).isEqualByComparingTo("168.00");

        verify(packageMapper, times(1)).getById(2001L);
        verify(packageMapper).reserveCapacity(2001L, 2);
        verify(packageMapper, never()).update(any());
    }

    @Test
    void createPackageOrderRejectsDisabledPackage() {
        givenShopOpen();
        ExplorePackage packageEntity = ExplorePackage.builder()
                .id(2001L)
                .name("Weekend Explorer")
                .price(new BigDecimal("168.00"))
                .status(StatusConstant.DISABLE)
                .capacity(8)
                .booked(3)
                .build();
        when(packageMapper.getById(2001L)).thenReturn(packageEntity);

        ExploreOrderDTO dto = new ExploreOrderDTO();
        dto.setOrderType(2);
        dto.setPackageId(2001L);
        dto.setPeopleCount(1);

        assertThatThrownBy(() -> orderService.create(dto, 8L))
                .isInstanceOf(BaseException.class)
                .hasMessageContaining("\u5df2\u505c\u7528");

        verify(orderMapper, never()).insert(any());
    }

    @Test
    void createPackageOrderRejectsInsufficientCapacity() {
        givenShopOpen();
        ExplorePackage packageEntity = ExplorePackage.builder()
                .id(2001L)
                .name("Weekend Explorer")
                .price(new BigDecimal("168.00"))
                .status(StatusConstant.ENABLE)
                .capacity(4)
                .booked(3)
                .build();
        when(packageMapper.getById(2001L)).thenReturn(packageEntity);

        ExploreOrderDTO dto = new ExploreOrderDTO();
        dto.setOrderType(2);
        dto.setPackageId(2001L);
        dto.setPeopleCount(2);

        assertThatThrownBy(() -> orderService.create(dto, 8L))
                .isInstanceOf(BaseException.class)
                .hasMessageContaining("\u540d\u989d\u4e0d\u8db3");

        verify(orderMapper, never()).insert(any());
    }

    @Test
    void cancelItemOrderReleasesBookedCount() {
        ExploreOrder order = ExploreOrder.builder()
                .id(3001L)
                .orderNo("ORD3001")
                .orderType(1)
                .itemId(1001L)
                .peopleCount(2)
                .status(1)
                .build();
        ExploreItem item = ExploreItem.builder()
                .id(1001L)
                .booked(6)
                .build();
        when(orderMapper.getById(3001L)).thenReturn(order);
        when(orderMapper.updateStatusIfCurrent(any(), any(), any(), any(), any(), any())).thenReturn(1);
        when(itemMapper.releaseCapacity(1001L, 2)).thenReturn(1);

        orderService.updateStatus(3001L, 3);

        verify(itemMapper).releaseCapacity(1001L, 2);
        verify(itemMapper, never()).update(any());
    }

    @Test
    void cancelPackageOrderReleasesBookedCount() {
        ExploreOrder order = ExploreOrder.builder()
                .id(3002L)
                .orderNo("ORD3002")
                .orderType(2)
                .packageId(2001L)
                .peopleCount(3)
                .status(1)
                .build();
        ExplorePackage packageEntity = ExplorePackage.builder()
                .id(2001L)
                .booked(7)
                .build();
        when(orderMapper.getById(3002L)).thenReturn(order);
        when(orderMapper.updateStatusIfCurrent(any(), any(), any(), any(), any(), any())).thenReturn(1);
        when(packageMapper.releaseCapacity(2001L, 3)).thenReturn(1);

        orderService.updateStatus(3002L, 3);

        verify(packageMapper).releaseCapacity(2001L, 3);
        verify(packageMapper, never()).update(any());
    }

    @Test
    void cancelAlreadyCanceledOrderDoesNotReleaseBookedAgain() {
        ExploreOrder order = ExploreOrder.builder()
                .id(3003L)
                .orderNo("ORD3003")
                .orderType(1)
                .itemId(1001L)
                .peopleCount(2)
                .status(3)
                .build();
        when(orderMapper.getById(3003L)).thenReturn(order);

        orderService.updateStatus(3003L, 3);

        verify(itemMapper, never()).update(any());
        verify(packageMapper, never()).update(any());
    }

    @Test
    void statusUpdateRejectsConcurrentTransitionWithoutReleasingCapacity() {
        ExploreOrder initial = ExploreOrder.builder()
                .id(3011L)
                .orderNo("ORD3011")
                .orderType(1)
                .itemId(1001L)
                .peopleCount(2)
                .status(1)
                .build();
        ExploreOrder latest = ExploreOrder.builder()
                .id(3011L)
                .orderNo("ORD3011")
                .status(2)
                .build();
        when(orderMapper.getById(3011L)).thenReturn(initial, latest);
        when(orderMapper.updateStatusIfCurrent(
                org.mockito.ArgumentMatchers.eq(3011L),
                org.mockito.ArgumentMatchers.eq(1),
                org.mockito.ArgumentMatchers.eq(3),
                any(),
                any(),
                any(LocalDateTime.class))).thenReturn(0);

        assertThatThrownBy(() -> orderService.updateStatus(3011L, 3))
                .isInstanceOf(BaseException.class)
                .hasMessageContaining("发生变化");

        verify(itemMapper, never()).releaseCapacity(any(), any());
        verify(packageMapper, never()).releaseCapacity(any(), any());
    }

    @Test
    void duplicateConcurrentCancellationDoesNotReleaseCapacityTwice() {
        ExploreOrder initial = ExploreOrder.builder()
                .id(3012L)
                .orderNo("ORD3012")
                .orderType(1)
                .itemId(1001L)
                .peopleCount(2)
                .status(1)
                .build();
        ExploreOrder latest = ExploreOrder.builder()
                .id(3012L)
                .orderNo("ORD3012")
                .status(3)
                .build();
        when(orderMapper.getById(3012L)).thenReturn(initial, latest);
        when(orderMapper.updateStatusIfCurrent(
                org.mockito.ArgumentMatchers.eq(3012L),
                org.mockito.ArgumentMatchers.eq(1),
                org.mockito.ArgumentMatchers.eq(3),
                any(),
                any(),
                any(LocalDateTime.class))).thenReturn(0);

        orderService.updateStatus(3012L, 3);

        verify(itemMapper, never()).releaseCapacity(any(), any());
        verify(packageMapper, never()).releaseCapacity(any(), any());
    }

    @Test
    void updateStatusRejectsInvalidTargetStatus() {
        ExploreOrder order = ExploreOrder.builder()
                .id(3004L)
                .orderNo("ORD3004")
                .status(0)
                .build();
        when(orderMapper.getById(3004L)).thenReturn(order);

        assertThatThrownBy(() -> orderService.updateStatus(3004L, 9))
                .isInstanceOf(BaseException.class)
                .hasMessageContaining("状态");

        verify(orderMapper, never()).update(any());
    }

    @Test
    void pendingOrderCannotBeCompletedDirectly() {
        ExploreOrder order = ExploreOrder.builder()
                .id(3005L)
                .orderNo("ORD3005")
                .status(0)
                .build();
        when(orderMapper.getById(3005L)).thenReturn(order);

        assertThatThrownBy(() -> orderService.updateStatus(3005L, 2))
                .isInstanceOf(BaseException.class)
                .hasMessageContaining("状态");

        verify(orderMapper, never()).update(any());
    }

    @Test
    void administratorCannotMarkOrderAsSystemExpired() {
        ExploreOrder order = ExploreOrder.builder()
                .id(3013L)
                .orderNo("ORD3013")
                .status(0)
                .build();
        when(orderMapper.getById(3013L)).thenReturn(order);

        assertThatThrownBy(() -> orderService.updateStatus(3013L, 4))
                .isInstanceOf(BaseException.class)
                .hasMessageContaining("系统超时");

        verify(orderMapper, never()).updateStatusIfCurrent(any(), any(), any(), any(), any(), any());
    }

    @Test
    void completedOrderCannotBeCanceledAndReleaseBooked() {
        ExploreOrder order = ExploreOrder.builder()
                .id(3006L)
                .orderNo("ORD3006")
                .orderType(1)
                .itemId(1001L)
                .peopleCount(2)
                .status(2)
                .build();
        when(orderMapper.getById(3006L)).thenReturn(order);

        assertThatThrownBy(() -> orderService.updateStatus(3006L, 3))
                .isInstanceOf(BaseException.class)
                .hasMessageContaining("状态");

        verify(orderMapper, never()).update(any());
        verify(itemMapper, never()).update(any());
        verify(packageMapper, never()).update(any());
    }

    @Test
    void userCancelRejectsOrderOwnedByOtherUser() {
        ExploreOrder order = ExploreOrder.builder()
                .id(3007L)
                .orderNo("ORD3007")
                .userId(99L)
                .status(0)
                .build();
        when(orderMapper.getById(3007L)).thenReturn(order);

        assertThatThrownBy(() -> orderService.cancelByUser(3007L, 7L))
                .isInstanceOf(BaseException.class)
                .hasMessageContaining("无权");

        verify(orderMapper, never()).update(any());
        verify(itemMapper, never()).update(any());
        verify(packageMapper, never()).update(any());
    }

    @Test
    void userGetByIdRejectsOrderOwnedByOtherUser() {
        ExploreOrder order = ExploreOrder.builder()
                .id(3009L)
                .orderNo("ORD3009")
                .userId(99L)
                .status(0)
                .build();
        when(orderMapper.getById(3009L)).thenReturn(order);

        assertThatThrownBy(() -> orderService.getByIdForUser(3009L, 7L))
                .isInstanceOf(BaseException.class)
                .hasMessageContaining("无权");
    }

    @Test
    void userGetByIdReturnsOwnOrder() {
        ExploreOrder order = ExploreOrder.builder()
                .id(3010L)
                .orderNo("ORD3010")
                .userId(7L)
                .status(0)
                .build();
        when(orderMapper.getById(3010L)).thenReturn(order);

        ExploreOrder result = orderService.getByIdForUser(3010L, 7L);

        assertThat(result).isSameAs(order);
    }

    @Test
    void userCancelOwnPendingOrderReleasesBookedCount() {
        ExploreOrder order = ExploreOrder.builder()
                .id(3008L)
                .orderNo("ORD3008")
                .userId(7L)
                .orderType(1)
                .itemId(1001L)
                .peopleCount(2)
                .status(0)
                .build();
        ExploreItem item = ExploreItem.builder()
                .id(1001L)
                .booked(5)
                .build();
        when(orderMapper.getById(3008L)).thenReturn(order);
        when(orderMapper.updateStatusIfCurrent(any(), any(), any(), any(), any(), any())).thenReturn(1);
        when(itemMapper.releaseCapacity(1001L, 2)).thenReturn(1);

        orderService.cancelByUser(3008L, 7L);

        verify(orderMapper).updateStatusIfCurrent(any(), any(), any(), any(), any(), any());
        verify(itemMapper).releaseCapacity(1001L, 2);
        verify(itemMapper, never()).update(any());
    }

    private void givenShopOpen() {
        when(runtimeSettingService.getShopStatus()).thenReturn(1);
    }
}
