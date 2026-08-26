package com.localexplorer.service;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class IssuedAuthSession {
    private String sessionId;
    private String principalType;
    private Long principalId;
    private String accessToken;
    private String refreshToken;
    private long accessExpiresInMillis;
}
