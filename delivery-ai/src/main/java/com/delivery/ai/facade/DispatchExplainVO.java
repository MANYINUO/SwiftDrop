package com.delivery.ai.facade;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 派单解释结果。
 *
 * <p><b>为什么带 {@code mode}</b>：调用方需要知道这段文字是模型生成的还是模板兜底的。
 * 前端据此显示不同的小字（「数字来自派单明细 · LLM」/「· 模板」），
 * 排查问题时也能一眼看出这次是否发生了降级——这比翻日志快得多。
 */
@Data
public class DispatchExplainVO implements Serializable {

    private String orderNo;

    /** 一句话结论 */
    private String conclusion;

    /** 带数字的依据，通常 4 条 */
    private List<String> evidence;

    /** LLM = 模型生成；TEMPLATE = 模板兜底 */
    private String mode;

    /** 本次耗时（毫秒），用于观察是否接近超时 */
    private long latencyMs;

    public static DispatchExplainVO llm(String orderNo, String conclusion,
                                        List<String> evidence, long latencyMs) {
        DispatchExplainVO vo = new DispatchExplainVO();
        vo.orderNo = orderNo;
        vo.conclusion = conclusion;
        vo.evidence = evidence;
        vo.mode = "LLM";
        vo.latencyMs = latencyMs;
        return vo;
    }

    public static DispatchExplainVO template(String orderNo, String conclusion,
                                             List<String> evidence) {
        DispatchExplainVO vo = new DispatchExplainVO();
        vo.orderNo = orderNo;
        vo.conclusion = conclusion;
        vo.evidence = evidence;
        vo.mode = "TEMPLATE";
        vo.latencyMs = 0L;
        return vo;
    }
}
