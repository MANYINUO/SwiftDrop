package com.delivery.rider.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 骑手。
 *
 * <p>注意 {@code deliveringCount} 是<b>冗余字段</b>：
 * 严格来说可以由订单表统计得出，但每次派单都要查一遍太慢。
 * 这里用冗余 + 在接单/送达时同步更新，是典型的「用一致性换性能」。
 */
@Data
@TableName("rider")
public class Rider implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 骑手编号（对外暴露） */
    private String riderNo;

    private String name;
    private String phone;

    /** 0=离线 1=在线 */
    private Integer onlineStatus;

    /** 0=空闲 1=忙碌 */
    private Integer workStatus;

    /** 最大同时接单数 */
    private Integer maxCapacity;

    /** 在手订单数（冗余） */
    private Integer deliveringCount;

    /** 服务评分 0.00~5.00 */
    private BigDecimal score;

    /** 最后上报位置（冷备；实时位置在 Redis GEO） */
    private BigDecimal lastLng;
    private BigDecimal lastLat;
    private LocalDateTime lastReportAt;

    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public boolean isOnline() {
        return onlineStatus != null && onlineStatus == 1;
    }

    public boolean isFree() {
        return deliveringCount == null || deliveringCount == 0;
    }
}
