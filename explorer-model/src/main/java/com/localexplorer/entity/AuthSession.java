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
public class AuthSession implements Serializable {
    private Long id;
    private String sessionId;
    private String tokenFamilyId;
    private String principalType;
    private Long principalId;
    private String refreshTokenHash;
    private String status;
    private LocalDateTime expiresAt;
    private LocalDateTime lastUsedAt;
    private LocalDateTime revokedAt;
    private String revokeReason;
    private String ipHash;
    private String deviceSummary;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
