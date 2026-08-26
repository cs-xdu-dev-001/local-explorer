package com.localexplorer.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.localexplorer.annotation.RequireAdmin;
import com.localexplorer.constant.ErrorCode;
import com.localexplorer.context.BaseContext;
import com.localexplorer.entity.Employee;
import com.localexplorer.handler.ApiErrorResponseWriter;
import com.localexplorer.mapper.EmployeeMapper;
import com.localexplorer.service.AdminPermissionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.core.annotation.AnnotatedElementUtils;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminAuthorizationInterceptorTest {

    private EmployeeMapper employeeMapper;
    private AdminAuthorizationInterceptor interceptor;

    @BeforeEach
    void setUp() {
        employeeMapper = mock(EmployeeMapper.class);
        AdminPermissionService permissionService = new AdminPermissionService(employeeMapper);
        interceptor = new AdminAuthorizationInterceptor(
                permissionService,
                new ApiErrorResponseWriter(new ObjectMapper()));
    }

    @AfterEach
    void tearDown() {
        BaseContext.removeCurrentId();
    }

    @Test
    void annotatedEndpointAllowsAdmin() throws Exception {
        BaseContext.setCurrentId(1L);
        when(employeeMapper.getById(1L)).thenReturn(Employee.builder().id(1L).role("ADMIN").build());

        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean allowed = interceptor.preHandle(
                new MockHttpServletRequest(), response, handlerMethod(ProtectedHandler.class));

        assertThat(allowed).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void annotatedEndpointRejectsStaffWithStructured403() throws Exception {
        BaseContext.setCurrentId(2L);
        when(employeeMapper.getById(2L)).thenReturn(Employee.builder().id(2L).role("STAFF").build());

        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean allowed = interceptor.preHandle(
                new MockHttpServletRequest(), response, handlerMethod(ProtectedHandler.class));

        assertThat(allowed).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains(
                "\"code\":" + ErrorCode.FORBIDDEN.getCode(),
                "当前员工没有权限执行该操作");
    }

    @Test
    void unannotatedEndpointRemainsAvailableToStaff() throws Exception {
        BaseContext.setCurrentId(2L);

        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean allowed = interceptor.preHandle(
                new MockHttpServletRequest(), response, handlerMethod(PublicHandler.class));

        assertThat(allowed).isTrue();
    }

    @Test
    void sensitiveAdminControllersDeclareTheAdminBoundary() {
        assertThat(AnnotatedElementUtils.hasAnnotation(
                com.localexplorer.controller.admin.UserManageController.class, RequireAdmin.class)).isTrue();
        assertThat(AnnotatedElementUtils.hasAnnotation(
                com.localexplorer.controller.admin.OperationLogController.class, RequireAdmin.class)).isTrue();
        assertThat(AnnotatedElementUtils.hasAnnotation(
                com.localexplorer.controller.admin.OutboxEventController.class, RequireAdmin.class)).isTrue();
        assertThat(AnnotatedElementUtils.hasAnnotation(
                com.localexplorer.controller.admin.AuthSecurityController.class, RequireAdmin.class)).isTrue();
        assertThat(AnnotatedElementUtils.hasAnnotation(
                com.localexplorer.controller.admin.CacheOpsController.class, RequireAdmin.class)).isTrue();

        boolean employeeMethodsProtected = Arrays.stream(
                        com.localexplorer.controller.admin.EmployeeController.class.getDeclaredMethods())
                .filter(method -> AnnotatedElementUtils.hasAnnotation(
                        method, org.springframework.web.bind.annotation.RequestMapping.class))
                .filter(method -> !method.getName().equals("login"))
                .filter(method -> !method.getName().equals("logout"))
                .filter(method -> !method.getName().equals("refresh"))
                .filter(method -> !method.getName().equals("logoutAll"))
                .filter(method -> !method.getName().equals("toLoginVO"))
                .allMatch(method -> method.isAnnotationPresent(RequireAdmin.class));
        assertThat(employeeMethodsProtected).isTrue();
    }

    private HandlerMethod handlerMethod(Class<?> handlerType) throws Exception {
        Object handler = handlerType.getDeclaredConstructor().newInstance();
        Method method = handlerType.getMethod("handle");
        return new HandlerMethod(handler, method);
    }

    @RequireAdmin
    public static class ProtectedHandler {
        public void handle() {
        }
    }

    public static class PublicHandler {
        public void handle() {
        }
    }
}
