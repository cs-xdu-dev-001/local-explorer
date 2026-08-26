package com.localexplorer.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.localexplorer.constant.ErrorCode;
import com.localexplorer.constant.JwtClaimsConstant;
import com.localexplorer.constant.StatusConstant;
import com.localexplorer.context.BaseContext;
import com.localexplorer.entity.Employee;
import com.localexplorer.entity.User;
import com.localexplorer.filter.RequestTracingFilter;
import com.localexplorer.handler.ApiErrorResponseWriter;
import com.localexplorer.mapper.EmployeeMapper;
import com.localexplorer.mapper.UserMapper;
import com.localexplorer.properties.JwtProperties;
import com.localexplorer.utils.JwtUtil;
import com.localexplorer.service.AuthSessionService;
import com.localexplorer.service.impl.AuthSessionServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class JwtTokenInterceptorTest {

    private JwtTokenAdminInterceptor adminInterceptor;
    private JwtTokenUserInterceptor userInterceptor;
    private HandlerMethod handlerMethod;
    private EmployeeMapper employeeMapper;
    private UserMapper userMapper;
    private AuthSessionService authSessionService;

    @BeforeEach
    void setUp() throws Exception {
        JwtProperties jwtProperties = new JwtProperties();
        jwtProperties.setAdminSecretKey("test-admin-secret");
        jwtProperties.setAdminTtl(3600000L);
        jwtProperties.setAdminTokenName("Admin-Token");
        jwtProperties.setUserSecretKey("test-user-secret");
        jwtProperties.setUserTtl(3600000L);
        jwtProperties.setUserTokenName("User-Token");

        adminInterceptor = new JwtTokenAdminInterceptor();
        ReflectionTestUtils.setField(adminInterceptor, "jwtProperties", jwtProperties);
        employeeMapper = mock(EmployeeMapper.class);
        ReflectionTestUtils.setField(adminInterceptor, "employeeMapper", employeeMapper);
        ReflectionTestUtils.setField(
                adminInterceptor,
                "apiErrorResponseWriter",
                new ApiErrorResponseWriter(new ObjectMapper()));
        authSessionService = mock(AuthSessionService.class);
        ReflectionTestUtils.setField(adminInterceptor, "authSessionService", authSessionService);

        userInterceptor = new JwtTokenUserInterceptor();
        ReflectionTestUtils.setField(userInterceptor, "jwtProperties", jwtProperties);
        userMapper = mock(UserMapper.class);
        ReflectionTestUtils.setField(userInterceptor, "userMapper", userMapper);
        ReflectionTestUtils.setField(
                userInterceptor,
                "apiErrorResponseWriter",
                new ApiErrorResponseWriter(new ObjectMapper()));
        ReflectionTestUtils.setField(userInterceptor, "authSessionService", authSessionService);

        Method method = DummyHandler.class.getMethod("handle");
        handlerMethod = new HandlerMethod(new DummyHandler(), method);
    }

    @AfterEach
    void tearDown() {
        BaseContext.removeCurrentId();
    }

    @Test
    void adminInterceptorAcceptsAdminTokenAndClearsContextAfterCompletion() throws Exception {
        when(employeeMapper.getById(9L)).thenReturn(Employee.builder()
                .id(9L)
                .status(StatusConstant.ENABLE)
                .build());
        when(authSessionService.isActive("session-1", "EMPLOYEE", 9L)).thenReturn(true);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Admin-Token", token("test-admin-secret", JwtClaimsConstant.EMP_ID, 9L, "EMPLOYEE"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = adminInterceptor.preHandle(request, response, handlerMethod);

        assertThat(allowed).isTrue();
        assertThat(BaseContext.getCurrentId()).isEqualTo(9L);

        adminInterceptor.afterCompletion(request, response, handlerMethod, null);

        assertThat(BaseContext.getCurrentId()).isNull();
    }

    @Test
    void adminInterceptorRejectsDisabledEmployeeToken() throws Exception {
        when(employeeMapper.getById(9L)).thenReturn(Employee.builder()
                .id(9L)
                .status(StatusConstant.DISABLE)
                .build());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Admin-Token", token("test-admin-secret", JwtClaimsConstant.EMP_ID, 9L, "EMPLOYEE"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = adminInterceptor.preHandle(request, response, handlerMethod);

        assertThat(allowed).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("\"code\":" + ErrorCode.AUTHENTICATION_FAILED.getCode());
        assertThat(BaseContext.getCurrentId()).isNull();
    }

    @Test
    void adminInterceptorRejectsDeletedEmployeeToken() throws Exception {
        when(employeeMapper.getById(9L)).thenReturn(null);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Admin-Token", token("test-admin-secret", JwtClaimsConstant.EMP_ID, 9L, "EMPLOYEE"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = adminInterceptor.preHandle(request, response, handlerMethod);

        assertThat(allowed).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("\"code\":" + ErrorCode.AUTHENTICATION_FAILED.getCode());
        assertThat(BaseContext.getCurrentId()).isNull();
    }

    @Test
    void userInterceptorAcceptsUserTokenAndClearsContextAfterCompletion() throws Exception {
        when(userMapper.getById(7L)).thenReturn(User.builder()
                .id(7L)
                .status(StatusConstant.ENABLE)
                .build());
        when(authSessionService.isActive("session-1", "USER", 7L)).thenReturn(true);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("User-Token", token("test-user-secret", JwtClaimsConstant.USER_ID, 7L, "USER"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = userInterceptor.preHandle(request, response, handlerMethod);

        assertThat(allowed).isTrue();
        assertThat(BaseContext.getCurrentId()).isEqualTo(7L);

        userInterceptor.afterCompletion(request, response, handlerMethod, null);

        assertThat(BaseContext.getCurrentId()).isNull();
    }

    @Test
    void userInterceptorRejectsDisabledUserToken() throws Exception {
        when(userMapper.getById(7L)).thenReturn(User.builder()
                .id(7L)
                .status(StatusConstant.DISABLE)
                .build());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("User-Token", token("test-user-secret", JwtClaimsConstant.USER_ID, 7L, "USER"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = userInterceptor.preHandle(request, response, handlerMethod);

        assertThat(allowed).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("\"code\":" + ErrorCode.AUTHENTICATION_FAILED.getCode());
        assertThat(BaseContext.getCurrentId()).isNull();
    }

    @Test
    void userInterceptorRejectsDeletedUserToken() throws Exception {
        when(userMapper.getById(7L)).thenReturn(null);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("User-Token", token("test-user-secret", JwtClaimsConstant.USER_ID, 7L, "USER"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = userInterceptor.preHandle(request, response, handlerMethod);

        assertThat(allowed).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("\"code\":" + ErrorCode.AUTHENTICATION_FAILED.getCode());
        assertThat(BaseContext.getCurrentId()).isNull();
    }

    @Test
    void adminInterceptorRejectsUserTokenAndClearsStaleContext() throws Exception {
        BaseContext.setCurrentId(99L);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Admin-Token", token("test-user-secret", JwtClaimsConstant.USER_ID, 7L, "USER"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = adminInterceptor.preHandle(request, response, handlerMethod);

        assertThat(allowed).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("\"code\":" + ErrorCode.AUTHENTICATION_FAILED.getCode());
        assertThat(BaseContext.getCurrentId()).isNull();
    }

    @Test
    void userInterceptorRejectsAdminTokenAndClearsStaleContext() throws Exception {
        BaseContext.setCurrentId(99L);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("User-Token", token("test-admin-secret", JwtClaimsConstant.EMP_ID, 9L, "EMPLOYEE"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = userInterceptor.preHandle(request, response, handlerMethod);

        assertThat(allowed).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("\"code\":" + ErrorCode.AUTHENTICATION_FAILED.getCode());
        assertThat(BaseContext.getCurrentId()).isNull();
    }

    @Test
    void mockMvcRejectsCrossEndAccessTokensWithStructuredRequestId() throws Exception {
        MockMvc adminMvc = MockMvcBuilders.standaloneSetup(new ProtectedController())
                .addInterceptors(adminInterceptor)
                .addFilters(new RequestTracingFilter())
                .build();
        adminMvc.perform(get("/admin/protected")
                        .header("X-Request-Id", "cross-admin-1")
                        .header("Admin-Token", token("test-user-secret", JwtClaimsConstant.USER_ID, 7L, "USER")))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-Request-Id", "cross-admin-1"))
                .andExpect(jsonPath("$.code").value(ErrorCode.AUTHENTICATION_FAILED.getCode()))
                .andExpect(jsonPath("$.requestId").value("cross-admin-1"));

        MockMvc userMvc = MockMvcBuilders.standaloneSetup(new ProtectedController())
                .addInterceptors(userInterceptor)
                .addFilters(new RequestTracingFilter())
                .build();
        userMvc.perform(get("/user/protected")
                        .header("X-Request-Id", "cross-user-1")
                        .header("User-Token", token("test-admin-secret", JwtClaimsConstant.EMP_ID, 9L, "EMPLOYEE")))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-Request-Id", "cross-user-1"))
                .andExpect(jsonPath("$.code").value(ErrorCode.AUTHENTICATION_FAILED.getCode()))
                .andExpect(jsonPath("$.requestId").value("cross-user-1"));
    }

    private static String token(String secretKey, String claimName, Long id, String principalType) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(claimName, id);
        claims.put(JwtClaimsConstant.SESSION_ID, "session-1");
        claims.put(JwtClaimsConstant.TOKEN_TYPE, "ACCESS");
        claims.put(JwtClaimsConstant.PRINCIPAL_TYPE, principalType);
        return JwtUtil.createJWT(secretKey, 3600000L, claims);
    }

    public static class DummyHandler {
        public void handle() {
        }
    }

    @RestController
    public static class ProtectedController {
        @GetMapping("/admin/protected")
        public String admin() {
            return "admin";
        }

        @GetMapping("/user/protected")
        public String user() {
            return "user";
        }
    }
}
