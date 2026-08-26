package com.localexplorer.service;

import com.localexplorer.domain.ExploreOrderStatus;
import com.localexplorer.mapper.ExploreOrderMapper;
import com.localexplorer.mapper.ReviewMapper;
import com.localexplorer.mapper.UserMapper;
import com.localexplorer.vo.DashboardTrendVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class DashboardService {

    private static final int TREND_DAYS = 7;
    private static final int LOOKBACK_DAYS = TREND_DAYS - 1;
    @Autowired
    private ExploreOrderMapper orderMapper;
    @Autowired
    private ReviewMapper reviewMapper;
    @Autowired
    private UserMapper userMapper;

    public DashboardTrendVO trend() {
        List<Map<String, Object>> orderCounts = orderMapper.countByDate(LOOKBACK_DAYS);
        List<Map<String, Object>> reviewCounts = reviewMapper.countByDate(LOOKBACK_DAYS);
        List<Map<String, Object>> userCounts = userMapper.countByDate(LOOKBACK_DAYS);

        DateTimeFormatter labelFormatter = DateTimeFormatter.ofPattern("MM-dd");
        List<String> dates = new ArrayList<>();
        List<Integer> orders = new ArrayList<>();
        List<Integer> reviews = new ArrayList<>();
        List<Integer> users = new ArrayList<>();

        for (int i = TREND_DAYS - 1; i >= 0; i--) {
            LocalDate date = LocalDate.now().minusDays(i);
            String fullDate = date.toString();
            dates.add(date.format(labelFormatter));
            orders.add(findCount(orderCounts, fullDate));
            reviews.add(findCount(reviewCounts, fullDate));
            users.add(findCount(userCounts, fullDate));
        }

        Long pendingOrders = orderMapper.countPending(ExploreOrderStatus.PENDING.getCode());
        BigDecimal confirmedRevenue = orderMapper.sumConfirmedRevenueSince(
                ExploreOrderStatus.CONFIRMED.getCode(),
                ExploreOrderStatus.COMPLETED.getCode(),
                LOOKBACK_DAYS);
        Long completedOrders = orderMapper.countByStatusSince(ExploreOrderStatus.COMPLETED.getCode(), LOOKBACK_DAYS);
        Long canceledOrders = orderMapper.countByStatusSince(ExploreOrderStatus.CANCELED.getCode(), LOOKBACK_DAYS);
        long totalOrders = sum(orders);
        long completed = completedOrders != null ? completedOrders : 0L;
        return DashboardTrendVO.builder()
                .dates(dates)
                .orderCounts(orders)
                .reviewCounts(reviews)
                .userCounts(users)
                .totalOrders(totalOrders)
                .totalReviews(sum(reviews))
                .totalUsers(sum(users))
                .pendingOrders(pendingOrders != null ? pendingOrders : 0L)
                .confirmedRevenue(confirmedRevenue != null ? confirmedRevenue : BigDecimal.ZERO)
                .completedOrders(completed)
                .canceledOrders(canceledOrders != null ? canceledOrders : 0L)
                .completionRate(totalOrders > 0 ? Math.round((float) completed * 100 / totalOrders) : 0)
                .build();
    }

    private int findCount(List<Map<String, Object>> data, String date) {
        for (Map<String, Object> row : data) {
            Object rowDate = row.get("date");
            if (rowDate != null && rowDate.toString().equals(date)) {
                Object count = row.get("count");
                return count != null ? ((Number) count).intValue() : 0;
            }
        }
        return 0;
    }

    private long sum(List<Integer> values) {
        return values.stream().mapToLong(Integer::longValue).sum();
    }
}
