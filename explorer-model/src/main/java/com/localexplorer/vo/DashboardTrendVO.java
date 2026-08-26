package com.localexplorer.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * Dashboard 趋势数据
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardTrendVO implements Serializable {

    /** 日期标签列表，如 ["04-25", "04-26", ...] */
    private List<String> dates;

    /** 每日预约数 */
    private List<Integer> orderCounts;

    /** 每日评价数 */
    private List<Integer> reviewCounts;

    /** 每日新增用户数 */
    private List<Integer> userCounts;

    /** 汇总 */
    private Long totalOrders;
    private Long totalReviews;
    private Long totalUsers;
    private Long pendingOrders;
    private BigDecimal confirmedRevenue;
    private Long completedOrders;
    private Long canceledOrders;
    private Integer completionRate;
}
