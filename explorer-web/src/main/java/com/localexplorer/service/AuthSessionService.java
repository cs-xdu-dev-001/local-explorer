package com.localexplorer.service;

import com.localexplorer.vo.AuthSessionStatsVO;

public interface AuthSessionService {
    IssuedAuthSession issue(String principalType, Long principalId, String ipHash, String deviceSummary);
    IssuedAuthSession rotate(String principalType, String refreshToken, String ipHash, String deviceSummary);
    boolean isActive(String sessionId, String principalType, Long principalId);
    int revokeSession(String sessionId, String reason);
    int revokeByRefreshToken(String principalType, String refreshToken, String reason);
    int revokeAll(String principalType, Long principalId, String reason);
    AuthSessionStatsVO stats();
    int cleanup();
}
