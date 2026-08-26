package com.localexplorer.service;

import com.localexplorer.mapper.ExploreOrderMapper;
import com.localexplorer.mapper.ReviewMapper;
import com.localexplorer.mapper.UserMapper;
import com.localexplorer.vo.DashboardTrendVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    private DashboardService dashboardService;

    @Mock
    private ExploreOrderMapper orderMapper;
    @Mock
    private ReviewMapper reviewMapper;
    @Mock
    private UserMapper userMapper;

    @BeforeEach
    void setUp() {
        dashboardService = new DashboardService();
        ReflectionTestUtils.setField(dashboardService, "orderMapper", orderMapper);
        ReflectionTestUtils.setField(dashboardService, "reviewMapper", reviewMapper);
        ReflectionTestUtils.setField(dashboardService, "userMapper", userMapper);
    }

    @Test
    void trendBuildsSevenDaySeriesAndTotalsFromBackendCounts() {
        LocalDate today = LocalDate.now();
        String firstDay = today.minusDays(6).toString();
        String yesterday = today.minusDays(1).toString();
        String todayText = today.toString();

        when(orderMapper.countByDate(6)).thenReturn(java.util.Arrays.asList(row(firstDay, 2), row(todayText, 3)));
        when(reviewMapper.countByDate(6)).thenReturn(Collections.singletonList(row(yesterday, 4)));
        when(userMapper.countByDate(6)).thenReturn(Collections.singletonList(row(todayText, 5)));
        when(orderMapper.countPending(0)).thenReturn(6L);
        when(orderMapper.sumConfirmedRevenueSince(1, 2, 6)).thenReturn(new BigDecimal("456.50"));
        when(orderMapper.countByStatusSince(2, 6)).thenReturn(3L);
        when(orderMapper.countByStatusSince(3, 6)).thenReturn(1L);

        DashboardTrendVO trend = dashboardService.trend();

        assertThat(trend.getDates()).hasSize(7);
        assertThat(trend.getDates().get(0)).isEqualTo(today.minusDays(6).format(DateTimeFormatter.ofPattern("MM-dd")));
        assertThat(trend.getDates().get(6)).isEqualTo(today.format(DateTimeFormatter.ofPattern("MM-dd")));
        assertThat(trend.getOrderCounts()).containsExactly(2, 0, 0, 0, 0, 0, 3);
        assertThat(trend.getReviewCounts()).containsExactly(0, 0, 0, 0, 0, 4, 0);
        assertThat(trend.getUserCounts()).containsExactly(0, 0, 0, 0, 0, 0, 5);
        assertThat(trend.getTotalOrders()).isEqualTo(5);
        assertThat(trend.getTotalReviews()).isEqualTo(4);
        assertThat(trend.getTotalUsers()).isEqualTo(5);
        assertThat(trend.getPendingOrders()).isEqualTo(6);
        assertThat(trend.getConfirmedRevenue()).isEqualByComparingTo("456.50");
        assertThat(trend.getCompletedOrders()).isEqualTo(3);
        assertThat(trend.getCanceledOrders()).isEqualTo(1);
        assertThat(trend.getCompletionRate()).isEqualTo(60);
    }

    private Map<String, Object> row(String date, Number count) {
        Map<String, Object> row = new HashMap<>();
        row.put("date", date);
        row.put("count", count);
        return row;
    }
}
