package com.localexplorer.service.impl;

import com.localexplorer.LocalExplorerApplication;
import com.localexplorer.entity.OperationLogEntity;
import com.localexplorer.mapper.OperationLogMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OperationLogServiceImplTest {

    private OperationLogServiceImpl operationLogService;

    @Mock
    private OperationLogMapper logMapper;

    @BeforeEach
    void setUp() {
        operationLogService = new OperationLogServiceImpl();
        ReflectionTestUtils.setField(operationLogService, "logMapper", logMapper);
    }

    @Test
    void applicationEnablesAsyncExecution() {
        assertThat(LocalExplorerApplication.class.getAnnotation(EnableAsync.class)).isNotNull();
    }

    @Test
    void saveUsesOperationLogExecutor() throws Exception {
        Method saveMethod = OperationLogServiceImpl.class.getMethod("save", OperationLogEntity.class);

        Async async = saveMethod.getAnnotation(Async.class);

        assertThat(async).isNotNull();
        assertThat(async.value()).isEqualTo("operationLogExecutor");
    }

    @Test
    void operationLogExecutorBeanIsConfigured() throws Exception {
        Class<?> configClass = Class.forName("com.localexplorer.config.AsyncConfiguration");
        Method executorMethod = configClass.getMethod("operationLogExecutor");

        assertThat(executorMethod.getAnnotation(Bean.class)).isNotNull();
        assertThat(Executor.class.isAssignableFrom(executorMethod.getReturnType())).isTrue();
    }

    @Test
    void savePersistsOperationLog() {
        OperationLogEntity log = OperationLogEntity.builder()
                .description("新增特色项目")
                .operatorId(1L)
                .build();

        operationLogService.save(log);

        verify(logMapper).insert(log);
    }

    @Test
    void saveSwallowsMapperFailure() {
        OperationLogEntity log = OperationLogEntity.builder()
                .description("新增特色项目")
                .operatorId(1L)
                .build();
        doThrow(new RuntimeException("database down")).when(logMapper).insert(log);

        assertThatCode(() -> operationLogService.save(log))
                .doesNotThrowAnyException();
    }
}
