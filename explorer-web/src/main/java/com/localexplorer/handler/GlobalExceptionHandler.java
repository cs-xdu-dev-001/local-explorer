package com.localexplorer.handler;

import com.localexplorer.constant.ErrorCode;
import com.localexplorer.constant.MessageConstant;
import com.localexplorer.exception.BaseException;
import com.localexplorer.filter.RequestTracingFilter;
import com.localexplorer.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.slf4j.MDC;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;

import javax.validation.ConstraintViolationException;
import java.sql.SQLIntegrityConstraintViolationException;

/**
 * Maps internal exceptions to stable API codes without exposing implementation details.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(BaseException.class)
    public ResponseEntity<Result<Void>> exceptionHandler(BaseException ex) {
        ErrorCode errorCode = ex.getErrorCode();
        log.warn("业务异常，code={}：{}", errorCode.getCode(), ex.getMessage());
        return response(errorCode, safeMessage(ex.getMessage(), errorCode));
    }

    @ExceptionHandler(SQLIntegrityConstraintViolationException.class)
    public ResponseEntity<Result<Void>> exceptionHandler(SQLIntegrityConstraintViolationException ex) {
        log.warn("SQL约束异常：{}", ex.getMessage());
        return response(ErrorCode.DUPLICATE_DATA);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Result<Void>> dataIntegrityExceptionHandler(DataIntegrityViolationException ex) {
        log.warn("数据约束异常：{}", ex.getMessage());
        return response(ErrorCode.DUPLICATE_DATA);
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public ResponseEntity<Result<Void>> validationExceptionHandler(Exception ex) {
        log.warn("参数校验异常：{}", ex.getMessage());
        String message = MessageConstant.PARAM_ERROR;
        if (ex instanceof MethodArgumentNotValidException) {
            MethodArgumentNotValidException validException = (MethodArgumentNotValidException) ex;
            if (validException.getBindingResult().hasErrors()) {
                message = validException.getBindingResult().getAllErrors().get(0).getDefaultMessage();
            }
        } else if (ex instanceof BindException) {
            BindException bindException = (BindException) ex;
            if (bindException.getBindingResult().hasErrors()) {
                message = bindException.getBindingResult().getAllErrors().get(0).getDefaultMessage();
            }
        }
        return response(ErrorCode.INVALID_REQUEST, message);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result<Void>> constraintViolationExceptionHandler(ConstraintViolationException ex) {
        log.warn("参数校验异常：{}", ex.getMessage());
        String message = ex.getConstraintViolations().stream()
                .findFirst()
                .map(violation -> violation.getMessage())
                .orElse(MessageConstant.PARAM_ERROR);
        return response(ErrorCode.INVALID_REQUEST, message);
    }

    @ExceptionHandler({
            MissingServletRequestParameterException.class,
            HttpMessageNotReadableException.class,
            IllegalArgumentException.class
    })
    public ResponseEntity<Result<Void>> parameterExceptionHandler(Exception ex) {
        log.warn("请求参数异常：{}", ex.getMessage());
        return response(ErrorCode.INVALID_REQUEST);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Result<Void>> methodNotSupportedHandler(HttpRequestMethodNotSupportedException ex) {
        log.warn("请求方法不支持：{}", ex.getMessage());
        return response(ErrorCode.METHOD_NOT_ALLOWED);
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<Result<Void>> notFoundHandler(NoHandlerFoundException ex) {
        log.warn("请求资源不存在：{}", ex.getRequestURL());
        return response(ErrorCode.NOT_FOUND);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> exceptionHandler(Exception ex) {
        log.error("系统异常：{}", ex.getMessage(), ex);
        if (isMissingDatabaseTable(ex)) {
            return response(ErrorCode.DATABASE_UNAVAILABLE);
        }
        return response(ErrorCode.INTERNAL_ERROR);
    }

    private ResponseEntity<Result<Void>> response(ErrorCode errorCode) {
        return response(errorCode, errorCode.getDefaultMessage());
    }

    private ResponseEntity<Result<Void>> response(ErrorCode errorCode, String message) {
        Result<Void> result = Result.error(errorCode, message);
        String requestId = MDC.get(RequestTracingFilter.MDC_KEY);
        result.setRequestId(requestId);
        ResponseEntity.BodyBuilder response = ResponseEntity.status(errorCode.getHttpStatus());
        if (requestId != null) {
            response.header(RequestTracingFilter.REQUEST_ID_HEADER, requestId);
        }
        return response.body(result);
    }

    private String safeMessage(String message, ErrorCode errorCode) {
        return message == null || message.trim().isEmpty()
                ? errorCode.getDefaultMessage()
                : message;
    }

    private boolean isMissingDatabaseTable(Throwable ex) {
        while (ex != null) {
            String message = ex.getMessage();
            if (message != null && message.contains("Table '") && message.contains("doesn't exist")) {
                return true;
            }
            ex = ex.getCause();
        }
        return false;
    }
}
