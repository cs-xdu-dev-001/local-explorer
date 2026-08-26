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
public class OrderEventOutbox implements Serializable {

    private Long id;
    private String eventId;
    private String eventType;
    private Long aggregateId;
    private Long userId;
    private String payload;
    private String status;
    private Integer retryCount;
    private LocalDateTime nextRetryAt;
    private LocalDateTime lockedUntil;
    private String lockToken;
    private String lastError;
    private LocalDateTime processedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
