package com.delivery.order.event;

/**
 * 订单创建事件。
 *
 * 下单成功后发布，调度模块监听此事件触发自动派单。
 * 用事件解耦：order 模块不依赖 dispatch 模块，避免循环依赖。
 * 事件里只带 orderId，监听器自己去查订单详情。
 */
public class OrderCreatedEvent {

    private final Long orderId;

    public OrderCreatedEvent(Long orderId) {
        this.orderId = orderId;
    }

    public Long getOrderId() {
        return orderId;
    }
}
