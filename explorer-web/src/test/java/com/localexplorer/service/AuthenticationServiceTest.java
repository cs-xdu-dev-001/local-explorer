package com.localexplorer.service;

import com.localexplorer.constant.ErrorCode;
import com.localexplorer.constant.StatusConstant;
import com.localexplorer.dto.EmployeeLoginDTO;
import com.localexplorer.entity.User;
import com.localexplorer.exception.AccountNotFoundException;
import com.localexplorer.exception.BaseException;
import com.localexplorer.exception.PasswordErrorException;
import com.localexplorer.mapper.EmployeeMapper;
import com.localexplorer.mapper.UserMapper;
import com.localexplorer.metrics.AuthenticationMetrics;
import com.localexplorer.properties.AuthSecurityProperties;
import com.localexplorer.service.impl.LoginProtectionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockCookie;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    @Mock private EmployeeService employeeService;
    @Mock private UserService userService;
    @Mock private EmployeeMapper employeeMapper;
    @Mock private UserMapper userMapper;
    @Mock private AuthSessionService sessionService;
    @Mock private LoginProtectionService protectionService;
    @Mock private AuthenticationMetrics metrics;
    private AuthenticationService service;

    @BeforeEach
    void setUp() {
        AuthSecurityProperties properties = new AuthSecurityProperties();
        properties.setFingerprintSecret("authentication-test-secret");
        service = new AuthenticationService(employeeService, userService, employeeMapper, userMapper,
                sessionService, protectionService, new AuthRequestSecurity(properties), metrics);
    }

    @Test
    void unknownAccountAndWrongPasswordExposeTheSamePublicFailure() {
        EmployeeLoginDTO dto = employeeLogin();
        when(employeeService.login(dto))
                .thenThrow(new AccountNotFoundException())
                .thenThrow(new PasswordErrorException());

        assertGenericLoginFailure(() -> service.loginEmployee(dto, request()));
        assertGenericLoginFailure(() -> service.loginEmployee(dto, request()));

        verify(protectionService, org.mockito.Mockito.times(2))
                .recordFailure(org.mockito.ArgumentMatchers.eq("EMPLOYEE"), any(), any(), any());
    }

    @Test
    void disabledAccountDuringRefreshRevokesEverySession() {
        MockHttpServletRequest request = request();
        request.setCookies(new MockCookie("LX_USER_REFRESH", "raw-refresh"));
        IssuedAuthSession issued = new IssuedAuthSession(
                "session-1", "USER", 9L, "access", "next-refresh", 1_800_000L);
        when(sessionService.rotate(org.mockito.ArgumentMatchers.eq("USER"), org.mockito.ArgumentMatchers.eq("raw-refresh"), any(), any()))
                .thenReturn(issued);
        when(userMapper.getById(9L)).thenReturn(User.builder().id(9L).status(StatusConstant.DISABLE).build());

        assertThatThrownBy(() -> service.refresh("USER", request))
                .isInstanceOf(BaseException.class)
                .satisfies(ex -> assertThat(((BaseException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.AUTHENTICATION_FAILED));

        verify(sessionService).revokeAll("USER", 9L, "ACCOUNT_UNAVAILABLE");
    }

    @Test
    void logoutUsesRefreshCookieToRevokeCurrentServerSession() {
        MockHttpServletRequest request = request();
        request.setCookies(new MockCookie("LX_ADMIN_REFRESH", "raw-refresh"));

        service.logout("EMPLOYEE", request);

        verify(sessionService).revokeByRefreshToken("EMPLOYEE", "raw-refresh", "LOGOUT");
    }

    private void assertGenericLoginFailure(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action)
                .isInstanceOf(BaseException.class)
                .satisfies(ex -> {
                    BaseException failure = (BaseException) ex;
                    assertThat(failure.getErrorCode()).isEqualTo(ErrorCode.AUTHENTICATION_FAILED);
                    assertThat(failure.getMessage()).isEqualTo("账号或密码错误");
                });
    }

    private EmployeeLoginDTO employeeLogin() {
        EmployeeLoginDTO dto = new EmployeeLoginDTO();
        dto.setUsername("unknown");
        dto.setPassword("wrong");
        return dto;
    }

    private MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setScheme("http");
        request.setServerName("localhost");
        request.setServerPort(8080);
        request.setRemoteAddr("192.0.2.10");
        return request;
    }
}
