package com.localexplorer.service.impl;

import com.localexplorer.constant.ErrorCode;
import com.localexplorer.entity.LoginGuard;
import com.localexplorer.exception.BaseException;
import com.localexplorer.mapper.LoginGuardMapper;
import com.localexplorer.metrics.AuthenticationMetrics;
import com.localexplorer.properties.AuthSecurityProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LoginProtectionServiceTest {

    private LoginGuardMapper mapper;
    private LoginProtectionService service;
    private LocalDateTime now;

    @BeforeEach
    void setUp() {
        mapper = mock(LoginGuardMapper.class);
        AuthSecurityProperties properties = new AuthSecurityProperties();
        properties.setLoginFailureLimit(5);
        properties.setLoginWindowSeconds(600);
        properties.setLoginLockSeconds(900);
        Clock clock = Clock.fixed(Instant.parse("2026-08-24T10:00:00Z"), ZoneOffset.UTC);
        now = LocalDateTime.ofInstant(clock.instant(), clock.getZone());
        service = new LoginProtectionService(
                mapper, properties, clock, mock(AuthenticationMetrics.class));
    }

    @Test
    void failureUsesOneAtomicUpsertWithConfiguredWindowAndLockBoundary() {
        LoginGuard guard = LoginGuard.builder()
                .id(3L).principalType("USER").accountHash("account")
                .ipHash("ip").failedCount(4).windowStartedAt(now.minusMinutes(2)).build();
        guard.setFailedCount(5);
        guard.setLockedUntil(now.plusMinutes(15));
        when(mapper.find("USER", "account", "ip")).thenReturn(guard);

        service.recordFailure("USER", "account", "ip", "138****1111");

        ArgumentCaptor<LoginGuard> captor = ArgumentCaptor.forClass(LoginGuard.class);
        verify(mapper).upsertFailure(captor.capture(), eq(now.minusMinutes(10)), eq(5), eq(now.plusMinutes(15)));
        assertThat(captor.getValue().getFailedCount()).isEqualTo(1);
        assertThat(captor.getValue().getLastFailedAt()).isEqualTo(now);
    }

    @Test
    void atomicUpsertReceivesFreshWindowStart() {
        LoginGuard guard = LoginGuard.builder()
                .id(3L).principalType("USER").accountHash("account")
                .ipHash("ip").failedCount(4).windowStartedAt(now.minusMinutes(11)).build();
        guard.setFailedCount(1);
        when(mapper.find("USER", "account", "ip")).thenReturn(guard);

        service.recordFailure("USER", "account", "ip", "138****1111");

        ArgumentCaptor<LoginGuard> captor = ArgumentCaptor.forClass(LoginGuard.class);
        verify(mapper).upsertFailure(captor.capture(), eq(now.minusMinutes(10)), eq(5), eq(now.plusMinutes(15)));
        assertThat(captor.getValue().getWindowStartedAt()).isEqualTo(now);
    }

    @Test
    void lockedTupleReturnsStable429() {
        when(mapper.find("EMPLOYEE", "account", "ip")).thenReturn(LoginGuard.builder()
                .lockedUntil(now.plusSeconds(1)).build());

        assertThatThrownBy(() -> service.assertAllowed("EMPLOYEE", "account", "ip"))
                .isInstanceOf(BaseException.class)
                .satisfies(ex -> assertThat(((BaseException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.TOO_MANY_REQUESTS));
    }

    @Test
    void lockExpiringAtCurrentInstantAllowsLogin() {
        when(mapper.find("EMPLOYEE", "account", "ip")).thenReturn(LoginGuard.builder()
                .lockedUntil(now).build());

        service.assertAllowed("EMPLOYEE", "account", "ip");

        verify(mapper).find("EMPLOYEE", "account", "ip");
    }

    @Test
    void successfulLoginClearsMatchingFailureState() {
        service.recordSuccess("USER", "account", "ip");

        verify(mapper).deleteByKey("USER", "account", "ip");
    }
}
