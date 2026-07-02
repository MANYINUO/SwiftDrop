package com.delivery.ai.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 工具调用次数上限 —— 「Agent 会不会挂死」的唯一防线。
 *
 * <p><b>为什么必须自己实现</b>：Spring AI <b>没有</b>内置「最多调用几次工具」的限制。
 * 模型如果反复调用同一个工具（拿到空结果后不断重试是典型场景），请求会一直循环到
 * HTTP 超时：用户看到页面转圈，服务端一直占着线程。
 *
 * <p><b>为什么不用 {@code @Primary} 覆盖 ToolCallingManager</b>：
 * 这里踩过一个真实的坑。最初写成 {@code toolCallingManager(ToolCallingManager delegate)}
 * 并加 {@code @Primary}，结果启动直接失败：
 * <pre>
 *   openAiChatModel → toolCallingManager → toolCallingManager(自己) → 循环依赖
 * </pre>
 * 原因是 {@code OpenAiAutoConfiguration} 创建的 {@code openAiChatModel} 需要注入
 * {@code ToolCallingManager}，而 {@code @Primary} 让容器优先选择我们定义的那个，
 * 我们的 Bean 又要求注入一个 {@code ToolCallingManager}——容器发现"正在创建中"，
 * 判定为循环引用。Spring Boot 3 默认禁止循环引用，于是启动失败。
 *
 * <p><b>正确做法</b>：Bean 方法<b>不注入 delegate</b>，而是在方法内部直接用
 * {@code DefaultToolCallingManager.builder().build()} 创建默认实现。
 * 这样装配期没有任何环，运行期行为完全等价。
 *
 * <p><b>计数器为什么是请求级</b>：若用全局静态变量，并发下所有请求共享同一个计数——
 * 第一个请求用完配额后，其他请求全被误判超限。这里用 ThreadLocal，每请求独立计数，
 * 由 {@code SpringAiCopilot} 在 finally 里清理。
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class ToolLimitConfig {

    private final AiProperties properties;

    /** 请求级计数器：用数组是为了在 lambda 里能写回 */
    private static final ThreadLocal<int[]> CALL_COUNTER = new ThreadLocal<>();

    /** 请求开始时归零，由 Copilot 在调用模型前调用 */
    public static void resetCounter() {
        CALL_COUNTER.set(new int[]{0});
    }

    /** 请求结束时清理，必须放在 finally 里，否则线程复用会串数据 */
    public static void clearCounter() {
        CALL_COUNTER.remove();
    }

    /** 当前请求已发生的工具调用次数，供日志使用 */
    public static int currentCalls() {
        int[] c = CALL_COUNTER.get();
        return c == null ? 0 : c[0];
    }

    /**
     * 包装默认实现：在真正执行工具前累计并检查次数。
     *
     * <p>注意方法签名里<b>没有</b> {@code ToolCallingManager delegate} 参数——
     * 这正是避开循环依赖的关键（见类注释）。
     */
    @Bean
    ToolCallingManager toolCallingManager() {
        ToolCallingManager delegate = ToolCallingManager.builder().build();

        return new ToolCallingManager() {

            @Override
            public List<ToolDefinition> resolveToolDefinitions(ToolCallingChatOptions options) {
                return delegate.resolveToolDefinitions(options);
            }

            @Override
            public ToolExecutionResult executeToolCalls(Prompt prompt, ChatResponse response) {
                int requested = countToolCalls(response);
                if (requested > 0) {
                    int[] counter = CALL_COUNTER.get();
                    if (counter == null) {
                        // 没有走 Copilot 入口（例如别处直接用 ChatClient），
                        // 就地计数，仍然能兜住死循环
                        counter = new int[]{0};
                        CALL_COUNTER.set(counter);
                    }
                    counter[0] += requested;

                    if (counter[0] > properties.getMaxToolCalls()) {
                        log.warn("工具调用次数超限：已调用 {} 次，上限 {}，中断本次请求",
                                counter[0], properties.getMaxToolCalls());
                        throw new ToolLimitExceededException(
                                counter[0], properties.getMaxToolCalls());
                    }
                }
                return delegate.executeToolCalls(prompt, response);
            }
        };
    }

    /** 本轮模型要求执行的工具调用数；无工具调用返回 0 */
    private int countToolCalls(ChatResponse response) {
        if (response == null || response.getResult() == null) {
            return 0;
        }
        AssistantMessage output = response.getResult().getOutput();
        if (output == null || output.getToolCalls() == null) {
            return 0;
        }
        return output.getToolCalls().size();
    }
}
