package com.delivery.ai.answer;

import com.delivery.ai.facade.DispatchExplainVO;
import com.delivery.dispatch.dto.DispatchTaskVO;
import com.delivery.order.dto.OrderVO;
import com.delivery.rider.entity.Rider;
import com.delivery.rider.mapper.RiderMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 模板答案 —— 不依赖模型的兜底文案。
 *
 * <p><b>为什么模板必须认真写</b>：模型超时、429、输出被守卫拦下、开关被关闭时，
 * 用户看到的就是它。<b>它不是「错误提示」，而是降级后的正常答案。</b>
 * 一个敷衍的模板等于把「AI 不可用」直接暴露给用户。
 *
 * <p><b>为什么它天然可信</b>：模板里的数字全部直接来自 {@code scoreDetail}，
 * 由 Java 拼装，不经过模型，因此不存在幻觉可能。这也是它能在守卫拦截后安全接管的原因。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TemplateAnswer {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final RiderMapper riderMapper;

    /**
     * 生成模板答案。
     *
     * @param orderNo 订单号
     * @param order   订单详情（可为 null，表示查不到）
     * @param tasks   派单记录（可为空）
     */
    public DispatchExplainVO build(String orderNo, OrderVO order, List<DispatchTaskVO> tasks) {

        // ---------- 无订单 ----------
        if (order == null) {
            return DispatchExplainVO.template(orderNo,
                    "没有查到订单 " + orderNo + " 的信息。",
                    List.of("请确认订单号是否正确。"));
        }

        // ---------- 无派单记录 ----------
        if (tasks == null || tasks.isEmpty()) {
            return DispatchExplainVO.template(orderNo,
                    String.format("订单 %s 还没有派单记录，当前状态是「%s」。",
                            orderNo, order.getStatusDesc()),
                    List.of(
                            String.format("已进行到第 %s 轮派单。",
                                    order.getDispatchRound() == null ? 0 : order.getDispatchRound()),
                            Boolean.TRUE.equals(order.getNeedManual())
                                    ? "当前标记为需人工调度。"
                                    : "当前未标记人工调度。"));
        }

        // ---------- 先找真实接单任务；未接单时只解释当前轮仍有效的候选 ----------
        DispatchTaskVO winner = tasks.stream()
                .filter(t -> "ACCEPTED".equals(t.getStatus()))
                .filter(t -> Objects.equals(t.getRiderId(), order.getRiderId()))
                .findFirst()
                .orElse(null);

        boolean accepted = winner != null;
        if (!accepted && order.getRiderId() != null) {
            return DispatchExplainVO.template(orderNo,
                    "订单已关联骑手，但派单记录中没有对应的接单任务。",
                    List.of("派单记录可能不完整，请核对订单与派单明细。"));
        }

        int currentRound = order.getDispatchRound() == null ? 0 : order.getDispatchRound();
        List<DispatchTaskVO> currentRoundTasks = tasks.stream()
                .filter(t -> Objects.equals(t.getRound(), currentRound))
                .collect(Collectors.toList());

        if (!accepted) {
            winner = currentRoundTasks.stream()
                    .filter(t -> "PENDING".equals(t.getStatus()))
                    .min(Comparator.comparing(
                            t -> t.getCandidateRank() == null ? Integer.MAX_VALUE : t.getCandidateRank()))
                    .orElse(null);
        }

        if (winner == null) {
            String conclusion = currentRoundTasks.stream().anyMatch(t -> "PENDING".equals(t.getStatus()))
                    ? "订单当前仍在等待骑手响应。"
                    : "订单当前没有有效的待响应或已接单任务。";
            List<String> evidence = new ArrayList<>();
            evidence.add("订单当前状态为「" + order.getStatusDesc() + "」。");
            if (currentRound > 0) {
                evidence.add(String.format("当前派单轮次为第 %d 轮。", currentRound));
            }
            if (Boolean.TRUE.equals(order.getNeedManual())) {
                evidence.add("订单当前标记为需人工调度。");
            }
            return DispatchExplainVO.template(orderNo, conclusion, evidence);
        }

        int taskRound = winner.getRound() == null ? currentRound : winner.getRound();

        String riderLabel = resolveRiderLabel(winner.getRiderId());
        double total = winner.getCandidateScore() == null
                ? 0d : winner.getCandidateScore().doubleValue();

        Map<String, Object> detail = parse(winner.getScoreDetail());
        boolean manualAssignment = detail != null && "MANUAL_ASSIGN".equals(detail.get("source"));
        String conclusion;
        if (manualAssignment && accepted) {
            conclusion = String.format("后台在第 %d 轮手动指派给 %s，骑手已接单。",
                    taskRound, riderLabel);
        } else if (manualAssignment) {
            conclusion = String.format("后台在第 %d 轮手动指派给 %s，目前等待接单。",
                    taskRound, riderLabel);
        } else if (accepted) {
            conclusion = String.format("第 %d 轮综合得分 %.3f，%s 已接单。",
                    taskRound, total, riderLabel);
        } else {
            conclusion = String.format("第 %d 轮排名靠前的候选是 %s，目前尚未接单。",
                    taskRound, riderLabel);
        }

        // ---------- 4 条证据，全部来自 scoreDetail ----------
        List<String> evidence = new ArrayList<>(4);
        if (manualAssignment) {
            evidence.add("该任务由后台手动指派，分值不代表算法排名。");
        } else if (detail != null) {
            addEvidence(evidence, detail, "distanceScore", "距离分", "越接近 1 表示离取件点越近");
            addEvidence(evidence, detail, "routeScore", "顺路分", "越接近 1 表示行进方向越顺路");
            addEvidence(evidence, detail, "loadScore", "负载分", "越接近 1 表示手上订单越少");
            addEvidence(evidence, detail, "timeRiskScore", "时效风险分", "越接近 1 表示超时风险越低");

            Object dist = detail.get("distanceToPickupM");
            if (dist != null) {
                evidence.add(String.format("骑手距离取件点约 %s 米。", formatNumber(dist)));
            }
        } else {
            evidence.add(String.format("综合得分为 %.3f。", total));
        }

        if (!accepted && !manualAssignment) {
            long pendingCount = currentRoundTasks.stream()
                    .filter(t -> "PENDING".equals(t.getStatus()))
                    .count();
            if (pendingCount > 0) {
                evidence.add(String.format("当前轮有 %d 名骑手仍待响应。", pendingCount));
            }
        }

        return DispatchExplainVO.template(orderNo, conclusion, evidence);
    }

    /** 拼一条证据，字段缺失就跳过（不要输出「null」） */
    private void addEvidence(List<String> evidence, Map<String, Object> detail,
                             String key, String label, String explain) {
        Object v = detail.get(key);
        if (v == null) {
            return;
        }
        evidence.add(String.format("%s %s（%s）。", label, formatNumber(v), explain));
    }

    /** 数字格式化：整数不带小数点，小数保留原样 */
    private String formatNumber(Object v) {
        if (v instanceof Number n) {
            double d = n.doubleValue();
            if (d == Math.floor(d) && !Double.isInfinite(d)) {
                return String.valueOf((long) d);
            }
            return BigDecimal.valueOf(d).stripTrailingZeros().toPlainString();
        }
        return String.valueOf(v);
    }

    private Map<String, Object> parse(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, Map.class);
        } catch (Exception e) {
            return null;
        }
    }

    /** 取骑手编号；查不到时退化为 ID，不编造 */
    private String resolveRiderLabel(Long riderId) {
        if (riderId == null) {
            return "未知骑手";
        }
        try {
            Rider r = riderMapper.selectById(riderId);
            return r == null ? ("骑手 " + riderId) : r.getRiderNo();
        } catch (Exception e) {
            log.debug("模板答案查骑手失败 riderId={}", riderId, e);
            return "骑手 " + riderId;
        }
    }
}
