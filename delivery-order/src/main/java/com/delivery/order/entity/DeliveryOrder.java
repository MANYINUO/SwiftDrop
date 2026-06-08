package com.delivery.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.delivery.common.enums.OrderStatus;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 配送订单主表。
 *
 * <p>字段设计要点：
 * <ul>
 *   <li>金额用「分」存 {@code Integer}，避免浮点误差</li>
 *   <li>对外暴露 {@code orderNo}，不暴露自增 id</li>
 *   <li>{@code version} 配合 CAS 更新做并发控制</li>
 *   <li>{@code needManual} 标记候选骑手耗尽、需人工调度的订单</li>
 * </ul>
 */
@Data
@TableName("delivery_order")
public class DeliveryOrder implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 业务订单号（对外暴露） */
    private String orderNo;

    /** 下单用户 */
    private Long userId;

    /** 当前状态，取值见 {@link OrderStatus} */
    private String status;

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
    private Integer goodsWeightG;
    /** 订单金额（分） */
    private Integer amountFen;
    /** 预估配送距离（米） */
    private Integer distanceM;

    // ---------- 骑手 ----------
    private Long riderId;

    // ---------- 时效节点 ----------
    private LocalDateTime expectDeliverAt;
    private LocalDateTime dispatchedAt;
    private LocalDateTime acceptedAt;
    private LocalDateTime pickedUpAt;
    private LocalDateTime deliveredAt;
    private LocalDateTime cancelledAt;

    // ---------- 调度 ----------
    /** 已进行到第几轮派单 */
    private Integer dispatchRound;
    /** 1 = 需人工调度 */
    private Integer needManual;
    private String cancelReason;

    /** 乐观锁版本号 */
    private Integer version;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public OrderStatus statusEnum() {
        return OrderStatus.of(status);
    }
}
