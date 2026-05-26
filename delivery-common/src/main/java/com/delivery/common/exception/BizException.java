package com.delivery.common.exception;

import lombok.Getter;

/**
 * 业务异常：订单不存在、骑手满了、状态不允许等"可预期的失败"都抛它。
 * Service 层只抛异常不关心响应格式，由 GlobalExceptionHandler 统一转成 Result。
 * 继承 RuntimeException 而不是 Exception：
 * 1. 不用在方法签名写 throws，调用方不强制 try-catch；
 * 2. Spring @Transactional 默认只对 RuntimeException 回滚。
 */
@Getter
public class BizException extends RuntimeException {

    private final int code; // 携带错误码，异常处理器从这里取

    public BizException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
    }

    public BizException(ErrorCode errorCode, String detail) {
        super(errorCode.getMessage() + "：" + detail);
        this.code = errorCode.getCode();
    }

    // 静态工厂，写起来更短
    public static BizException of(ErrorCode errorCode) {
        return new BizException(errorCode);
    }

    public static BizException of(ErrorCode errorCode, String detail) {
        return new BizException(errorCode, detail);
    }
}