package com.delivery.rider.dto;

import com.delivery.rider.entity.Rider;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 骑手视图对象：数据库信息 + 最后上报位置。
 * F1/F2 阶段没有 Redis GEO，位置直接读 rider 表的 last_lng/last_lat。
 */
@Data
public class RiderVO implements Serializable {

    private Long riderId;
    private String riderNo;
    private String name;
    private String phone;

    private boolean online;
    private boolean busy;

    private Integer deliveringCount;
    private Integer maxCapacity;
    private BigDecimal score;

    /** 最后上报位置（来自 rider 表） */
    private BigDecimal lng;
    private BigDecimal lat;
    private LocalDateTime lastReportAt;

    public static RiderVO of(Rider r) {
        RiderVO vo = new RiderVO();
        vo.riderId = r.getId();
        vo.riderNo = r.getRiderNo();
        vo.name = r.getName();
        vo.phone = r.getPhone();
        vo.online = r.getOnlineStatus() != null && r.getOnlineStatus() == 1;
        vo.busy = r.getDeliveringCount() != null && r.getDeliveringCount() > 0;
        vo.deliveringCount = r.getDeliveringCount();
        vo.maxCapacity = r.getMaxCapacity();
        vo.score = r.getScore();
        vo.lng = r.getLastLng();
        vo.lat = r.getLastLat();
        vo.lastReportAt = r.getLastReportAt();
        return vo;
    }
}
