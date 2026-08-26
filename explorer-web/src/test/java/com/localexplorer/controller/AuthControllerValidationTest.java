package com.localexplorer.controller;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.localexplorer.controller.admin.EmployeeController;
import com.localexplorer.controller.user.UserController;
import com.localexplorer.constant.ErrorCode;
import com.localexplorer.exception.BaseException;
import com.localexplorer.handler.GlobalExceptionHandler;
import com.localexplorer.properties.AuthSecurityProperties;
import com.localexplorer.service.AuthenticationResult;
import com.localexplorer.service.AuthenticationService;
import com.localexplorer.service.AuthRequestSecurity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuthControllerValidationTest {

    private MockMvc mockMvc;

    @Mock
    private AuthenticationService authenticationService;

    @BeforeEach
    void setUp() {
        AuthSecurityProperties properties = new AuthSecurityProperties();
        AuthRequestSecurity requestSecurity = new AuthRequestSecurity(properties);

        EmployeeController employeeController = new EmployeeController();
        ReflectionTestUtils.setField(employeeController, "authenticationService", authenticationService);
        ReflectionTestUtils.setField(employeeController, "authRequestSecurity", requestSecurity);

        UserController userController = new UserController();
        ReflectionTestUtils.setField(userController, "authenticationService", authenticationService);
        ReflectionTestUtils.setField(userController, "authRequestSecurity", requestSecurity);

        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(employeeController, userController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void adminLoginRejectsMissingUsername() throws Exception {
        mockMvc.perform(post("/admin/employee/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"123456\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000))
                .andExpect(jsonPath("$.msg").value("员工账号不能为空"));

        verifyNoInteractions(authenticationService);
    }

    @Test
    void adminLoginRejectsBlankPassword() throws Exception {
        mockMvc.perform(post("/admin/employee/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000))
                .andExpect(jsonPath("$.msg").value("员工密码不能为空"));

        verifyNoInteractions(authenticationService);
    }

    @Test
    void userLoginRejectsMissingPhone() throws Exception {
        mockMvc.perform(post("/user/user/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"123456\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000))
                .andExpect(jsonPath("$.msg").value("手机号不能为空"));

        verifyNoInteractions(authenticationService);
    }

    @Test
    void userLoginRejectsBlankPassword() throws Exception {
        mockMvc.perform(post("/user/user/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"13800001111\",\"password\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000))
                .andExpect(jsonPath("$.msg").value("用户密码不能为空"));

        verifyNoInteractions(authenticationService);
    }

    @Test
    void adminLoginReturnsTokenWhenCredentialsAreValid() throws Exception {
        when(authenticationService.loginEmployee(any(), any())).thenReturn(AuthenticationResult.builder()
                .id(1L).userName("admin")
                .name("管理员")
                .role("ADMIN")
                .accessToken("access-token").refreshToken("refresh-token")
                .accessExpiresInMillis(1800000L)
                .build());

        mockMvc.perform(post("/admin/employee/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.userName").value("admin"))
                .andExpect(jsonPath("$.data.role").value("ADMIN"))
                .andExpect(jsonPath("$.data.token").value("access-token"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Set-Cookie", org.hamcrest.Matchers.allOf(
                                org.hamcrest.Matchers.containsString("LX_ADMIN_REFRESH=refresh-token"),
                                org.hamcrest.Matchers.containsString("HttpOnly"),
                                org.hamcrest.Matchers.containsString("SameSite=Lax"),
                                org.hamcrest.Matchers.containsString("Path=/admin"))));
    }

    @Test
    void userLoginReturnsTokenWhenCredentialsAreValid() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(UserController.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        when(authenticationService.loginUser(any(), any())).thenReturn(AuthenticationResult.builder()
                .id(1L)
                .name("张小明")
                .phone("13800001111")
                .avatar("avatar.png")
                .accessToken("user-access-token").refreshToken("user-refresh-token")
                .accessExpiresInMillis(1800000L)
                .build());

        try {
            mockMvc.perform(post("/user/user/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"phone\":\"13800001111\",\"password\":\"123456\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(1))
                    .andExpect(jsonPath("$.data.id").value(1))
                    .andExpect(jsonPath("$.data.phone").value("13800001111"))
                    .andExpect(jsonPath("$.data.token").value("user-access-token"))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                            .string("Set-Cookie", org.hamcrest.Matchers.allOf(
                                    org.hamcrest.Matchers.containsString("LX_USER_REFRESH=user-refresh-token"),
                                    org.hamcrest.Matchers.containsString("HttpOnly"),
                                    org.hamcrest.Matchers.containsString("Path=/user"))));
        } finally {
            logger.detachAppender(appender);
        }

        assertThat(appender.list)
                .extracting(ILoggingEvent::getFormattedMessage)
                .allSatisfy(message -> assertThat(message)
                        .doesNotContain("13800001111", "123456"));
    }

    @Test
    void adminLoginFailureUsesStructured401Response() throws Exception {
        when(authenticationService.loginEmployee(any(), any()))
                .thenThrow(new BaseException(ErrorCode.AUTHENTICATION_FAILED, "账号或密码错误"));

        mockMvc.perform(post("/admin/employee/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100))
                .andExpect(jsonPath("$.msg").value("账号或密码错误"));
    }
}
