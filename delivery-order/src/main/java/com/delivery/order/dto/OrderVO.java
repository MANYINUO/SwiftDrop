package com.delivery.order.dto;

import com.delivery.common.enums.OrderStatus;
import com.delivery.order.entity.DeliveryOrder;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单视图对象（VO，View Object）：返回给前端的数据结构。
 *
 * <p>与实体 {@code DeliveryOrder} 的区别：VO 只挑前端需要的字段，并额外派生
 * {@code statusDesc}（状态中文名）与 {@code amountYuan}（把「分」换算成「元」用于显示）。
 * 数据库内部一律用「分」存整数以避免浮点误差，换算成元只发生在出参这一层。
 */
@Data
public class OrderVO implements Serializable {

    private String orderNo;
    private Long userId;

    private String status;
    private String statusDesc;

    private BigDecimal pickupLng;
    private BigDecimal pickupLat;
    private String pickupAddress;
    private String pickupContact;
    private String pickupPhone;

    private BigDecimal deliverLng;
    private BigDecimal deliverLat;
    private String deliverAddress;
    private String deliverContact;
    private String deliverPhone;

    private String goodsDesc;
    private Integer goodsWeightG;
    /** 金额（分） */
    private Integer amountFen;
    /** 金额（元），前端展示用 */
    private String amountYuan;
    private Integer distanceM;

    private Long riderId;

    private LocalDateTime expectDeliverAt;
    private LocalDateTime acceptedAt;
    private LocalDateTime pickedUpAt;
    private LocalDateTime deliveredAt;
    private LocalDateTime cancelledAt;

    private Integer dispatchRound;
    private Boolean needManual;
    private String cancelReason;

    private LocalDateTime createdAt;

    public static OrderVO of(DeliveryOrder o) {
        OrderVO vo = new OrderVO();
        vo.orderNo = o.getOrderNo();
        vo.userId = o.getUserId();
        vo.status = o.getStatus();
        vo.statusDesc = OrderStatus.of(o.getStatus()).getDesc();

        vo.pickupLng = o.getPickupLng();
        vo.pickupLat = o.getPickupLat();
        vo.pickupAddress = o.getPickupAddress();
        vo.pickupContact = o.getPickupContact();
        vo.pickupPhone = o.getPickupPhone();

        vo.deliverLng = o.getDeliverLng();
        vo.deliverLat = o.getDeliverLat();
        vo.deliverAddress = o.getDeliverAddress();
        vo.deliverContact = o.getDeliverContact();
        vo.deliverPhone = o.getDeliverPhone();

        vo.goodsDesc = o.getGoodsDesc();
        vo.goodsWeightG = o.getGoodsWeightG();
        vo.amountFen = o.getAmountFen();
        vo.amountYuan = o.getAmountFen() == null ? null
                : BigDecimal.valueOf(o.getAmountFen(), 2).toPlainString();
        vo.distanceM = o.getDistanceM();

        vo.riderId = o.getRiderId();

        vo.expectDeliverAt = o.getExpectDeliverAt();
        vo.acceptedAt = o.getAcceptedAt();
        vo.pickedUpAt = o.getPickedUpAt();
        vo.deliveredAt = o.getDeliveredAt();
        vo.cancelledAt = o.getCancelledAt();

        vo.dispatchRound = o.getDispatchRound();
        vo.needManual = o.getNeedManual() != null && o.getNeedManual() == 1;
        vo.cancelReason = o.getCancelReason();
        vo.createdAt = o.getCreatedAt();
        return vo;
    }
}
