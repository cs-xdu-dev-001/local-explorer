package com.localexplorer.controller;

import com.localexplorer.controller.admin.EmployeeController;
import com.localexplorer.controller.user.UserController;
import com.localexplorer.constant.ErrorCode;
import com.localexplorer.exception.BaseException;
import com.localexplorer.filter.RequestTracingFilter;
import com.localexplorer.handler.GlobalExceptionHandler;
import com.localexplorer.properties.AuthSecurityProperties;
import com.localexplorer.service.AuthRequestSecurity;
import com.localexplorer.service.AuthenticationResult;
import com.localexplorer.service.AuthenticationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockCookie;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuthControllerSessionTest {

    @Mock
    private AuthenticationService authenticationService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        AuthSecurityProperties properties = new AuthSecurityProperties();
        AuthRequestSecurity requestSecurity = new AuthRequestSecurity(properties);
        mockMvc = buildMvc(authenticationService, requestSecurity);
    }

    @Test
    void adminRefreshRotatesCookieAndKeepsRequestId() throws Exception {
        when(authenticationService.refresh(eq("EMPLOYEE"), any())).thenReturn(AuthenticationResult.builder()
                .id(1L).userName("admin").name("管理员").role("ADMIN")
                .accessToken("new-access").refreshToken("new-refresh")
                .accessExpiresInMillis(1_800_000L).build());

        mockMvc.perform(post("/admin/employee/refresh")
                        .header("X-Request-Id", "auth-refresh-1")
                        .header("Origin", "http://localhost")
                        .cookie(new MockCookie("LX_ADMIN_REFRESH", "old-refresh")))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", "auth-refresh-1"))
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("LX_ADMIN_REFRESH=new-refresh"),
                        org.hamcrest.Matchers.containsString("HttpOnly"),
                        org.hamcrest.Matchers.containsString("Path=/admin"))))
                .andExpect(jsonPath("$.data.token").value("new-access"));

        verify(authenticationService).refresh(eq("EMPLOYEE"), any());
    }

    @Test
    void userLogoutRevokesBackendSessionClearsCookieAndKeepsRequestId() throws Exception {
        mockMvc.perform(post("/user/user/logout")
                        .header("X-Request-Id", "auth-logout-1")
                        .header("Origin", "http://localhost")
                        .cookie(new MockCookie("LX_USER_REFRESH", "refresh-secret")))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", "auth-logout-1"))
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("LX_USER_REFRESH="),
                        org.hamcrest.Matchers.containsString("Max-Age=0"),
                        org.hamcrest.Matchers.containsString("Path=/user"))));

        verify(authenticationService).logout(eq("USER"), any());
    }

    @Test
    void refreshFromUntrustedOriginReturnsStructured403AndRequestId() throws Exception {
        AuthSecurityProperties properties = new AuthSecurityProperties();
        AuthRequestSecurity requestSecurity = new AuthRequestSecurity(properties);
        AuthenticationService realService = new AuthenticationService(
                null, null, null, null, null, null, requestSecurity, null);
        MockMvc originMvc = buildMvc(realService, requestSecurity);

        originMvc.perform(post("/admin/employee/refresh")
                        .header("X-Request-Id", "auth-origin-1")
                        .header("Origin", "https://evil.example")
                        .cookie(new MockCookie("LX_ADMIN_REFRESH", "refresh-secret")))
                .andExpect(status().isForbidden())
                .andExpect(header().string("X-Request-Id", "auth-origin-1"))
                .andExpect(jsonPath("$.code").value(40300))
                .andExpect(jsonPath("$.requestId").value("auth-origin-1"));
    }

    @Test
    void lockedLoginReturnsStructured429AndRequestId() throws Exception {
        when(authenticationService.loginEmployee(any(), any())).thenThrow(
                new BaseException(ErrorCode.TOO_MANY_REQUESTS, ErrorCode.TOO_MANY_REQUESTS.getDefaultMessage()));

        mockMvc.perform(post("/admin/employee/login")
                        .header("X-Request-Id", "auth-lock-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"123456\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("X-Request-Id", "auth-lock-1"))
                .andExpect(jsonPath("$.code").value(42900))
                .andExpect(jsonPath("$.requestId").value("auth-lock-1"));
    }

    private MockMvc buildMvc(AuthenticationService service, AuthRequestSecurity requestSecurity) {
        EmployeeController employeeController = new EmployeeController();
        ReflectionTestUtils.setField(employeeController, "authenticationService", service);
        ReflectionTestUtils.setField(employeeController, "authRequestSecurity", requestSecurity);
        UserController userController = new UserController();
        ReflectionTestUtils.setField(userController, "authenticationService", service);
        ReflectionTestUtils.setField(userController, "authRequestSecurity", requestSecurity);
        return MockMvcBuilders.standaloneSetup(employeeController, userController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new RequestTracingFilter())
                .build();
    }
}
