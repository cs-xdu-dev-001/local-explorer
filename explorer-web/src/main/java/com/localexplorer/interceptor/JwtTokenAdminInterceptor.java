package com.localexplorer.interceptor;

import com.localexplorer.constant.JwtClaimsConstant;
import com.localexplorer.constant.ErrorCode;
import com.localexplorer.constant.StatusConstant;
import com.localexplorer.context.BaseContext;
import com.localexplorer.entity.Employee;
import com.localexplorer.handler.ApiErrorResponseWriter;
import com.localexplorer.mapper.EmployeeMapper;
import com.localexplorer.properties.JwtProperties;
import com.localexplorer.utils.JwtUtil;
import com.localexplorer.service.AuthSessionService;
import com.localexplorer.service.impl.AuthSessionServiceImpl;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * JWT 令牌校验拦截器 -- 管理端
 */
@Component
@Slf4j
public class JwtTokenAdminInterceptor implements HandlerInterceptor {

    @Autowired
    private JwtProperties jwtProperties;
    @Autowired
    private EmployeeMapper employeeMapper;
    @Autowired
    private ApiErrorResponseWriter apiErrorResponseWriter;
    @Autowired
    private AuthSessionService authSessionService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }

        String token = request.getHeader(jwtProperties.getAdminTokenName());

        try {
            Claims claims = JwtUtil.parseJWT(jwtProperties.getAdminSecretKey(), token);
            requireClaim(claims, JwtClaimsConstant.TOKEN_TYPE, "ACCESS");
            requireClaim(claims, JwtClaimsConstant.PRINCIPAL_TYPE, AuthSessionServiceImpl.EMPLOYEE);
            Long empId = Long.valueOf(claims.get(JwtClaimsConstant.EMP_ID).toString());
            String sessionId = claims.get(JwtClaimsConstant.SESSION_ID, String.class);
            if (sessionId == null || !authSessionService.isActive(sessionId, AuthSessionServiceImpl.EMPLOYEE, empId)) {
                throw new IllegalStateException("员工会话已失效");
            }
            Employee employee = employeeMapper.getById(empId);
            if (employee == null || !StatusConstant.ENABLE.equals(employee.getStatus())) {
                throw new IllegalStateException("员工账号已禁用或不存在");
            }
            BaseContext.setCurrentId(empId);
            request.setAttribute(JwtClaimsConstant.SESSION_ID, sessionId);
            log.debug("JWT校验通过，当前员工id：{}", empId);
            return true;
        } catch (Exception ex) {
            log.warn("JWT校验失败：{}", ex.getMessage());
            BaseContext.removeCurrentId();
            apiErrorResponseWriter.write(
                    response,
                    ErrorCode.AUTHENTICATION_FAILED,
                    "登录状态无效或员工账号已禁用");
            return false;
        }
    }

    private void requireClaim(Claims claims, String name, String expected) {
        if (!expected.equals(claims.get(name, String.class))) {
            throw new IllegalStateException("令牌类型不匹配");
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        BaseContext.removeCurrentId();
    }
}
