package com.localexplorer.exception;

import com.localexplorer.constant.ErrorCode;
import com.localexplorer.constant.MessageConstant;

/**
 * 账号不存在异常
 */
public class AccountNotFoundException extends BaseException {

    public AccountNotFoundException() {
        super(ErrorCode.AUTHENTICATION_FAILED, MessageConstant.ACCOUNT_NOT_FOUND);
    }

    public AccountNotFoundException(String msg) {
        super(ErrorCode.AUTHENTICATION_FAILED, msg);
    }

}
