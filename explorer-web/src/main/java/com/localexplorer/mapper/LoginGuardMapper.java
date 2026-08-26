package com.localexplorer.mapper;

import com.github.pagehelper.Page;
import com.localexplorer.dto.LoginGuardPageQueryDTO;
import com.localexplorer.entity.LoginGuard;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface LoginGuardMapper {
    int upsertFailure(@Param("guard") LoginGuard guard,
                      @Param("windowCutoff") java.time.LocalDateTime windowCutoff,
                      @Param("failureLimit") int failureLimit,
                      @Param("lockUntil") java.time.LocalDateTime lockUntil);
    LoginGuard find(@Param("principalType") String principalType, @Param("accountHash") String accountHash,
                    @Param("ipHash") String ipHash);
    int deleteByKey(@Param("principalType") String principalType, @Param("accountHash") String accountHash,
                    @Param("ipHash") String ipHash);
    int deleteById(Long id);
    LoginGuard getById(Long id);
    Page<LoginGuard> pageLocked(LoginGuardPageQueryDTO dto);
}
