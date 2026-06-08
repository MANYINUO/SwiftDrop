package com.delivery.rider.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 骑手位置流水（轨迹回放用）。
 *
 * <p>实时位置在 Redis GEO；这张表是历史轨迹。
 * 写入走「内存缓冲 + 定时批量落库」，不阻塞上报接口。
 *
 * <p>数据量估算：1 万骑手 × 每 3 秒一点 × 10 小时 = 1.2 亿点/天。
 * 生产环境需要按 rider_id 或 report_at 分表，并考虑换 ClickHouse / TDengine。
 */
@Data
@TableName("rider_location_log")
public class RiderLocationLog implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long riderId;

    /** 关联订单号（配送中才有值） */
    private String orderNo;

    private BigDecimal lng;
    private BigDecimal lat;

    private LocalDateTime reportAt;
}
