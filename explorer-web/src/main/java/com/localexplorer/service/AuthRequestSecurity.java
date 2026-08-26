package com.localexplorer.service;

import com.localexplorer.constant.ErrorCode;
import com.localexplorer.exception.BaseException;
import com.localexplorer.properties.AuthSecurityProperties;
import com.localexplorer.service.impl.AuthSessionServiceImpl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Locale;

@Component
public class AuthRequestSecurity {
    private final AuthSecurityProperties properties;

    public AuthRequestSecurity(AuthSecurityProperties properties) {
        this.properties = properties;
    }

    public void validateOrigin(HttpServletRequest request) {
        String origin = request.getHeader("Origin");
        if (origin == null || origin.trim().isEmpty()) return;
        String ownOrigin = request.getScheme() + "://" + request.getServerName()
                + (isDefaultPort(request) ? "" : ":" + request.getServerPort());
        if (origin.equalsIgnoreCase(ownOrigin) || properties.getAllowedOrigins().stream()
                .anyMatch(item -> origin.equalsIgnoreCase(item))) return;
        throw new BaseException(ErrorCode.FORBIDDEN, "请求来源不受信任");
    }

    public String accountHash(String principalType, String account) {
        return digest(principalType + ":account:" + normalize(account));
    }

    public String ipHash(HttpServletRequest request) {
        String ip = request.getRemoteAddr();
        if (properties.isTrustedProxyEnabled()) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.trim().isEmpty()) ip = forwarded.split(",")[0].trim();
        }
        return digest("ip:" + normalize(ip));
    }

    public String ipFingerprint(HttpServletRequest request) {
        return ipHash(request).substring(0, 16);
    }

    public String deviceSummary(HttpServletRequest request) {
        String agent = request.getHeader("User-Agent");
        if (agent == null) return "Unknown client";
        String os = agent.contains("Windows") ? "Windows" : agent.contains("Android") ? "Android"
                : agent.contains("iPhone") || agent.contains("iPad") ? "iOS" : "Other OS";
        String browser = agent.contains("Edg/") ? "Edge" : agent.contains("Chrome/") ? "Chrome"
                : agent.contains("Firefox/") ? "Firefox" : agent.contains("Safari/") ? "Safari" : "Other browser";
        return browser + " / " + os;
    }

    public String accountHint(String account) {
        String value = normalize(account);
        if (value.length() <= 2) return "**";
        if (value.length() == 11 && value.chars().allMatch(Character::isDigit)) {
            return value.substring(0, 3) + "****" + value.substring(7);
        }
        return value.substring(0, 1) + "***" + value.substring(value.length() - 1);
    }

    public String readRefreshCookie(HttpServletRequest request, String principalType) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        String name = cookieName(principalType);
        for (Cookie cookie : cookies) if (name.equals(cookie.getName())) return cookie.getValue();
        return null;
    }

    public void writeRefreshCookie(HttpServletResponse response, String principalType, String token) {
        ResponseCookie cookie = ResponseCookie.from(cookieName(principalType), token)
                .httpOnly(true).secure(properties.isCookieSecure()).sameSite("Lax")
                .path(cookiePath(principalType)).maxAge(Duration.ofMillis(properties.getRefreshTtlMillis())).build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    public void clearRefreshCookie(HttpServletResponse response, String principalType) {
        ResponseCookie cookie = ResponseCookie.from(cookieName(principalType), "")
                .httpOnly(true).secure(properties.isCookieSecure()).sameSite("Lax")
                .path(cookiePath(principalType)).maxAge(Duration.ZERO).build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private String cookieName(String type) {
        return AuthSessionServiceImpl.EMPLOYEE.equals(type)
                ? properties.getAdminCookieName() : properties.getUserCookieName();
    }
    private String cookiePath(String type) { return AuthSessionServiceImpl.EMPLOYEE.equals(type) ? "/admin" : "/user"; }
    private boolean isDefaultPort(HttpServletRequest request) {
        return ("http".equalsIgnoreCase(request.getScheme()) && request.getServerPort() == 80)
                || ("https".equalsIgnoreCase(request.getScheme()) && request.getServerPort() == 443);
    }
    private String normalize(String value) { return value == null ? "" : value.trim().toLowerCase(Locale.ROOT); }
    private String digest(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(
                    (properties.getFingerprintSecret() + ":" + value).getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(64);
            for (byte item : bytes) result.append(String.format("%02x", item));
            return result.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
