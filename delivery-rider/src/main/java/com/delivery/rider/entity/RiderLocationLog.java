package com.delivery.rider.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 骑手位置流水，对应 rider_location_log 表
 * 实时位置在 Redis GEO，这张表只为「回放历史轨迹」和排查问题
 * 数据量大，上线前需按 rider_id 或 report_at 分表
 */
@Data
@TableName("rider_location_log")
public class RiderLocationLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long riderId;           // 骑手ID
    private String orderNo;         // 关联订单号（配送中才有）

    // 经纬度用 DECIMAL(10,7)，约 1cm 精度，比 MySQL POINT 类型好调试
    private BigDecimal lng;         // 经度
    private BigDecimal lat;         // 纬度

    private LocalDateTime reportAt; // 上报时间（毫秒精度 DATETIME(3)）
}
