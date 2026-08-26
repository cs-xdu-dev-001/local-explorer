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
public class LoginGuard implements Serializable {
    private Long id;
    private String principalType;
    private String accountHash;
    private String ipHash;
    private String accountHint;
    private Integer failedCount;
    private LocalDateTime windowStartedAt;
    private LocalDateTime lockedUntil;
    private LocalDateTime lastFailedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
