package com.delivery.dispatch.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 一轮派单的结果。
 */
@Data
public class DispatchRoundResult implements Serializable {

    /** 结果类型 */
    private String outcome;
    private String orderNo;
    private int round;
    private int candidateCount;
    private LocalDateTime expireAt;
    private String message;
    /** 候选骑手摘要，便于演示时打印 */
    private List<String> candidateExplains;

    public static DispatchRoundResult dispatched(String orderNo, int round, int count,
                                                 LocalDateTime expireAt, List<String> explains) {
        DispatchRoundResult r = new DispatchRoundResult();
        r.outcome = "DISPATCHED";
        r.orderNo = orderNo;
        r.round = round;
        r.candidateCount = count;
        r.expireAt = expireAt;
        r.candidateExplains = explains;
        r.message = String.format("第 %d 轮派单成功，已推送 %d 名候选骑手", round, count);
        return r;
    }

    public static DispatchRoundResult noCandidate(String orderNo, int round) {
        DispatchRoundResult r = new DispatchRoundResult();
        r.outcome = "NO_CANDIDATE";
        r.orderNo = orderNo;
        r.round = round;
        r.message = String.format("第 %d 轮未找到可用骑手，准备扩大搜索范围", round);
        return r;
    }

    public static DispatchRoundResult exhausted(String orderNo, int round) {
        DispatchRoundResult r = new DispatchRoundResult();
        r.outcome = "EXHAUSTED";
        r.orderNo = orderNo;
        r.round = round;
        r.message = "所有轮次均无可用骑手，订单已转人工调度";
        return r;
    }

    public static DispatchRoundResult skipped(String orderNo, String status) {
        DispatchRoundResult r = new DispatchRoundResult();
        r.outcome = "SKIPPED";
        r.orderNo = orderNo;
        r.message = "订单当前状态为 " + status + "，无需派单";
        return r;
    }
}
