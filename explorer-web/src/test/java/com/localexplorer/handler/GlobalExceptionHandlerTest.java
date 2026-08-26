package com.localexplorer.handler;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.localexplorer.constant.MessageConstant;
import com.localexplorer.constant.ErrorCode;
import com.localexplorer.dto.ReviewPageQueryDTO;
import com.localexplorer.exception.BaseException;
import com.localexplorer.exception.ForbiddenOperationException;
import com.localexplorer.exception.PasswordErrorException;
import com.localexplorer.filter.RequestTracingFilter;
import com.localexplorer.result.Result;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.http.ResponseEntity;

import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.SQLSyntaxErrorException;

import javax.validation.ConstraintViolationException;
import javax.validation.Validation;
import javax.validation.Validator;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private Logger logger;
    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void captureLogs() {
        logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        logger.addAppender(logAppender);
    }

    @AfterEach
    void releaseLogs() {
        MDC.clear();
        logger.detachAppender(logAppender);
        logAppender.stop();
    }

    @Test
    void errorResponseCarriesRequestIdInHeaderAndBody() {
        MDC.put(RequestTracingFilter.MDC_KEY, "trace-error-01");

        ResponseEntity<Result<Void>> response = handler.exceptionHandler(new BaseException("预约失败"));

        assertThat(response.getHeaders().getFirst(RequestTracingFilter.REQUEST_ID_HEADER))
                .isEqualTo("trace-error-01");
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getRequestId()).isEqualTo("trace-error-01");
    }

    @Test
    void baseExceptionReturnsBusinessMessage() {
        ResponseEntity<Result<Void>> response = handler.exceptionHandler(new BaseException("分类名称已存在"));
        Result<Void> result = response.getBody();

        assertThat(response.getStatusCodeValue()).isEqualTo(409);
        assertThat(result).isNotNull();
        assertThat(result.getCode()).isEqualTo(ErrorCode.BUSINESS_ERROR.getCode());
        assertThat(result.getMsg()).isEqualTo("分类名称已存在");
        assertThat(logAppender.list).hasSize(1);
        assertThat(logAppender.list.get(0).getLevel()).isEqualTo(Level.WARN);
        assertThat(logAppender.list.get(0).getThrowableProxy()).isNull();
    }

    @Test
    void duplicateKeyExceptionReturnsReadableMessage() {
        SQLIntegrityConstraintViolationException exception =
                new SQLIntegrityConstraintViolationException("Duplicate entry 'admin' for key 'idx_username'");

        ResponseEntity<Result<Void>> response = handler.exceptionHandler(exception);
        Result<Void> result = response.getBody();

        assertThat(response.getStatusCodeValue()).isEqualTo(409);
        assertThat(result).isNotNull();
        assertThat(result.getCode()).isEqualTo(ErrorCode.DUPLICATE_DATA.getCode());
        assertThat(result.getMsg()).isEqualTo("数据已存在");
        assertThat(result.getMsg()).doesNotContain("idx_username", "Duplicate entry");
    }

    @Test
    void parameterExceptionReturnsParamError() {
        ResponseEntity<Result<Void>> response = handler.parameterExceptionHandler(new IllegalArgumentException("bad argument"));
        Result<Void> result = response.getBody();

        assertThat(response.getStatusCodeValue()).isEqualTo(400);
        assertThat(result).isNotNull();
        assertThat(result.getCode()).isEqualTo(ErrorCode.INVALID_REQUEST.getCode());
        assertThat(result.getMsg()).isEqualTo(MessageConstant.PARAM_ERROR);
    }

    @Test
    void constraintViolationReturnsReadableMessage() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        ReviewPageQueryDTO dto = new ReviewPageQueryDTO();
        dto.setPage(0);

        ResponseEntity<Result<Void>> response = handler.constraintViolationExceptionHandler(
                new ConstraintViolationException(validator.validate(dto)));
        Result<Void> result = response.getBody();

        assertThat(response.getStatusCodeValue()).isEqualTo(400);
        assertThat(result).isNotNull();
        assertThat(result.getCode()).isEqualTo(ErrorCode.INVALID_REQUEST.getCode());
        assertThat(result.getMsg()).isEqualTo("页码不能小于1");
    }

    @Test
    void missingDatabaseTableReturnsSetupHint() {
        SQLSyntaxErrorException cause = new SQLSyntaxErrorException("Table 'agent_studio.employee' doesn't exist");
        BadSqlGrammarException exception = new BadSqlGrammarException(
                "select",
                "select * from employee where username = ?",
                cause
        );

        ResponseEntity<Result<Void>> response = handler.exceptionHandler(exception);
        Result<Void> result = response.getBody();

        assertThat(response.getStatusCodeValue()).isEqualTo(503);
        assertThat(result).isNotNull();
        assertThat(result.getCode()).isEqualTo(ErrorCode.DATABASE_UNAVAILABLE.getCode());
        assertThat(result.getMsg()).isEqualTo(MessageConstant.DATABASE_NOT_INITIALIZED);
    }

    @Test
    void authenticationAndAuthorizationUseDifferentHttpStatuses() {
        ResponseEntity<Result<Void>> authentication = handler.exceptionHandler(
                new PasswordErrorException(MessageConstant.PASSWORD_ERROR));
        ResponseEntity<Result<Void>> authorization = handler.exceptionHandler(
                new ForbiddenOperationException("当前员工没有权限执行该操作"));

        assertThat(authentication.getStatusCodeValue()).isEqualTo(401);
        assertThat(authentication.getBody()).isNotNull();
        assertThat(authentication.getBody().getCode()).isEqualTo(ErrorCode.AUTHENTICATION_FAILED.getCode());
        assertThat(authorization.getStatusCodeValue()).isEqualTo(403);
        assertThat(authorization.getBody()).isNotNull();
        assertThat(authorization.getBody().getCode()).isEqualTo(ErrorCode.FORBIDDEN.getCode());
    }

    @Test
    void unexpectedExceptionDoesNotLeakSqlOrStackDetails() {
        RuntimeException exception = new RuntimeException(
                "select password from employee where username='admin'; connection refused");

        ResponseEntity<Result<Void>> response = handler.exceptionHandler(exception);

        assertThat(response.getStatusCodeValue()).isEqualTo(500);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(ErrorCode.INTERNAL_ERROR.getCode());
        assertThat(response.getBody().getMsg()).isEqualTo(MessageConstant.UNKNOWN_ERROR);
        assertThat(response.getBody().getMsg()).doesNotContain("select", "password", "connection");
    }
}
