package com.localexplorer.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户评价
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Review implements Serializable {

    private static long serialVersionUID = 1L;

    private Long id;

    /** 评价用户ID */
    private Long userId;

    /** 评价的特色项目ID */
    private Long itemId;

    /** 关联预约ID */
    private Long orderId;

    /** 评分 1-5 */
    private Integer rating;

    /** 评价内容 */
    private String content;

    private String replyContent;

    private LocalDateTime replyTime;

    private LocalDateTime createTime;
}
