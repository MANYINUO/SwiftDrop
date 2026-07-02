package com.delivery.ai.config;

import com.delivery.ai.tool.DispatchTools;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * ChatClient 装配。
 *
 * <p><b>为什么 System Prompt 外置成 md 文件</b>：Prompt 是这个模块里改动最频繁的东西，
 * 而且改它需要「读起来」而不是「编译得过」。放在 resources 下，改完重启即生效，
 * 不必重新编译 Java；也方便后续做版本化与 A/B 对比。
 *
 * <p><b>为什么用 defaultSystem 而不是每次 prompt 时拼</b>：默认系统提示会在每次调用时
 * 自动带上，避免某处漏写导致模型失去约束——漏一次 Prompt 就等于漏一次防幻觉。
 */
@Slf4j
@Configuration
public class ChatClientConfig {

    private static final String PROMPT_PATH = "prompts/system-copilot.md";

    @Bean
    ChatClient copilotChatClient(ChatClient.Builder builder, DispatchTools dispatchTools) {
        return builder
                .defaultSystem(loadPrompt())
                .defaultTools(dispatchTools)
                .build();
    }

    /**
     * 读取 Prompt 文件。
     *
     * <p>读不到时返回一个最小可用的兜底 Prompt，而不是让应用启动失败。
     * 理由同 {@link AiProperties#validate()}：一个文件缺失不应该拖垮整个平台。
     * 但这里会打 ERROR，因为它意味着解释质量会明显下降。
     */
    private String loadPrompt() {
        try {
            ClassPathResource resource = new ClassPathResource(PROMPT_PATH);
            String content = resource.getContentAsString(StandardCharsets.UTF_8);
            log.info("已加载 System Prompt：{} ({} 字符)", PROMPT_PATH, content.length());
            return content;
        } catch (IOException e) {
            log.error("System Prompt 加载失败：{}，将使用最小兜底提示", PROMPT_PATH, e);
            return "你是调度分析助手。只能依据工具返回的数据回答，禁止编造任何数字。";
        }
    }
}
