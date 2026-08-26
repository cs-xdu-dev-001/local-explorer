package com.localexplorer.exception;

import com.localexplorer.constant.ErrorCode;

/**
 * 业务异常
 */
public class BaseException extends RuntimeException {

    private final ErrorCode errorCode;

    public BaseException() {
        this(ErrorCode.BUSINESS_ERROR, ErrorCode.BUSINESS_ERROR.getDefaultMessage());
    }

    public BaseException(String msg) {
        this(ErrorCode.BUSINESS_ERROR, msg);
    }

    public BaseException(ErrorCode errorCode, String msg) {
        super(msg);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

}
