package com.localexplorer.controller;

import com.localexplorer.cache.CacheInvalidationCoordinator;
import com.localexplorer.constant.StatusConstant;
import com.localexplorer.context.BaseContext;
import com.localexplorer.controller.user.ExploreOrderController;
import com.localexplorer.entity.ExploreItem;
import com.localexplorer.entity.ExploreOrder;
import com.localexplorer.handler.GlobalExceptionHandler;
import com.localexplorer.mapper.ExploreItemMapper;
import com.localexplorer.mapper.ExploreOrderMapper;
import com.localexplorer.mapper.ExplorePackageMapper;
import com.localexplorer.metrics.BookingMetrics;
import com.localexplorer.service.DashboardService;
import com.localexplorer.service.RuntimeSettingService;
import com.localexplorer.service.OrderEventOutboxService;
import com.localexplorer.service.OrderExpirationPolicy;
import com.localexplorer.service.impl.ExploreOrderServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BookingApiFlowTest {

    private MockMvc mockMvc;
    private final AtomicReference<ExploreOrder> storedOrder = new AtomicReference<>();

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
    private DashboardService dashboardService;
    @Mock
    private OrderEventOutboxService outboxService;

    @BeforeEach
    void setUp() {
        ExploreOrderServiceImpl orderService = new ExploreOrderServiceImpl();
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

        ExploreOrderController userController = new ExploreOrderController();
        ReflectionTestUtils.setField(userController, "orderService", orderService);
        com.localexplorer.controller.admin.ExploreOrderController adminController =
                new com.localexplorer.controller.admin.ExploreOrderController();
        ReflectionTestUtils.setField(adminController, "orderService", orderService);
        ReflectionTestUtils.setField(adminController, "dashboardService", dashboardService);

        mockMvc = MockMvcBuilders.standaloneSetup(userController, adminController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        when(runtimeSettingService.getShopStatus()).thenReturn(1);
        when(itemMapper.getById(1001L)).thenReturn(ExploreItem.builder()
                .id(1001L)
                .name("城市咖啡体验")
                .price(new BigDecimal("39.00"))
                .status(StatusConstant.ENABLE)
                .capacity(10)
                .booked(0)
                .build());
        when(itemMapper.reserveCapacity(1001L, 1)).thenReturn(1);
        when(orderMapper.getByUserIdAndRequestId(31L, "req-api-flow"))
                .thenAnswer(invocation -> storedOrder.get());
        when(orderMapper.getById(9001L)).thenAnswer(invocation -> storedOrder.get());
        when(orderMapper.updateStatusIfCurrent(anyLong(), anyInt(), anyInt(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    ExploreOrder current = storedOrder.get();
                    current.setStatus(invocation.getArgument(2));
                    return 1;
                });
        when(itemMapper.releaseCapacity(1001L, 1)).thenReturn(1);
        doAnswer(invocation -> {
            ExploreOrder order = invocation.getArgument(0);
            order.setId(9001L);
            storedOrder.set(order);
            return null;
        }).when(orderMapper).insert(any(ExploreOrder.class));
    }

    @AfterEach
    void tearDown() {
        BaseContext.removeCurrentId();
        storedOrder.set(null);
    }

    @Test
    void createRetryConfirmAndCancelRunThroughControllersAndRealService() throws Exception {
        BaseContext.setCurrentId(31L);
        String body = "{\"requestId\":\"req-api-flow\",\"orderType\":1,\"itemId\":1001,"
                + "\"peopleCount\":1,\"contactName\":\"张三\",\"contactPhone\":\"13800001111\","
                + "\"reserveTime\":\"2099-01-01T10:00:00\"}";

        mockMvc.perform(post("/user/explore-order").contentType("application/json").content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data").value(9001));
        mockMvc.perform(post("/user/explore-order").contentType("application/json").content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(9001));

        verify(itemMapper, times(1)).reserveCapacity(1001L, 1);

        mockMvc.perform(put("/admin/explore-order/status")
                        .param("id", "9001")
                        .param("status", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));

        mockMvc.perform(put("/user/explore-order/9001/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));

        verify(itemMapper).releaseCapacity(1001L, 1);
    }

    @Test
    void userCannotCancelAnotherUsersOrder() throws Exception {
        storedOrder.set(ExploreOrder.builder()
                .id(9001L)
                .userId(99L)
                .orderType(1)
                .itemId(1001L)
                .peopleCount(1)
                .status(0)
                .build());
        BaseContext.setCurrentId(31L);

        mockMvc.perform(put("/user/explore-order/9001/cancel"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40900))
                .andExpect(jsonPath("$.msg").value("无权操作该预约"));
    }
}
