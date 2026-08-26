package com.localexplorer.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExploreOrderVO implements Serializable {

    private Long id;
    private Long userId;
    private String orderNo;
    private Integer orderType;
    private Long itemId;
    private Long packageId;
    private String itemName;
    private BigDecimal amount;
    private Integer peopleCount;
    private String contactName;
    private String contactPhone;
    private LocalDateTime reserveTime;
    private LocalDateTime expireAt;
    private String cancelType;
    private String cancelReason;
    private String remark;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /** 冗余：用户名 */
    private String userName;

    private Boolean hasReview;
}
