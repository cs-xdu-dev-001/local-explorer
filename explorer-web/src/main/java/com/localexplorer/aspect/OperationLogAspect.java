package com.localexplorer.aspect;

import com.localexplorer.annotation.OperationLog;
import com.localexplorer.context.BaseContext;
import com.localexplorer.entity.OperationLogEntity;
import com.localexplorer.result.Result;
import com.localexplorer.service.AuthRequestSecurity;
import com.localexplorer.service.OperationLogService;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;

/**
 * 操作日志切面
 *
 * <p>拦截所有标注了 {@link OperationLog} 注解的 Controller 方法，记录：</p>
 * <ul>
 *   <li>操作描述（注解的 value）</li>
 *   <li>操作人 ID（从 JWT 上下文中获取）</li>
 *   <li>请求方法和路径</li>
 *   <li>客户端 IP</li>
 *   <li>方法耗时（ms）</li>
 * </ul>
 *
 * <p>同时持久化到数据库 operation_log 表，可在后台查看。</p>
 */
@Aspect
@Component
@Slf4j
public class OperationLogAspect {

    @Autowired
    private OperationLogService logService;
    @Autowired
    private AuthRequestSecurity requestSecurity;

    @Pointcut("@annotation(com.localexplorer.annotation.OperationLog)")
    public void operationLogPointcut() {
    }

    @Around("operationLogPointcut()")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        OperationLog operationLog = signature.getMethod().getAnnotation(OperationLog.class);
        String description = operationLog.value();

        // 获取请求信息
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        String requestMethod = "";
        String requestUri = "";
        String clientIp = "";
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            requestMethod = request.getMethod();
            requestUri = request.getRequestURI();
            clientIp = requestSecurity.ipFingerprint(request);
        }

        Long operatorId = BaseContext.getCurrentId();

        Object result = joinPoint.proceed();
        if (result instanceof Result && !Integer.valueOf(1).equals(((Result<?>) result).getCode())) {
            return result;
        }

        long costTime = System.currentTimeMillis() - startTime;
        log.info("[操作日志] 描述={} | 操作人={} | {} {} | IP指纹={} | 耗时={}ms",
                description, operatorId, requestMethod, requestUri, clientIp, costTime);

        try {
            OperationLogEntity entity = OperationLogEntity.builder()
                    .description(description)
                    .operatorId(operatorId)
                    .requestMethod(requestMethod)
                    .requestUri(requestUri)
                    .clientIp(clientIp)
                    .costTime(costTime)
                    .createTime(LocalDateTime.now())
                    .build();
            logService.save(entity);
        } catch (Exception e) {
            log.warn("保存操作日志到数据库失败：{}", e.getMessage());
        }

        return result;
    }

}
