package com.localexplorer.mapper;

import com.localexplorer.entity.AuthSession;
import com.localexplorer.vo.AuthSessionStatsVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface AuthSessionMapper {
    void insert(AuthSession session);
    AuthSession getByRefreshTokenHash(String refreshTokenHash);
    AuthSession getBySessionId(String sessionId);
    int rotateIfActive(@Param("sessionId") String sessionId, @Param("now") LocalDateTime now);
    int revokeSession(@Param("sessionId") String sessionId, @Param("now") LocalDateTime now,
                      @Param("reason") String reason);
    int revokeFamily(@Param("familyId") String familyId, @Param("now") LocalDateTime now,
                     @Param("reason") String reason);
    int revokeAll(@Param("principalType") String principalType, @Param("principalId") Long principalId,
                  @Param("now") LocalDateTime now, @Param("reason") String reason);
    int expireActive(@Param("now") LocalDateTime now);
    int deleteTerminalBefore(@Param("cutoff") LocalDateTime cutoff, @Param("limit") int limit);
    AuthSessionStatsVO stats();
}
