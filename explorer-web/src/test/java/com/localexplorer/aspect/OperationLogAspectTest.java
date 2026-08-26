package com.localexplorer.aspect;

import com.localexplorer.annotation.OperationLog;
import com.localexplorer.context.BaseContext;
import com.localexplorer.entity.OperationLogEntity;
import com.localexplorer.result.Result;
import com.localexplorer.properties.AuthSecurityProperties;
import com.localexplorer.service.AuthRequestSecurity;
import com.localexplorer.service.OperationLogService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OperationLogAspectTest {

    private OperationLogAspect aspect;

    @Mock
    private OperationLogService logService;

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private MethodSignature signature;

    @BeforeEach
    void setUp() {
        aspect = new OperationLogAspect();
        ReflectionTestUtils.setField(aspect, "logService", logService);
        AuthSecurityProperties properties = new AuthSecurityProperties();
        properties.setFingerprintSecret("operation-log-test-secret");
        ReflectionTestUtils.setField(aspect, "requestSecurity", new AuthRequestSecurity(properties));
        when(joinPoint.getSignature()).thenReturn(signature);
        BaseContext.setCurrentId(7L);
    }

    @AfterEach
    void tearDown() {
        BaseContext.removeCurrentId();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void successfulOperationIsPersisted() throws Throwable {
        Method method = LoggedActions.class.getMethod("success");
        Result<String> result = Result.success("ok");
        when(signature.getMethod()).thenReturn(method);
        when(joinPoint.proceed()).thenReturn(result);

        assertSame(result, aspect.around(joinPoint));

        ArgumentCaptor<OperationLogEntity> captor = ArgumentCaptor.forClass(OperationLogEntity.class);
        verify(logService).save(captor.capture());
        assertEquals("测试成功操作", captor.getValue().getDescription());
        assertEquals(7L, captor.getValue().getOperatorId());
    }

    @Test
    void persistedClientFingerprintNeverContainsRawIpOrUntrustedForwardedIp() throws Throwable {
        Method method = LoggedActions.class.getMethod("success");
        when(signature.getMethod()).thenReturn(method);
        when(joinPoint.proceed()).thenReturn(Result.success("ok"));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.0.2.44");
        request.addHeader("X-Forwarded-For", "203.0.113.10");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        aspect.around(joinPoint);

        ArgumentCaptor<OperationLogEntity> captor = ArgumentCaptor.forClass(OperationLogEntity.class);
        verify(logService).save(captor.capture());
        assertThat(captor.getValue().getClientIp())
                .matches("[0-9a-f]{16}")
                .doesNotContain("192.0.2.44", "203.0.113.10");
    }

    @Test
    void thrownBusinessFailureIsNotRecordedAsSuccessfulOperation() throws Throwable {
        Method method = LoggedActions.class.getMethod("success");
        RuntimeException failure = new RuntimeException("business failed");
        when(signature.getMethod()).thenReturn(method);
        when(joinPoint.proceed()).thenThrow(failure);

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> aspect.around(joinPoint));

        assertSame(failure, thrown);
        verify(logService, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void errorResultIsNotRecordedAsSuccessfulOperation() throws Throwable {
        Method method = LoggedActions.class.getMethod("success");
        Result<String> result = Result.error("upload failed");
        when(signature.getMethod()).thenReturn(method);
        when(joinPoint.proceed()).thenReturn(result);

        assertSame(result, aspect.around(joinPoint));
        verify(logService, never()).save(org.mockito.ArgumentMatchers.any());
    }

    static class LoggedActions {

        @OperationLog("测试成功操作")
        public Result<String> success() {
            return Result.success();
        }
    }
}
