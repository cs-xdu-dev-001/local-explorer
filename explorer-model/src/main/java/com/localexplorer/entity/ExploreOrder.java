package com.localexplorer.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 预约/订单
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExploreOrder implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    /** 预约用户ID */
    private Long userId;

    /** 预约编号 */
    private String orderNo;

    /** 1=特色项目 2=探店套餐 */
    private Integer orderType;

    /** 特色项目ID（order_type=1） */
    private Long itemId;

    /** 探店套餐ID（order_type=2） */
    private Long packageId;

    /** 项目/套餐名称（冗余） */
    private String itemName;

    /** 预约金额 */
    private BigDecimal amount;

    /** 预约人数 */
    private Integer peopleCount;

    /** 联系人 */
    private String contactName;

    /** 联系电话 */
    private String contactPhone;

    /** 预约时间 */
    private LocalDateTime reserveTime;

    /** 客户端幂等请求ID */
    private String requestId;

    /** 待确认状态的自动关闭时间 */
    private LocalDateTime expireAt;

    /** USER/ADMIN/TIMEOUT */
    private String cancelType;

    /** 取消或超时原因 */
    private String cancelReason;

    /** 备注 */
    private String remark;

    /** 0=待确认 1=已确认 2=已完成 3=已取消 4=超时取消 */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
