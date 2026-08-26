package com.localexplorer.exception;

import com.localexplorer.constant.ErrorCode;

public class RefreshReplayException extends BaseException {
    public RefreshReplayException() {
        super(ErrorCode.AUTHENTICATION_FAILED, "刷新凭证无效或已失效");
    }
}
