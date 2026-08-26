package com.localexplorer.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewVO implements Serializable {

    private Long id;
    private Long userId;
    private Long itemId;
    private Long orderId;
    private Integer rating;
    private String content;
    private String replyContent;
    private LocalDateTime replyTime;
    private LocalDateTime createTime;

    /** 冗余 */
    private String userName;
    private String userAvatar;
    private String itemName;
}
