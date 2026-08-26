package com.localexplorer.controller.user;

import com.localexplorer.context.BaseContext;
import com.localexplorer.dto.UserLoginDTO;
import com.localexplorer.result.Result;
import com.localexplorer.service.AuthenticationResult;
import com.localexplorer.service.AuthenticationService;
import com.localexplorer.service.AuthRequestSecurity;
import com.localexplorer.service.impl.AuthSessionServiceImpl;
import com.localexplorer.vo.UserLoginVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("/user/user")
@Api(tags = "用户登录相关接口")
@Slf4j
public class UserController {

    @Autowired
    private AuthenticationService authenticationService;
    @Autowired
    private AuthRequestSecurity authRequestSecurity;

    @PostMapping("/login")
    @ApiOperation("用户登录")
    public Result<UserLoginVO> login(@Valid @RequestBody UserLoginDTO userLoginDTO,
                                    HttpServletRequest request, HttpServletResponse response) {
        log.info("收到用户登录请求");
        AuthenticationResult result = authenticationService.loginUser(userLoginDTO, request);
        authRequestSecurity.writeRefreshCookie(response, AuthSessionServiceImpl.USER, result.getRefreshToken());
        return Result.success(toLoginVO(result));
    }

    @PostMapping("/refresh")
    @ApiOperation("轮换用户刷新凭证")
    public Result<UserLoginVO> refresh(HttpServletRequest request, HttpServletResponse response) {
        AuthenticationResult result = authenticationService.refresh(AuthSessionServiceImpl.USER, request);
        authRequestSecurity.writeRefreshCookie(response, AuthSessionServiceImpl.USER, result.getRefreshToken());
        return Result.success(toLoginVO(result));
    }

    @PostMapping("/logout")
    @ApiOperation("用户退出当前会话")
    public Result<String> logout(HttpServletRequest request, HttpServletResponse response) {
        authenticationService.logout(AuthSessionServiceImpl.USER, request);
        authRequestSecurity.clearRefreshCookie(response, AuthSessionServiceImpl.USER);
        return Result.success();
    }

    @PostMapping("/logout-all")
    @ApiOperation("用户退出全部会话")
    public Result<String> logoutAll(HttpServletRequest request, HttpServletResponse response) {
        authenticationService.logoutAll(AuthSessionServiceImpl.USER, BaseContext.getCurrentId(), request);
        authRequestSecurity.clearRefreshCookie(response, AuthSessionServiceImpl.USER);
        return Result.success();
    }

    private UserLoginVO toLoginVO(AuthenticationResult result) {
        return UserLoginVO.builder().id(result.getId()).name(result.getName()).phone(result.getPhone())
                .avatar(result.getAvatar()).token(result.getAccessToken())
                .accessExpiresInMillis(result.getAccessExpiresInMillis()).build();
    }
}
