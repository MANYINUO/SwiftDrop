package com.delivery.common.exception;

import com.delivery.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.data.redis.RedisConnectionFailureException;

import jakarta.validation.ConstraintViolationException;
import java.util.stream.Collectors;

/**
 * 全局异常处理。
 *
 * <p>保证任何异常都以统一的 {@link Result} 结构返回，前端不用处理 500 页面。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 业务异常：可预期，记 warn 即可，不打堆栈 */
    @ExceptionHandler(BizException.class)
    public Result<Void> handleBiz(BizException e) {
        log.warn("业务异常 code={} msg={}", e.getCode(), e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    /** @RequestBody 参数校验失败 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValid(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + " " + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败：{}", detail);
        return Result.fail(ErrorCode.PARAM_INVALID.getCode(),
                ErrorCode.PARAM_INVALID.getMessage() + "：" + detail);
    }

    /** 表单 / 路径参数校验失败 */
    @ExceptionHandler(BindException.class)
    public Result<Void> handleBind(BindException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        return Result.fail(ErrorCode.PARAM_INVALID.getCode(),
                ErrorCode.PARAM_INVALID.getMessage() + "：" + detail);
    }

    /** 单参数校验失败 */
    @ExceptionHandler(ConstraintViolationException.class)
    public Result<Void> handleConstraint(ConstraintViolationException e) {
        String detail = e.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + " " + v.getMessage())
                .collect(Collectors.joining("; "));
        return Result.fail(ErrorCode.PARAM_INVALID.getCode(),
                ErrorCode.PARAM_INVALID.getMessage() + "：" + detail);
    }

    /** 缺少必填请求参数 */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Result<Void> handleMissingParam(MissingServletRequestParameterException e) {
        return Result.fail(ErrorCode.PARAM_INVALID.getCode(),
                "缺少必填参数：" + e.getParameterName());
    }

    /** Redis 连不上（登录令牌、GEO 等依赖它）：给明确的 503 提示，避免白屏 500 */
    @ExceptionHandler(RedisConnectionFailureException.class)
    public Result<Void> handleRedisUnavailable(RedisConnectionFailureException e) {
        log.error("Redis 连接失败", e);
        return Result.fail(ErrorCode.SERVICE_UNAVAILABLE);
    }

    /** 兜底：未预期的异常，打完整堆栈，对外只给通用提示 */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleOther(Exception e) {
        log.error("系统异常", e);
        return Result.fail(ErrorCode.SYSTEM_ERROR);
    }
}
