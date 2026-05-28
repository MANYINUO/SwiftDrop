package com.delivery.rider.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 骑手实体类，对应数据库 rider 表
 */
@Data
@TableName("rider")
public class Rider {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String riderNo;         // 骑手编号（对外暴露，不暴露自增ID）
    private String name;            // 姓名
    private String phone;           // 手机号
    private Integer onlineStatus;   // 0=离线 1=在线
    private Integer workStatus;     // 0=空闲 1=忙碌
    private Integer maxCapacity;    // 最大同时接单数

    // 注意：这里用有符号 INT，不用 UNSIGNED
    // 因为这个计数器会被减1，UNSIGNED 的 0-1 会下溢报错
    private Integer deliveringCount;// 在手订单数（冗余字段，便于快速筛选）

    private BigDecimal score;       // 服务评分 0.00~5.00

    // 实时位置存在 Redis GEO，这里是冷备（重启后可恢复 / 便于排查）
    private BigDecimal lastLng;     // 最后上报经度
    private BigDecimal lastLat;     // 最后上报纬度
    private LocalDateTime lastReportAt; // 最后上报时间

    // 乐观锁版本号：更新时带 version 条件，防止并发覆盖
    private Integer version;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
