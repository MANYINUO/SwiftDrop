package com.delivery.dispatch.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 派单任务，对应 dispatch_task 表
 * 每次「派给某个骑手」= 一条记录
 * 这张表是量化指标的来源：派单成功率、平均响应时长都靠它统计
 */
@Data
@TableName("dispatch_task")
public class DispatchTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;
    private String orderNo;         // 冗余，便于排查
    private Integer round;          // 第几轮派单，从1开始
    private Long riderId;           // 目标骑手ID
    private Integer candidateRank;  // 该骑手在候选列表中的排名
    private BigDecimal candidateScore; // 派单打分（距离+评分+负载综合）
    private String status;          // PENDING / ACCEPTED / REJECTED / TIMEOUT / CANCELLED
    private LocalDateTime expireAt; // 响应截止时间（超时未响应=TIMEOUT）
    private LocalDateTime respondedAt; // 骑手响应时间
    private LocalDateTime createdAt;
}
