package com.localexplorer.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "explorer.auth")
public class AuthSecurityProperties {

    private long refreshTtlMillis = 604_800_000L;
    private String adminCookieName = "LX_ADMIN_REFRESH";
    private String userCookieName = "LX_USER_REFRESH";
    private boolean cookieSecure;
    private int loginFailureLimit = 5;
    private long loginWindowSeconds = 600L;
    private long loginLockSeconds = 900L;
    private String fingerprintSecret = "local-explorer-dev-fingerprint-secret";
    private boolean trustedProxyEnabled;
    private List<String> allowedOrigins = new ArrayList<>();
    private int cleanupBatchSize = 200;
    private int retentionDays = 30;
    private long replayGraceMillis = 2000L;
}
