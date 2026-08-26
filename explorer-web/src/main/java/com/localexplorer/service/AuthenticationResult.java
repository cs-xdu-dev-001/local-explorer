package com.localexplorer.service;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AuthenticationResult {
    private Long id;
    private String userName;
    private String name;
    private String phone;
    private String avatar;
    private String role;
    private String sessionId;
    private String accessToken;
    private String refreshToken;
    private long accessExpiresInMillis;
}
