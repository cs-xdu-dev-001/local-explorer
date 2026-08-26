package com.localexplorer.exception;

import com.localexplorer.constant.ErrorCode;

/**
 * 登录失败
 */
public class LoginFailedException extends BaseException{
    public LoginFailedException(String msg){
        super(ErrorCode.AUTHENTICATION_FAILED, msg);
    }
}
