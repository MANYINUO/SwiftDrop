package com.delivery.order.event;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 订单创建事件。
 *
 * <p>order 模块不依赖 dispatch，所以下单后不能直接调用派单逻辑。
 * 改为发布事件，由 dispatch 模块监听并在<b>事务提交后</b>触发派单 ——
 * 这样既保持模块单向依赖，又避免「事务还没提交就开始派单」导致的脏读。
 */
@Getter
@AllArgsConstructor
public class OrderCreatedEvent {

    private final Long orderId;
    private final String orderNo;
}
