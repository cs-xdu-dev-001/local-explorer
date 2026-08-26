package com.localexplorer.exception;

import com.localexplorer.constant.ErrorCode;
import com.localexplorer.constant.MessageConstant;

/**
 * 密码错误异常
 */
public class PasswordErrorException extends BaseException {

    public PasswordErrorException() {
        super(ErrorCode.AUTHENTICATION_FAILED, MessageConstant.PASSWORD_ERROR);
    }

    public PasswordErrorException(String msg) {
        super(ErrorCode.AUTHENTICATION_FAILED, msg);
    }

}
