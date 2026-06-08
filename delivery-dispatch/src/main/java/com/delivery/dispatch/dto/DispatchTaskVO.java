package com.delivery.dispatch.dto;

import com.delivery.dispatch.entity.DispatchTask;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 派单任务视图（推送给骑手端 / 后台展示）。
 */
@Data
public class DispatchTaskVO implements Serializable {

    private Long taskId;
    private String orderNo;
    private Integer round;
    private Long riderId;
    private Integer candidateRank;
    private BigDecimal candidateScore;
    private String scoreDetail;
    private String status;
    private LocalDateTime expireAt;
    private LocalDateTime createdAt;

    /** 剩余响应时间（秒） */
    private Long remainingSeconds;

    // ---------- 订单摘要（骑手端展示用）----------
    private String pickupAddress;
    private String deliverAddress;
    private Integer distanceM;
    private Integer amountFen;
    private String amountYuan;
    private String goodsDesc;

    public static DispatchTaskVO of(DispatchTask t) {
        DispatchTaskVO vo = new DispatchTaskVO();
        vo.taskId = t.getId();
        vo.orderNo = t.getOrderNo();
        vo.round = t.getRound();
        vo.riderId = t.getRiderId();
        vo.candidateRank = t.getCandidateRank();
        vo.candidateScore = t.getCandidateScore();
        vo.scoreDetail = t.getScoreDetail();
        vo.status = t.getStatus();
        vo.expireAt = t.getExpireAt();
        vo.createdAt = t.getCreatedAt();
        if (t.getExpireAt() != null) {
            vo.remainingSeconds = Math.max(0,
                    java.time.Duration.between(LocalDateTime.now(), t.getExpireAt()).getSeconds());
        }
        return vo;
    }
}
