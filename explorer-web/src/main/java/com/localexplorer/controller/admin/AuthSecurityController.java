package com.localexplorer.controller.admin;

import com.localexplorer.annotation.RequireAdmin;
import com.localexplorer.annotation.OperationLog;
import com.localexplorer.dto.LoginGuardPageQueryDTO;
import com.localexplorer.result.PageResult;
import com.localexplorer.result.Result;
import com.localexplorer.service.AuthSecurityAdminService;
import com.localexplorer.vo.AuthSessionStatsVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

@RestController
@RequestMapping("/admin/auth-security")
@RequireAdmin
@Api(tags = "认证安全运维接口")
public class AuthSecurityController {
    private final AuthSecurityAdminService service;

    public AuthSecurityController(AuthSecurityAdminService service) { this.service = service; }

    @GetMapping("/sessions/stats")
    @ApiOperation("查询会话状态统计")
    public Result<AuthSessionStatsVO> stats() { return Result.success(service.sessionStats()); }

    @GetMapping("/lockouts")
    @ApiOperation("查询当前登录锁定")
    public Result<PageResult> lockouts(@Valid LoginGuardPageQueryDTO dto) {
        return Result.success(service.locked(dto));
    }

    @DeleteMapping("/lockouts/{id}")
    @ApiOperation("解除登录锁定")
    @OperationLog("解除登录锁定")
    public Result<Void> unlock(@PathVariable Long id) {
        service.unlock(id);
        return Result.success();
    }
}
