package com.localexplorer.entity;

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
public class UserNotification implements Serializable {

    private Long id;
    private String eventId;
    private Long userId;
    private Long orderId;
    private String notificationType;
    private String title;
    private String content;
    private Integer readStatus;
    private LocalDateTime readTime;
    private LocalDateTime createTime;
}
