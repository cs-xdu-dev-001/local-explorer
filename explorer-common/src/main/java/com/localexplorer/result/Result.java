package com.localexplorer.result;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.localexplorer.constant.ErrorCode;
import lombok.Data;

import java.io.Serializable;

/**
 * 后端统一返回结果
 * @param <T>
 */
@Data
public class Result<T> implements Serializable {

    private Integer code; // 编码：1成功，其它数字为稳定业务错误码
    private String msg; //错误信息
    private T data; //数据
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String requestId; //异常链路追踪ID

    public static <T> Result<T> success() {
        Result<T> result = new Result<T>();
        result.code = 1;
        return result;
    }

    public static <T> Result<T> success(T object) {
        Result<T> result = new Result<T>();
        result.data = object;
        result.code = 1;
        return result;
    }

    public static <T> Result<T> error(String msg) {
        return error(ErrorCode.BUSINESS_ERROR, msg);
    }

    public static <T> Result<T> error(ErrorCode errorCode) {
        return error(errorCode, errorCode.getDefaultMessage());
    }

    public static <T> Result<T> error(ErrorCode errorCode, String msg) {
        Result<T> result = new Result<>();
        result.msg = msg;
        result.code = errorCode.getCode();
        return result;
    }

}
