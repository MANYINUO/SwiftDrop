package com.delivery.common.result;

import com.delivery.common.exception.ErrorCode;
import lombok.Data;

import java.io.Serializable;

/**
 * 统一响应体。
 *
 * <p>约定：{@code code == 0} 表示成功，非 0 表示业务失败。
 * 所有接口（包括异常）都返回这个结构，前端只需判断 code。
 */
@Data
public class Result<T> implements Serializable {

    private int code;
    private String message;
    private T data;
    private String traceId;
    private long timestamp;

    private Result() {
        this.timestamp = System.currentTimeMillis();
    }

    public static <T> Result<T> ok() {
        return ok(null);
    }

    public static <T> Result<T> ok(T data) {
        Result<T> r = new Result<>();
        r.code = ErrorCode.SUCCESS.getCode();
        r.message = ErrorCode.SUCCESS.getMessage();
        r.data = data;
        return r;
    }

    public static <T> Result<T> fail(ErrorCode errorCode) {
        return fail(errorCode.getCode(), errorCode.getMessage());
    }

    public static <T> Result<T> fail(int code, String message) {
        Result<T> r = new Result<>();
        r.code = code;
        r.message = message;
        return r;
    }

    public boolean isSuccess() {
        return this.code == ErrorCode.SUCCESS.getCode();
    }
}
