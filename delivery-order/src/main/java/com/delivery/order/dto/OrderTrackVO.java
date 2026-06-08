package com.delivery.order.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单轨迹视图：订单状态 + 骑手实时位置。
 *
 * <p>对应用户端「用户能看到状态和位置变化」的需求。
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

    /** 骑手实时位置（来自 Redis GEO），可能为空 */
    private BigDecimal lng;
    private BigDecimal lat;
    private LocalDateTime locationUpdatedAt;

    private LocalDateTime expectDeliverAt;

    /** 是否已分配骑手 */
    private boolean riderAssigned;

    /**
     * 骑手历史轨迹点（按时间正序）。
     *
     * <p>进页面时直接画出骑手走过的路线，不用等实时推送慢慢累积。
     */
    private List<TrailPoint> trail;

    /** 轨迹点 */
    public record TrailPoint(double lng, double lat) {
    }
}
