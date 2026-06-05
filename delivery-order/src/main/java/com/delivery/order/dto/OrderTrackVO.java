package com.delivery.order.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单轨迹视图：订单状态 + 骑手实时位置
 * F1 简化版：骑手位置和历史轨迹都为空（F4 推送阶段加回）
 */
@Data
public class OrderTrackVO implements Serializable {

    private String orderNo;
    private String status;
    private String statusDesc;

    private Long riderId;
    private String riderNo;
    private String riderName;
    private String riderPhone;

    /** 骑手实时位置（来自 Redis GEO），F1 阶段为空 */
    private BigDecimal lng;
    private BigDecimal lat;
    private LocalDateTime locationUpdatedAt;

    private LocalDateTime expectDeliverAt;

    /** 是否已分配骑手 */
    private boolean riderAssigned;

    /** 骑手历史轨迹点，F1 阶段为空 */
    private List<TrailPoint> trail;

    /** 轨迹点 */
    public record TrailPoint(double lng, double lat) {
    }
}
