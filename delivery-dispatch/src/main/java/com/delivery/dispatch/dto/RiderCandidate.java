package com.delivery.dispatch.dto;

import com.delivery.common.util.GeoUtil;
import lombok.Data;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 候选骑手 + 打分明细。
 *
 * <p>这是 v2 多目标打分模型的产物。把每个因子的得分都留着，
 * 一是方便调参（看哪个因子在起决定作用），
 * 二是出问题时能直接回答「为什么派给了他，而不是那个更近的」。
 */
@Data
public class RiderCandidate implements Serializable {

    private Long riderId;
    private String riderNo;
    private String riderName;

    /** 骑手到取件点的距离（米） */
    private double distanceToPickup;

    private int deliveringCount;
    private int maxCapacity;

    /** 骑手历史评分（0~5） */
    private double rating;

    // ---------- 五个因子得分，均归一化到 [0,1] ----------
    /** 距离分：越近越高 */
    private double distanceScore;
    /** 负载分：在手单越少越高 */
    private double loadScore;
    /** 顺路分：行进方向与取件方向越一致越高 */
    private double routeScore;
    /** 评分分：历史评分越高越高 */
    private double ratingScore;
    /** 时效风险分：预计送达越不超时越高 */
    private double timeRiskScore;

    /** 顺路度原始余弦值，[-1,1]，便于解释 */
    private double cosine;
    /** 预计送达总耗时（秒） */
    private int etaSeconds;

    /** 综合得分 */
    private double score;

    /** 打分明细 JSON（写入 dispatch_task.score_detail） */
    public String detailJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("distanceScore", round4(distanceScore));
        m.put("loadScore", round4(loadScore));
        m.put("routeScore", round4(routeScore));
        m.put("ratingScore", round4(ratingScore));
        m.put("timeRiskScore", round4(timeRiskScore));
        m.put("cosine", round4(cosine));
        m.put("distanceToPickupM", Math.round(distanceToPickup));
        m.put("deliveringCount", deliveringCount);
        m.put("maxCapacity", maxCapacity);
        m.put("etaSeconds", etaSeconds);
        m.put("total", round4(score));
        return toJson(m);
    }

    /** 人类可读的解释，方便演示时直接打日志 */
    public String explain() {
        return String.format(
                "骑手%s 距离%.0fm 在手上限%d/%d 顺路度%.2f 评分%.1f 预计%d秒 => 综合%.4f",
                riderId, distanceToPickup, deliveringCount, maxCapacity,
                cosine, rating, etaSeconds, score);
    }

    private static double round4(double v) {
        return Math.round(v * 10000d) / 10000d;
    }

    /** 极简 JSON 拼装，避免为一个明细引入额外依赖 */
    private static String toJson(Map<String, Object> m) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> e : m.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append('"').append(e.getKey()).append("\":");
            Object v = e.getValue();
            if (v instanceof Number) {
                sb.append(v);
            } else {
                sb.append('"').append(v).append('"');
            }
        }
        return sb.append('}').toString();
    }

    /** 计算两个坐标的距离，供选择器内部使用 */
    public static double distance(double lng1, double lat1, double lng2, double lat2) {
        return GeoUtil.distanceMeters(lng1, lat1, lng2, lat2);
    }
}
