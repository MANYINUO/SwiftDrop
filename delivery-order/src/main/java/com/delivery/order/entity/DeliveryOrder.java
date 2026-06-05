package com.delivery.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.delivery.common.enums.OrderStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 配送订单实体类，对应 delivery_order 表
 */
@Data
@TableName("delivery_order")
public class DeliveryOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String orderNo;         // 业务订单号（对外暴露，不暴露自增ID）
    private Long userId;            // 下单用户ID
    private String status;          // 订单状态：PENDING_DISPATCH / DISPATCHING / PENDING_PICKUP / DELIVERING / DELIVERED / CANCELLED / EXCEPTION

    // ---------- 取件信息 ----------
    private BigDecimal pickupLng;
    private BigDecimal pickupLat;
    private String pickupAddress;
    private String pickupContact;
    private String pickupPhone;

    // ---------- 送达信息 ----------
    private BigDecimal deliverLng;
    private BigDecimal deliverLat;
    private String deliverAddress;
    private String deliverContact;
    private String deliverPhone;

    // ---------- 物品与金额 ----------
    private String goodsDesc;
    private Integer goodsWeightG;   // 物品重量（克）

    // 金额统一用「分」存 INT，不用 DECIMAL/FLOAT
    // 原因：浮点数有精度误差，财务计算必须用整数分
    private Integer amountFen;      // 订单金额（分）
    private Integer distanceM;      // 预估配送距离（米）

    // ---------- 骑手 ----------
    private Long riderId;           // 接单骑手ID，未派单为 NULL

    // ---------- 时效节点 ----------
    private LocalDateTime expectDeliverAt;  // 承诺送达时间
    private LocalDateTime dispatchedAt;     // 首次派单时间
    private LocalDateTime acceptedAt;       // 骑手接单时间
    private LocalDateTime pickedUpAt;       // 骑手取货时间
    private LocalDateTime deliveredAt;      // 送达时间
    private LocalDateTime cancelledAt;      // 取消时间

    // ---------- 调度相关 ----------
    private Integer dispatchRound;  // 已进行到第几轮派单
    private Integer needManual;     // 1=需人工调度（候选骑手已耗尽）
    private String cancelReason;    // 取消原因

    // 乐观锁版本号
    private Integer version;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * 把字符串状态转成枚举，方便业务代码判断
     */
    public OrderStatus statusEnum() {
        return OrderStatus.of(this.status);
    }
}
