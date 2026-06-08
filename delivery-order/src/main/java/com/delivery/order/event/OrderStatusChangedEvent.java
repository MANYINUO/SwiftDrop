package com.delivery.order.event;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 订单状态变更事件。
 *
 * <p>由 {@code OrderStateMachine} 在状态流转成功后发布，notify 模块监听后通过 WebSocket
 * 推给正在查看该订单的用户。
 *
 * <p>刻意只带「订单号 + 新状态」这类轻量信息：前端收到推送后再调
 * {@code GET /api/user/orders/{orderNo}/track} 拉全量。这样 notify 模块不需要依赖
 * order 模块，模块边界更干净。
 */
@Getter
@AllArgsConstructor
public class OrderStatusChangedEvent {

    private final Long orderId;
    private final String orderNo;
    private final String fromStatus;
    private final String toStatus;
}
