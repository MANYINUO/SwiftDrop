package com.delivery.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 订单事件流水表。
 *
 * <p>记录「谁在什么时候做了什么」。排查问题、复盘调度效果、做数据看板都靠它。
 * 注意：这是<b>只追加</b>的表，不做更新和删除。
 */
@Data
@TableName("order_event")
public class OrderEvent implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;
    private String orderNo;

    /** 事件类型，取值见 OrderEventType */
    private String eventType;

    private String fromStatus;
    private String toStatus;

    /** 操作方类型，取值见 OperatorType */
    private String operatorType;
    private Long operatorId;

    private String remark;
    /** 扩展信息（JSON 字符串） */
    private String ext;

    private LocalDateTime createdAt;
}
