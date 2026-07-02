package com.delivery.ai.config;

import lombok.Getter;

/**
 * 工具调用次数超限异常。
 *
 * <p><b>为什么需要它</b>：Spring AI <b>没有</b>内置「最多调用几次工具」的限制。
 * 模型如果反复调用同一个工具（典型场景：拿到空结果后不断重试），请求会一直循环
 * 直到 HTTP 超时——用户看到的是页面转圈，服务端则一直占着线程。
 *
 * <p>这是「Agent 会不会挂死」的唯一防线，因此单独建一个异常类型：
 * 让 {@code SpringAiCopilot} 能精确捕获它并降级为模板，而不是混在通用 Exception 里。
 */
@Getter
public class ToolLimitExceededException extends RuntimeException {

    /** 实际已经发生的工具调用次数，用于日志排查 */
    private final int actualCalls;

    /** 配置的上限 */
    private final int limit;

    public ToolLimitExceededException(int actualCalls, int limit) {
        super(String.format("工具调用次数超过上限：已调用 %d 次，上限 %d 次", actualCalls, limit));
        this.actualCalls = actualCalls;
        this.limit = limit;
    }
}
