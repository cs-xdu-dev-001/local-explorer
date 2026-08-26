package com.localexplorer.exception;

import com.localexplorer.constant.ErrorCode;
import com.localexplorer.constant.MessageConstant;

/**
 * 账号被锁定异常
 */
public class AccountLockedException extends BaseException {

    public AccountLockedException() {
        super(ErrorCode.AUTHENTICATION_FAILED, MessageConstant.ACCOUNT_LOCKED);
    }

    public AccountLockedException(String msg) {
        super(ErrorCode.AUTHENTICATION_FAILED, msg);
    }

}
