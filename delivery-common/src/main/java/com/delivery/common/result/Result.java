package com.delivery.common.result;

import com.delivery.common.exception.ErrorCode;
import lombok.Data;

import java.io.Serializable;

/** 统一响应体：所有接口返回 {code, message, data} */
@Data
public class Result<T> implements Serializable {

    private int code;       // 0=成功，非0=失败
    private String message; // 提示信息
    private T data;         // 业务数据
    private String traceId; // 链路ID（预留）
    private long timestamp; // 响应时间戳

    private Result() {
        this.timestamp = System.currentTimeMillis();
    }

    public static <T> Result<T> ok() { return ok(null); }

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