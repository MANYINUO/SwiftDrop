package com.delivery.ai.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * AI 模块的可调参数，对应 application.yml 里的 {@code delivery.ai.*}。
 *
 * <p><b>为什么要有 enabled 开关</b>：AI 是增值功能，不是主链路。当 API Key 没配、
 * 模型服务故障或成本需要临时掐断时，必须能在<b>不改代码、不重启</b>的前提下退回模板答案，
 * 而不是让后台页面直接报错。这也让「关掉 AI 后系统依然完整可用」成为一条可验证的事实。
 *
 * <p><b>为什么默认是 false</b>：默认关闭意味着「没配 Key 的部署天然不会失败」。
 * 开启需要显式设置 {@code AI_ENABLED=true}，避免演示环境里一次误提交就产生真实账单。
 */
@Slf4j
@Data
@Component
@ConfigurationProperties(prefix = "delivery.ai")
public class AiProperties {

    /** 总开关。false 时不调用模型，直接返回模板答案 */
    private boolean enabled = false;

    /** 答案长度上限，超出截断到最后一个完整句 */
    private int maxAnswerChars = 400;

    /** 工具调用次数上限。模型反复调同一工具时会一直循环，必须兜住 */
    private int maxToolCalls = 6;

    /** 按「订单 + 轮次」缓存解释结果的秒数 */
    private long cacheTtlSeconds = 3600;

    /**
     * 模型 API Key。这里只读出来做启动校验，不参与业务逻辑。
     *
     * <p>Spring AI 自己从 {@code spring.ai.openai.api-key} 读取，这里读同一份配置只是为了
     * 在「开关打开但没配 Key」时能提前降级，而不是等到第一次调用才抛异常。
     */
    @Value("${spring.ai.openai.api-key:}")
    private String apiKey;

    /**
     * 启动自检：开关打开但没有 API Key 时自动关闭，只打 WARN 不阻断启动。
     *
     * <p>理由：让应用能起来永远比让某个功能可用更重要。若这里直接抛异常，
     * 一个漏配的环境变量就会让整个调度平台无法启动——那才是真正的事故。
     */
    @PostConstruct
    void validate() {
        if (enabled && (apiKey == null || apiKey.isBlank())) {
            log.warn("delivery.ai.enabled=true 但未配置 spring.ai.openai.api-key，"
                    + "已自动关闭 AI 能力，接口将返回模板答案。"
                    + "如需启用请设置环境变量 AI_API_KEY。");
            enabled = false;
        }
        if (enabled) {
            log.info("AI 调度 Copilot 已启用：maxToolCalls={}, maxAnswerChars={}",
                    maxToolCalls, maxAnswerChars);
        }
    }
}
