package com.localexplorer.service;

import com.localexplorer.constant.ErrorCode;
import com.localexplorer.constant.StatusConstant;
import com.localexplorer.dto.EmployeeLoginDTO;
import com.localexplorer.dto.UserLoginDTO;
import com.localexplorer.entity.Employee;
import com.localexplorer.entity.User;
import com.localexplorer.exception.AccountNotFoundException;
import com.localexplorer.exception.BaseException;
import com.localexplorer.exception.PasswordErrorException;
import com.localexplorer.mapper.EmployeeMapper;
import com.localexplorer.mapper.UserMapper;
import com.localexplorer.metrics.AuthenticationMetrics;
import com.localexplorer.service.impl.AuthSessionServiceImpl;
import com.localexplorer.service.impl.LoginProtectionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class AuthenticationService {
    private final EmployeeService employeeService;
    private final UserService userService;
    private final EmployeeMapper employeeMapper;
    private final UserMapper userMapper;
    private final AuthSessionService sessionService;
    private final LoginProtectionService protectionService;
    private final AuthRequestSecurity requestSecurity;
    private final AuthenticationMetrics metrics;

    public AuthenticationService(EmployeeService employeeService, UserService userService,
                                 EmployeeMapper employeeMapper, UserMapper userMapper,
                                 AuthSessionService sessionService, LoginProtectionService protectionService,
                                 AuthRequestSecurity requestSecurity, AuthenticationMetrics metrics) {
        this.employeeService = employeeService;
        this.userService = userService;
        this.employeeMapper = employeeMapper;
        this.userMapper = userMapper;
        this.sessionService = sessionService;
        this.protectionService = protectionService;
        this.requestSecurity = requestSecurity;
        this.metrics = metrics;
    }

    public AuthenticationResult loginEmployee(EmployeeLoginDTO dto, HttpServletRequest request) {
        return login(AuthSessionServiceImpl.EMPLOYEE, dto.getUsername(), request,
                () -> employeeResult(employeeService.login(dto), null));
    }

    public AuthenticationResult loginUser(UserLoginDTO dto, HttpServletRequest request) {
        return login(AuthSessionServiceImpl.USER, dto.getPhone(), request,
                () -> userResult(userService.login(dto), null));
    }

    public AuthenticationResult refresh(String principalType, HttpServletRequest request) {
        long started = System.nanoTime();
        requestSecurity.validateOrigin(request);
        String raw = requestSecurity.readRefreshCookie(request, principalType);
        IssuedAuthSession issued = sessionService.rotate(principalType, raw,
                requestSecurity.ipHash(request), requestSecurity.deviceSummary(request));
        try {
            if (AuthSessionServiceImpl.EMPLOYEE.equals(principalType)) {
                Employee employee = employeeMapper.getById(issued.getPrincipalId());
                if (employee == null || !StatusConstant.ENABLE.equals(employee.getStatus())) throw invalid();
                return employeeResult(employee, issued);
            }
            User user = userMapper.getById(issued.getPrincipalId());
            if (user == null || !StatusConstant.ENABLE.equals(user.getStatus())) throw invalid();
            return userResult(user, issued);
        } catch (BaseException ex) {
            sessionService.revokeAll(principalType, issued.getPrincipalId(), "ACCOUNT_UNAVAILABLE");
            throw ex;
        } finally {
            metrics.latency("refresh", principalType, System.nanoTime() - started);
        }
    }

    public void logout(String principalType, HttpServletRequest request) {
        requestSecurity.validateOrigin(request);
        String token = requestSecurity.readRefreshCookie(request, principalType);
        if (token != null) sessionService.revokeByRefreshToken(principalType, token, "LOGOUT");
    }

    public void logoutAll(String principalType, Long principalId, HttpServletRequest request) {
        requestSecurity.validateOrigin(request);
        sessionService.revokeAll(principalType, principalId, "LOGOUT_ALL");
    }

    private AuthenticationResult login(String type, String account, HttpServletRequest request, LoginAction action) {
        long started = System.nanoTime();
        requestSecurity.validateOrigin(request);
        String accountHash = requestSecurity.accountHash(type, account);
        String ipHash = requestSecurity.ipHash(request);
        try {
            protectionService.assertAllowed(type, accountHash, ipHash);
            AuthenticationResult result = action.run();
            protectionService.recordSuccess(type, accountHash, ipHash);
            IssuedAuthSession issued = sessionService.issue(type, result.getId(), ipHash,
                    requestSecurity.deviceSummary(request));
            applyIssued(result, issued);
            log.info("认证结果 principalType={} session={} result=success elapsedMs={}", type,
                    shortId(issued.getSessionId()), TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
            return result;
        } catch (AccountNotFoundException | PasswordErrorException ex) {
            protectionService.recordFailure(type, accountHash, ipHash, requestSecurity.accountHint(account));
            log.warn("认证结果 principalType={} session=none result=invalid elapsedMs={}", type,
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
            throw new BaseException(ErrorCode.AUTHENTICATION_FAILED, "账号或密码错误");
        } catch (BaseException ex) {
            String result = ErrorCode.TOO_MANY_REQUESTS.equals(ex.getErrorCode()) ? "locked" : "rejected";
            log.warn("认证结果 principalType={} session=none result={} elapsedMs={}", type, result,
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
            throw ex;
        } finally {
            metrics.latency("login", type, System.nanoTime() - started);
        }
    }

    private AuthenticationResult employeeResult(Employee employee, IssuedAuthSession issued) {
        AuthenticationResult result = AuthenticationResult.builder().id(employee.getId()).userName(employee.getUsername())
                .name(employee.getName()).role(employee.getRole()).build();
        applyIssued(result, issued);
        return result;
    }

    private AuthenticationResult userResult(User user, IssuedAuthSession issued) {
        AuthenticationResult result = AuthenticationResult.builder().id(user.getId()).name(user.getName())
                .phone(user.getPhone()).avatar(user.getAvatar()).build();
        applyIssued(result, issued);
        return result;
    }

    private void applyIssued(AuthenticationResult result, IssuedAuthSession issued) {
        if (issued == null) return;
        result.setSessionId(issued.getSessionId());
        result.setAccessToken(issued.getAccessToken());
        result.setRefreshToken(issued.getRefreshToken());
        result.setAccessExpiresInMillis(issued.getAccessExpiresInMillis());
    }

    private BaseException invalid() { return new BaseException(ErrorCode.AUTHENTICATION_FAILED, "账号不可用"); }
    private String shortId(String value) { return value == null ? "none" : value.substring(0, Math.min(8, value.length())); }
    private interface LoginAction { AuthenticationResult run(); }
}
