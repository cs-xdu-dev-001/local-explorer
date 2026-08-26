package com.localexplorer.service.impl;

import com.localexplorer.constant.ErrorCode;
import com.localexplorer.entity.LoginGuard;
import com.localexplorer.exception.BaseException;
import com.localexplorer.mapper.LoginGuardMapper;
import com.localexplorer.metrics.AuthenticationMetrics;
import com.localexplorer.properties.AuthSecurityProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
@Slf4j
public class LoginProtectionService {
    private final LoginGuardMapper mapper;
    private final AuthSecurityProperties properties;
    private final Clock clock;
    private final AuthenticationMetrics metrics;

    public LoginProtectionService(LoginGuardMapper mapper, AuthSecurityProperties properties,
                                  Clock clock, AuthenticationMetrics metrics) {
        this.mapper = mapper;
        this.properties = properties;
        this.clock = clock;
        this.metrics = metrics;
    }

    public void assertAllowed(String principalType, String accountHash, String ipHash) {
        LoginGuard guard = mapper.find(principalType, accountHash, ipHash);
        if (guard != null && guard.getLockedUntil() != null && guard.getLockedUntil().isAfter(now())) {
            metrics.login(principalType, "locked");
            log.warn("登录被锁定 principalType={} result=locked", principalType);
            throw new BaseException(ErrorCode.TOO_MANY_REQUESTS, ErrorCode.TOO_MANY_REQUESTS.getDefaultMessage());
        }
    }

    @Transactional
    public void recordFailure(String principalType, String accountHash, String ipHash, String accountHint) {
        LocalDateTime now = now();
        LoginGuard fresh = LoginGuard.builder().principalType(principalType).accountHash(accountHash)
                .ipHash(ipHash).accountHint(accountHint).failedCount(1).windowStartedAt(now)
                .lastFailedAt(now).createTime(now).updateTime(now).build();
        mapper.upsertFailure(fresh, now.minusSeconds(properties.getLoginWindowSeconds()),
                properties.getLoginFailureLimit(), now.plusSeconds(properties.getLoginLockSeconds()));
        LoginGuard guard = mapper.find(principalType, accountHash, ipHash);
        if (guard == null || guard.getFailedCount() == null) {
            throw new BaseException(ErrorCode.INTERNAL_ERROR, "登录保护状态写入失败");
        }
        metrics.login(principalType,
                guard.getFailedCount() >= properties.getLoginFailureLimit() ? "locked" : "invalid");
        log.warn("登录失败 principalType={} accountHint={} result={} failedCount={}", principalType,
                accountHint, guard.getFailedCount() >= properties.getLoginFailureLimit() ? "locked" : "invalid",
                guard.getFailedCount());
    }

    public void recordSuccess(String principalType, String accountHash, String ipHash) {
        mapper.deleteByKey(principalType, accountHash, ipHash);
        metrics.login(principalType, "success");
    }

    LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), clock.getZone());
    }
}
