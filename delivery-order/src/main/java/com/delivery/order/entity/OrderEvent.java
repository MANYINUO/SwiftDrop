package com.delivery.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 订单事件流水，对应 order_event 表
 * 记录「谁在什么时候做了什么」，排查问题、复盘调度效果、做数据看板都靠它
 */
@Data
@TableName("order_event")
public class OrderEvent {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;
    private String orderNo;         // 冗余字段，便于排查不用 join
    private String eventType;       // 事件类型：ORDER_CREATED / STATUS_CHANGED / DISPATCH_ACCEPTED 等
    private String fromStatus;      // 变更前状态
    private String toStatus;        // 变更后状态
    private String operatorType;    // 操作人类型：USER / RIDER / SYSTEM / ADMIN
    private Long operatorId;        // 操作人ID
    private String remark;          // 备注

    private LocalDateTime createdAt;
}
