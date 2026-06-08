package com.delivery.dispatch.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 派单任务：一次「派给某个骑手」= 一条记录。
 *
 * <p><b>这张表是量化指标的唯一来源</b> —— 派单成功率、平均响应时长、平均派单轮次
 * 都能从它查出来。没有这张表，这些指标就只能靠估算。
 */
@Data
@TableName("dispatch_task")
public class DispatchTask implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;
    private String orderNo;

    /** 第几轮派单，从 1 开始 */
    private Integer round;

    /** 目标骑手 */
    private Long riderId;

    /** 该骑手在候选列表中的排名 */
    private Integer candidateRank;

    /** 综合打分 */
    private BigDecimal candidateScore;

    /** 打分明细（JSON）：各因子得分，便于调参和排查「为什么派给他」 */
    private String scoreDetail;

    /** PENDING / ACCEPTED / REJECTED / TIMEOUT / CANCELLED */
    private String status;

    /** 响应截止时间 */
    private LocalDateTime expireAt;

    private LocalDateTime respondedAt;
    private LocalDateTime createdAt;
}
