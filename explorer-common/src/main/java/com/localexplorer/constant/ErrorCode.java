package com.localexplorer.constant;

/**
 * Stable API error codes. Success keeps the historical code 1 for frontend compatibility.
 */
public enum ErrorCode {

    INVALID_REQUEST(40000, 400, MessageConstant.PARAM_ERROR),
    AUTHENTICATION_FAILED(40100, 401, "登录状态无效"),
    FORBIDDEN(40300, 403, "当前账号没有权限执行该操作"),
    NOT_FOUND(40400, 404, "请求资源不存在"),
    METHOD_NOT_ALLOWED(40500, 405, "请求方法不支持"),
    BUSINESS_ERROR(40900, 409, "业务状态冲突"),
    DUPLICATE_DATA(40901, 409, "数据已存在"),
    EXPORT_JOB_CONFLICT(40910, 409, "导出任务状态冲突"),
    EXPORT_JOB_NOT_READY(40911, 409, "导出文件尚不可下载"),
    EXPORT_FILE_GONE(41010, 410, "导出文件已过期或不存在"),
    TOO_MANY_REQUESTS(42900, 429, "登录尝试过于频繁，请稍后再试"),
    EXPORT_JOB_LIMIT(42910, 429, "导出任务超过资源限制"),
    INTERNAL_ERROR(50000, 500, MessageConstant.UNKNOWN_ERROR),
    DATABASE_UNAVAILABLE(50300, 503, MessageConstant.DATABASE_NOT_INITIALIZED);

    private final int code;
    private final int httpStatus;
    private final String defaultMessage;

    ErrorCode(int code, int httpStatus, String defaultMessage) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    public int getCode() {
        return code;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}
