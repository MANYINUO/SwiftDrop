package com.delivery.common.exception;

import com.delivery.common.result.Result;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * 全局异常拦截器。
 * @RestControllerAdvice 对所有 Controller 生效，Controller 抛出的异常会先到这里，
 * 匹配到对应的 @ExceptionHandler 就转成 Result 返回，前端永远看不到 500 错误页。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 业务异常：只打 warn（预期内），返回 code+message
    @ExceptionHandler(BizException.class)
    public Result<Void> handleBiz(BizException e) {
        log.warn("业务异常 code={} msg={}", e.getCode(), e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    // @Valid 校验失败：把所有字段错误拼起来返回
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValid(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + " " + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return Result.fail(ErrorCode.PARAM_INVALID.getCode(),
                ErrorCode.PARAM_INVALID.getMessage() + "：" + detail);
    }

    // 表单参数绑定失败（非 JSON 请求体）
    @ExceptionHandler(BindException.class)
    public Result<Void> handleBind(BindException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        return Result.fail(ErrorCode.PARAM_INVALID.getCode(),
                ErrorCode.PARAM_INVALID.getMessage() + "：" + detail);
    }

    // 方法参数上的 @Validated 校验失败
    @ExceptionHandler(ConstraintViolationException.class)
    public Result<Void> handleConstraint(ConstraintViolationException e) {
        String detail = e.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + " " + v.getMessage())
                .collect(Collectors.joining("; "));
        return Result.fail(ErrorCode.PARAM_INVALID.getCode(),
                ErrorCode.PARAM_INVALID.getMessage() + "：" + detail);
    }

    // 缺少必填的 @RequestParam
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Result<Void> handleMissingParam(MissingServletRequestParameterException e) {
        return Result.fail(ErrorCode.PARAM_INVALID.getCode(), "缺少必填参数：" + e.getParameterName());
    }

    // Redis 连不上：返回 503 而不是 500，前端可区分提示
    @ExceptionHandler(RedisConnectionFailureException.class)
    public Result<Void> handleRedisUnavailable(RedisConnectionFailureException e) {
        log.error("Redis 连接失败", e);
        return Result.fail(ErrorCode.SERVICE_UNAVAILABLE);
    }

    // 兜底：所有没被上面捕获的异常（空指针等代码bug），
    // 打 error 带堆栈，但对外只说"系统繁忙"，不暴露堆栈
    @ExceptionHandler(Exception.class)
    public Result<Void> handleOther(Exception e) {
        log.error("系统异常", e);
        return Result.fail(ErrorCode.SYSTEM_ERROR);
    }
}