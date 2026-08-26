package com.localexplorer.service.impl;

import com.localexplorer.constant.ErrorCode;
import com.localexplorer.entity.AuthSession;
import com.localexplorer.exception.BaseException;
import com.localexplorer.mapper.AuthSessionMapper;
import com.localexplorer.metrics.AuthenticationMetrics;
import com.localexplorer.properties.AuthSecurityProperties;
import com.localexplorer.properties.JwtProperties;
import com.localexplorer.service.IssuedAuthSession;
import com.localexplorer.vo.AuthSessionStatsVO;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthSessionServiceImplTest {

    private AuthSessionMapper mapper;
    private AuthSessionServiceImpl service;
    private JwtProperties jwt;
    private Clock clock;

    @BeforeEach
    void setUp() {
        mapper = mock(AuthSessionMapper.class);
        jwt = new JwtProperties();
        jwt.setAdminSecretKey("admin-test-secret-that-is-long-enough");
        jwt.setAdminTtl(1_800_000L);
        jwt.setUserSecretKey("user-test-secret-that-is-long-enough");
        jwt.setUserTtl(1_800_000L);
        AuthSecurityProperties security = new AuthSecurityProperties();
        security.setRefreshTtlMillis(604_800_000L);
        clock = Clock.fixed(Instant.parse("2026-08-24T10:00:00Z"), ZoneOffset.UTC);
        service = new AuthSessionServiceImpl(
                mapper, jwt, security, clock, mock(AuthenticationMetrics.class));
    }

    @Test
    void issueStoresOnlyRefreshHashAndAddsRequiredAccessClaims() {
        IssuedAuthSession issued = service.issue("EMPLOYEE", 7L, "ip-hash", "Chrome Windows");

        ArgumentCaptor<AuthSession> captor = ArgumentCaptor.forClass(AuthSession.class);
        verify(mapper).insert(captor.capture());
        AuthSession stored = captor.getValue();
        assertThat(stored.getRefreshTokenHash()).isNotEqualTo(issued.getRefreshToken());
        assertThat(stored.getRefreshTokenHash()).hasSize(64);
        assertThat(stored.getStatus()).isEqualTo("ACTIVE");

        Claims claims = Jwts.parser()
                .setClock(() -> new Date(clock.millis()))
                .setSigningKey(jwt.getAdminSecretKey().getBytes(java.nio.charset.StandardCharsets.UTF_8))
                .parseClaimsJws(issued.getAccessToken())
                .getBody();
        assertThat(claims.get("sessionId")).isEqualTo(stored.getSessionId());
        assertThat(claims.get("tokenType")).isEqualTo("ACCESS");
        assertThat(claims.get("principalType")).isEqualTo("EMPLOYEE");
        assertThat(claims.getId()).isNotBlank();
        assertThat(claims.getIssuedAt()).isNotNull();
    }

    @Test
    void rotateConsumesOldTokenOnceAndCreatesOneSuccessor() {
        AuthSession current = activeSession("old-session", "family-1", 7L);
        when(mapper.getByRefreshTokenHash(any())).thenReturn(current);
        when(mapper.rotateIfActive("old-session", service.now())).thenReturn(1);

        IssuedAuthSession next = service.rotate("EMPLOYEE", "raw-old-token", "ip-2", "Edge Windows");

        assertThat(next.getSessionId()).isNotEqualTo("old-session");
        ArgumentCaptor<AuthSession> captor = ArgumentCaptor.forClass(AuthSession.class);
        verify(mapper).insert(captor.capture());
        assertThat(captor.getValue().getTokenFamilyId()).isEqualTo("family-1");
        assertThat(captor.getValue().getPrincipalId()).isEqualTo(7L);
    }

    @Test
    void casLoserFailsWithoutRevokingWinningSuccessor() {
        AuthSession current = activeSession("old-session", "family-1", 7L);
        when(mapper.getByRefreshTokenHash(any())).thenReturn(current);
        when(mapper.rotateIfActive("old-session", service.now())).thenReturn(0);

        assertThatThrownBy(() -> service.rotate("EMPLOYEE", "raw-old-token", "ip", "device"))
                .isInstanceOf(BaseException.class)
                .satisfies(ex -> assertThat(((BaseException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.AUTHENTICATION_FAILED));

        verify(mapper, never()).revokeFamily(any(), any(), any());
        verify(mapper, never()).insert(any());
    }

    @Test
    void observedRotatedTokenRevokesWholeFamilyAsReplay() {
        AuthSession replayed = activeSession("old-session", "family-1", 7L);
        replayed.setStatus("ROTATED");
        when(mapper.getByRefreshTokenHash(any())).thenReturn(replayed);

        assertThatThrownBy(() -> service.rotate("EMPLOYEE", "raw-old-token", "ip", "device"))
                .isInstanceOf(BaseException.class)
                .satisfies(ex -> assertThat(((BaseException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.AUTHENTICATION_FAILED));

        verify(mapper).revokeFamily("family-1", service.now(), "REFRESH_REPLAY");
    }

    @Test
    void immediateConcurrentObservationDoesNotRevokeWinningFamily() {
        AuthSession replayed = activeSession("old-session", "family-1", 7L);
        replayed.setStatus("ROTATED");
        replayed.setLastUsedAt(service.now());
        when(mapper.getByRefreshTokenHash(any())).thenReturn(replayed);

        assertThatThrownBy(() -> service.rotate("EMPLOYEE", "raw-old-token", "ip", "device"))
                .isInstanceOf(BaseException.class);

        verify(mapper, never()).revokeFamily(any(), any(), any());
    }

    @Test
    void refreshAtOrPastExpiryBoundaryIsRejectedWithoutSideEffects() {
        for (LocalDateTime expiresAt : Arrays.asList(service.now(), service.now().minusNanos(1))) {
            AuthSession expired = activeSession("expired-session", "family-expired", 7L);
            expired.setExpiresAt(expiresAt);
            when(mapper.getByRefreshTokenHash(any())).thenReturn(expired);

            assertThatThrownBy(() -> service.rotate("EMPLOYEE", "expired-refresh", "ip", "device"))
                    .isInstanceOf(BaseException.class)
                    .satisfies(ex -> assertThat(((BaseException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.AUTHENTICATION_FAILED));
        }

        verify(mapper, never()).rotateIfActive(any(), any());
        verify(mapper, never()).insert(any());
        verify(mapper, never()).revokeFamily(any(), any(), any());
    }

    @Test
    void emptySessionStatisticsAreReturnedAsZeros() {
        when(mapper.stats()).thenReturn(AuthSessionStatsVO.builder().build());

        AuthSessionStatsVO stats = service.stats();

        assertThat(stats.getActive()).isZero();
        assertThat(stats.getRotated()).isZero();
        assertThat(stats.getRevoked()).isZero();
        assertThat(stats.getExpired()).isZero();
    }

    private AuthSession activeSession(String sessionId, String familyId, Long principalId) {
        return AuthSession.builder()
                .sessionId(sessionId)
                .tokenFamilyId(familyId)
                .principalType("EMPLOYEE")
                .principalId(principalId)
                .status("ACTIVE")
                .expiresAt(service.now().plusDays(1))
                .build();
    }
}
