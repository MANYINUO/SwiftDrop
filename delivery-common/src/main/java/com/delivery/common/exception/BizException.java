package com.delivery.common.exception;

import lombok.Getter;

/**
 * 业务异常。
 *
 * <p>所有可预期的业务失败都抛这个异常，由 {@link GlobalExceptionHandler} 统一转成响应体。
 * 不要用它包装系统级异常（那些走 {@code Exception} 兜底）。
 */
@Getter
public class BizException extends RuntimeException {

    private final int code;

    public BizException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
    }

    /** 需要补充上下文时使用，例如「订单 xxx 当前状态不允许取消」 */
    public BizException(ErrorCode errorCode, String detail) {
        super(errorCode.getMessage() + "：" + detail);
        this.code = errorCode.getCode();
    }

    public static BizException of(ErrorCode errorCode) {
        return new BizException(errorCode);
    }

    public static BizException of(ErrorCode errorCode, String detail) {
        return new BizException(errorCode, detail);
    }
}
