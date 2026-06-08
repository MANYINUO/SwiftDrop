package com.delivery.rider.event;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * 骑手位置更新事件。
 *
 * <p>用 Spring 事件解耦：rider 模块只管发布，notify 模块负责推送。
 * 这样 rider 不需要依赖 notify，模块依赖方向保持单向。
 */
@Getter
@AllArgsConstructor
public class RiderLocationUpdatedEvent {

    private final Long riderId;
    private final BigDecimal lng;
    private final BigDecimal lat;

    /**
     * 配送中的订单号，为空表示骑手空闲。
     *
     * <p><b>必须是 orderNo 而不是 orderId</b>：用户端 WebSocket 是按
     * {@code /ws/user/{orderNo}} 订阅的，推送 key 必须和订阅 key 一致，
     * 否则消息永远推不到用户（这个坑踩过）。
     */
    private final String orderNo;
}
