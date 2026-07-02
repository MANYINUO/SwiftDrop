package com.delivery.ai.facade;

import com.delivery.ai.answer.TemplateAnswer;
import com.delivery.ai.config.AiProperties;
import com.delivery.ai.config.ToolLimitConfig;
import com.delivery.ai.context.FactSheet;
import com.delivery.ai.context.FactSheetHolder;
import com.delivery.ai.guard.OutputGuard;
import com.delivery.dispatch.dto.DispatchTaskVO;
import com.delivery.dispatch.service.DispatchService;
import com.delivery.order.dto.OrderVO;
import com.delivery.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 基于 Spring AI 的 Copilot 实现。
 *
 * <p><b>整体流程</b>（顺序不能变）：
 * <pre>
 *   开关关闭 → 模板
 *   绑定 FactSheet（ThreadLocal）
 *   调模型（模型自己决定调哪个工具）
 *   异常 → 模板
 *   守卫校验 → 不通过 → 模板
 *   通过 → 拆分结论 / 证据
 *   finally 清理 ThreadLocal
 * </pre>
 *
 * <p><b>为什么先绑定 FactSheet 再调模型</b>：工具方法在执行时要往 FactSheet 写数据，
 * 而工具是模型在调用过程中触发的。必须在进入模型调用前就绑好，否则工具执行时
 * 拿到的是 null，数字白名单就是空的，守卫会把所有数字都判为越界。
 *
 * <p><b>为什么 finally 里必须 clear</b>：Tomcat 线程复用。不清会导致下一个请求
 * 读到上一个订单的事实表——那不是「校验失效」，而是「拿别人的数据当白名单」，
 * 比不校验更危险。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SpringAiCopilot implements Copilot {

    /** 固定问句。不拼接用户输入，避免提示注入 */
    private static final String QUESTION_TEMPLATE =
            "解释订单 %s 为什么派给了现在的骑手。只依据工具返回的数据，不要编造数字。";

    /** 从模型输出里拆结论与证据：以「- 」或「* 」开头的行算证据 */
    private static final Pattern EVIDENCE_LINE = Pattern.compile("^\\s*[-*•]\\s+(.+)$", Pattern.MULTILINE);

    private final ChatClient copilotChatClient;
    private final AiProperties properties;
    private final TemplateAnswer templateAnswer;
    private final OrderService orderService;
    private final DispatchService dispatchService;

    @Override
    public DispatchExplainVO explain(String orderNo) {

        // ---------- ① 开关关闭：直接模板，不碰模型 ----------
        if (!properties.isEnabled()) {
            return template(orderNo);
        }

        long start = System.currentTimeMillis();
        FactSheet facts = new FactSheet();
        FactSheetHolder.set(facts);
        // 工具调用计数器归零：必须和 FactSheet 一起在模型调用前初始化
        ToolLimitConfig.resetCounter();

        try {
            String question = String.format(QUESTION_TEMPLATE, orderNo);

            // ---------- ② 调模型：工具由模型自行决定调用 ----------
            String raw = copilotChatClient.prompt()
                    .user(question)
                    .call()
                    .content();

            long latency = System.currentTimeMillis() - start;

            if (raw == null || raw.isBlank()) {
                log.warn("模型返回空内容 orderNo={}，降级模板", orderNo);
                return template(orderNo);
            }

            // ---------- ③ 数字守卫 ----------
            OutputGuard guard = new OutputGuard(properties.getMaxAnswerChars());
            OutputGuard.GuardResult result = guard.check(raw, facts);
            if (!result.isPassed()) {
                log.warn("输出守卫未通过 orderNo={} reason={} violations={}，降级模板",
                        orderNo, result.getReason(), result.getViolations());
                return template(orderNo);
            }

            // ---------- ④ 拆分结论与证据 ----------
            DispatchExplainVO vo = split(orderNo, result.getAnswer(), latency);
            log.info("AI 解释成功 orderNo={} mode=LLM latency={}ms tools={}",
                    orderNo, latency, facts.isEmpty() ? "未调用" : "已调用");
            return vo;

        } catch (Exception e) {
            // 模型超时 / 429 / 5xx / 工具次数超限：全部降级，绝不外抛
            log.warn("AI 解释失败 orderNo={} elapsed={}ms toolCalls={}，降级模板：{}",
                    orderNo, System.currentTimeMillis() - start,
                    ToolLimitConfig.currentCalls(), e.toString());
            return template(orderNo);
        } finally {
            // 必须清理，否则线程池复用会串到下一个请求
            FactSheetHolder.clear();
            ToolLimitConfig.clearCounter();
        }
    }

    /**
     * 模板兜底。
     *
     * <p>模板需要订单与派单数据，因此这里重新查一次。之所以不复用模型调用前的查询，
     * 是因为模型可能根本没被调用（开关关闭、超时），此时没有任何前置查询可复用。
     * 这两次查询都是主键/索引查询，代价可接受。
     */
    private DispatchExplainVO template(String orderNo) {
        OrderVO order = null;
        List<DispatchTaskVO> tasks = List.of();
        try {
            order = orderService.getByOrderNo(orderNo);
            tasks = dispatchService.listByOrderNo(orderNo);
        } catch (Exception e) {
            log.warn("模板答案取数失败 orderNo={}", orderNo, e);
        }
        return templateAnswer.build(orderNo, order, tasks);
    }

    /**
     * 把模型输出拆成「一句话结论 + 证据列表」。
     *
     * <p>模型的输出是纯文本，而前端需要结构化渲染。拆分规则很简单：
     * 以 {@code - } / {@code * } 开头的行是证据，第一段非空文本是结论，其余忽略。
     *
     * <p>拆不出来时退化为「整段当结论，证据为空」——宁可展示得朴素一点，
     * 也不要因为格式不符合预期就丢掉一次成功的模型调用。
     */
    private DispatchExplainVO split(String orderNo, String answer, long latency) {
        List<String> evidence = new ArrayList<>();
        Matcher m = EVIDENCE_LINE.matcher(answer);
        while (m.find()) {
            String line = m.group(1).trim();
            if (!line.isEmpty()) {
                evidence.add(line);
            }
        }

        // 结论 = 第一个「- 」之前的非空行
        String conclusion = answer;
        int firstBullet = answer.indexOf("- ");
        if (firstBullet > 0) {
            conclusion = answer.substring(0, firstBullet).trim();
        }
        // 去掉可能的 markdown 标题符号
        conclusion = conclusion.replaceAll("^#+\\s*", "").trim();
        // 结论太长时只取第一句
        int stop = conclusion.indexOf('。');
        if (stop > 0 && stop < conclusion.length() - 1) {
            conclusion = conclusion.substring(0, stop + 1);
        }

        if (conclusion.isEmpty()) {
            conclusion = "已生成解释，但模型未按预期格式输出。";
        }
        if (evidence.isEmpty()) {
            evidence = List.of("模型未给出分条依据，请查看下方派单明细。");
        }
        return DispatchExplainVO.llm(orderNo, conclusion, evidence, latency);
    }
}
