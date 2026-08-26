package com.localexplorer.service.impl;

import com.localexplorer.constant.ErrorCode;
import com.localexplorer.constant.JwtClaimsConstant;
import com.localexplorer.entity.AuthSession;
import com.localexplorer.exception.BaseException;
import com.localexplorer.exception.RefreshReplayException;
import com.localexplorer.mapper.AuthSessionMapper;
import com.localexplorer.metrics.AuthenticationMetrics;
import com.localexplorer.properties.AuthSecurityProperties;
import com.localexplorer.properties.JwtProperties;
import com.localexplorer.service.AuthSessionService;
import com.localexplorer.service.IssuedAuthSession;
import com.localexplorer.utils.JwtUtil;
import com.localexplorer.vo.AuthSessionStatsVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class AuthSessionServiceImpl implements AuthSessionService {
    public static final String EMPLOYEE = "EMPLOYEE";
    public static final String USER = "USER";
    private final AuthSessionMapper mapper;
    private final JwtProperties jwt;
    private final AuthSecurityProperties security;
    private final Clock clock;
    private final AuthenticationMetrics metrics;
    private final SecureRandom random = new SecureRandom();

    public AuthSessionServiceImpl(AuthSessionMapper mapper, JwtProperties jwt,
                                  AuthSecurityProperties security, Clock clock,
                                  AuthenticationMetrics metrics) {
        this.mapper = mapper;
        this.jwt = jwt;
        this.security = security;
        this.clock = clock;
        this.metrics = metrics;
    }

    @Override
    @Transactional
    public IssuedAuthSession issue(String principalType, Long principalId, String ipHash, String deviceSummary) {
        return create(principalType, principalId, randomId(), ipHash, deviceSummary);
    }

    @Override
    @Transactional(noRollbackFor = RefreshReplayException.class)
    public IssuedAuthSession rotate(String principalType, String refreshToken, String ipHash, String deviceSummary) {
        long started = System.nanoTime();
        if (refreshToken == null || refreshToken.trim().isEmpty()) {
            logRefresh(principalType, "none", "missing", started);
            throw invalidRefresh();
        }
        AuthSession current = mapper.getByRefreshTokenHash(hash(refreshToken));
        LocalDateTime now = now();
        if (current == null || !principalType.equals(current.getPrincipalType())
                || current.getExpiresAt() == null || !current.getExpiresAt().isAfter(now)) {
            metrics.refresh(principalType, "invalid");
            logRefresh(principalType, "none", "invalid", started);
            throw invalidRefresh();
        }
        if ("ROTATED".equals(current.getStatus())) {
            if (current.getLastUsedAt() != null
                    && current.getLastUsedAt().plusNanos(security.getReplayGraceMillis() * 1_000_000L).isAfter(now)) {
                metrics.refresh(principalType, "cas_conflict");
                logRefresh(principalType, current.getSessionId(), "concurrent", started);
                throw invalidRefresh();
            }
            mapper.revokeFamily(current.getTokenFamilyId(), now, "REFRESH_REPLAY");
            metrics.refresh(principalType, "replay");
            logRefresh(principalType, current.getSessionId(), "replay_family_revoked", started);
            throw new RefreshReplayException();
        }
        if (!"ACTIVE".equals(current.getStatus())) {
            metrics.refresh(principalType, "revoked");
            logRefresh(principalType, current.getSessionId(), "revoked", started);
            throw invalidRefresh();
        }
        if (mapper.rotateIfActive(current.getSessionId(), now) != 1) {
            metrics.refresh(principalType, "cas_conflict");
            logRefresh(principalType, current.getSessionId(), "cas_conflict", started);
            throw invalidRefresh();
        }
        IssuedAuthSession issued = create(principalType, current.getPrincipalId(), current.getTokenFamilyId(),
                ipHash, deviceSummary);
        metrics.refresh(principalType, "success");
        logRefresh(principalType, issued.getSessionId(), "success", started);
        return issued;
    }

    private IssuedAuthSession create(String principalType, Long principalId, String familyId,
                                     String ipHash, String deviceSummary) {
        LocalDateTime now = now();
        String rawRefresh = randomToken();
        String sessionId = randomId();
        AuthSession session = AuthSession.builder().sessionId(sessionId).tokenFamilyId(familyId)
                .principalType(principalType).principalId(principalId).refreshTokenHash(hash(rawRefresh))
                .status("ACTIVE").expiresAt(now.plusNanos(security.getRefreshTtlMillis() * 1_000_000L))
                .ipHash(ipHash).deviceSummary(trim(deviceSummary, 120)).createTime(now).updateTime(now).build();
        mapper.insert(session);
        long accessTtl = EMPLOYEE.equals(principalType) ? jwt.getAdminTtl() : jwt.getUserTtl();
        String secret = EMPLOYEE.equals(principalType) ? jwt.getAdminSecretKey() : jwt.getUserSecretKey();
        Map<String, Object> claims = new HashMap<>();
        claims.put(JwtClaimsConstant.SESSION_ID, sessionId);
        claims.put(JwtClaimsConstant.TOKEN_TYPE, "ACCESS");
        claims.put(JwtClaimsConstant.PRINCIPAL_TYPE, principalType);
        claims.put(EMPLOYEE.equals(principalType) ? JwtClaimsConstant.EMP_ID : JwtClaimsConstant.USER_ID, principalId);
        String access = JwtUtil.createJWT(secret, accessTtl, claims, clock);
        return new IssuedAuthSession(sessionId, principalType, principalId, access, rawRefresh, accessTtl);
    }

    @Override
    public boolean isActive(String sessionId, String principalType, Long principalId) {
        AuthSession session = mapper.getBySessionId(sessionId);
        return session != null && "ACTIVE".equals(session.getStatus())
                && principalType.equals(session.getPrincipalType()) && principalId.equals(session.getPrincipalId())
                && session.getExpiresAt() != null && session.getExpiresAt().isAfter(now());
    }

    @Override
    public int revokeSession(String sessionId, String reason) {
        return mapper.revokeSession(sessionId, now(), trim(reason, 64));
    }

    @Override
    public int revokeByRefreshToken(String principalType, String refreshToken, String reason) {
        long started = System.nanoTime();
        if (refreshToken == null || refreshToken.trim().isEmpty()) {
            logRevoke(principalType, "none", "missing", 0, started);
            return 0;
        }
        AuthSession session = mapper.getByRefreshTokenHash(hash(refreshToken));
        if (session == null || !principalType.equals(session.getPrincipalType())) {
            logRevoke(principalType, "none", "not_found", 0, started);
            return 0;
        }
        int count = revokeSession(session.getSessionId(), reason);
        metrics.revoked(principalType, count);
        logRevoke(principalType, session.getSessionId(), count > 0 ? "success" : "already_inactive",
                count, started);
        return count;
    }

    @Override
    public int revokeAll(String principalType, Long principalId, String reason) {
        long started = System.nanoTime();
        int count = mapper.revokeAll(principalType, principalId, now(), trim(reason, 64));
        metrics.revoked(principalType, count);
        logRevoke(principalType, "none", count > 0 ? "success" : "already_inactive", count, started);
        return count;
    }

    @Override
    public AuthSessionStatsVO stats() {
        AuthSessionStatsVO stats = mapper.stats();
        if (stats == null) stats = AuthSessionStatsVO.builder().build();
        stats.setActive(zero(stats.getActive()));
        stats.setRotated(zero(stats.getRotated()));
        stats.setRevoked(zero(stats.getRevoked()));
        stats.setExpired(zero(stats.getExpired()));
        return stats;
    }

    @Override
    @Transactional
    public int cleanup() {
        LocalDateTime now = now();
        int changed = mapper.expireActive(now);
        changed += mapper.deleteTerminalBefore(now.minusDays(security.getRetentionDays()), security.getCleanupBatchSize());
        return changed;
    }

    public LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), clock.getZone());
    }

    private BaseException invalidRefresh() {
        return new BaseException(ErrorCode.AUTHENTICATION_FAILED, "刷新凭证无效或已失效");
    }

    private void logRevoke(String principalType, String sessionId, String result, int count, long started) {
        log.info("会话撤销 principalType={} session={} result={} count={} elapsedMs={}", principalType,
                shortId(sessionId), result, count,
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String randomId() { return UUID.randomUUID().toString().replace("-", ""); }

    public static String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(64);
            for (byte item : digest) result.append(String.format("%02x", item));
            return result.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private String trim(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }

    private long zero(Long value) { return value == null ? 0L : value; }

    private void logRefresh(String principalType, String sessionId, String result, long started) {
        log.info("刷新会话 principalType={} session={} result={} elapsedMs={}", principalType,
                shortId(sessionId), result, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
    }

    private String shortId(String value) {
        return value == null ? "none" : value.substring(0, Math.min(8, value.length()));
    }
}
