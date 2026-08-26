package com.localexplorer.interceptor;

import com.localexplorer.annotation.RequireAdmin;
import com.localexplorer.exception.ForbiddenOperationException;
import com.localexplorer.handler.ApiErrorResponseWriter;
import com.localexplorer.service.AdminPermissionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Component
public class AdminAuthorizationInterceptor implements HandlerInterceptor {

    private final AdminPermissionService permissionService;
    private final ApiErrorResponseWriter errorResponseWriter;

    @Autowired
    public AdminAuthorizationInterceptor(
            AdminPermissionService permissionService,
            ApiErrorResponseWriter errorResponseWriter) {
        this.permissionService = permissionService;
        this.errorResponseWriter = errorResponseWriter;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }
        HandlerMethod handlerMethod = (HandlerMethod) handler;
        boolean requireAdmin = AnnotatedElementUtils.hasAnnotation(handlerMethod.getMethod(), RequireAdmin.class)
                || AnnotatedElementUtils.hasAnnotation(handlerMethod.getBeanType(), RequireAdmin.class);
        if (!requireAdmin) {
            return true;
        }
        try {
            permissionService.requireAdmin();
            return true;
        } catch (ForbiddenOperationException ex) {
            errorResponseWriter.write(response, ex.getErrorCode(), ex.getMessage());
            return false;
        }
    }
}
