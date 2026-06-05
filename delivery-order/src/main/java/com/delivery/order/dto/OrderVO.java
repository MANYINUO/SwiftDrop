package com.delivery.order.dto;

import com.delivery.common.enums.OrderStatus;
import com.delivery.order.entity.DeliveryOrder;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单视图对象（VO）：返回给前端的数据结构
 * 与实体 DeliveryOrder 的区别：VO 只挑前端需要的字段，
 * 并额外派生 statusDesc（状态中文名）和 amountYuan（把「分」换算成「元」）
 * 数据库内部一律用「分」存整数避免浮点误差，换算成元只发生在出参这一层
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
    private Integer amountFen;    // 金额（分）
    private String amountYuan;   // 金额（元），前端展示用
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

    /**
     * 把实体转成 VO
     */
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
        // 分转元：BigDecimal.valueOf(金额, 2) 表示小数点左移2位
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
