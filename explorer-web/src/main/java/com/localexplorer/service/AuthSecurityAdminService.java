package com.localexplorer.service;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.localexplorer.constant.ErrorCode;
import com.localexplorer.dto.LoginGuardPageQueryDTO;
import com.localexplorer.entity.LoginGuard;
import com.localexplorer.exception.BaseException;
import com.localexplorer.mapper.LoginGuardMapper;
import com.localexplorer.result.PageResult;
import com.localexplorer.vo.AuthSessionStatsVO;
import org.springframework.stereotype.Service;

@Service
public class AuthSecurityAdminService {
    private final LoginGuardMapper loginGuardMapper;
    private final AuthSessionService authSessionService;

    public AuthSecurityAdminService(LoginGuardMapper loginGuardMapper, AuthSessionService authSessionService) {
        this.loginGuardMapper = loginGuardMapper;
        this.authSessionService = authSessionService;
    }

    public PageResult locked(LoginGuardPageQueryDTO dto) {
        PageHelper.startPage(dto.getPage(), dto.getPageSize());
        Page<LoginGuard> page = loginGuardMapper.pageLocked(dto);
        return new PageResult(page.getTotal(), page.getResult());
    }

    public void unlock(Long id) {
        if (loginGuardMapper.getById(id) == null) throw new BaseException(ErrorCode.NOT_FOUND, "锁定记录不存在");
        loginGuardMapper.deleteById(id);
    }

    public AuthSessionStatsVO sessionStats() { return authSessionService.stats(); }
}
