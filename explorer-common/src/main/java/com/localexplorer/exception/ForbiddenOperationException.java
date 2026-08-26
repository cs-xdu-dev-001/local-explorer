package com.localexplorer.exception;

import com.localexplorer.constant.ErrorCode;

public class ForbiddenOperationException extends BaseException {

    public ForbiddenOperationException(String message) {
        super(ErrorCode.FORBIDDEN, message);
    }
}
