package com.delivery.notify.listener;

import com.delivery.common.constant.DeliveryConstants;
import com.delivery.notify.dto.PushMessage;
import com.delivery.notify.service.PushService;
import com.delivery.order.event.OrderStatusChangedEvent;
import com.delivery.rider.event.RiderLocationUpdatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * 事件监听：把业务事件转成 WebSocket 推送。
 *
 * <p>用 Spring 事件解耦的好处：order / rider 模块只管发布事件，
 * 完全不需要知道 notify 的存在，模块依赖方向保持单向。
 *
 * <p><b>注意</b>：监听器是同步执行的（在发布方的事务内），所以这里必须吞掉异常，
 * 否则推送失败会导致业务事务回滚。真实项目更稳妥的做法是用
 * {@code @TransactionalEventListener(phase = AFTER_COMMIT)} + 异步线程池。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PushEventListener {

    private final PushService pushService;
    private final StringRedisTemplate redis;

    /** 订单状态变化 → 推给正在查看该订单的用户 */
    @EventListener
    public void onOrderStatusChanged(OrderStatusChangedEvent event) {
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("orderNo", event.getOrderNo());
            data.put("status", event.getToStatus());
            data.put("fromStatus", event.getFromStatus());

            pushService.pushToOrder(event.getOrderNo(),
                    PushMessage.of("ORDER_STATUS", data));

            log.debug("已推送订单状态变更 orderNo={} -> {}",
                    event.getOrderNo(), event.getToStatus());
        } catch (Exception e) {
            // 推送失败不能影响下单/派单主流程
            log.warn("推送订单状态变更失败 orderNo={}", event.getOrderNo(), e);
        }
    }

    /** 骑手位置更新 → 推给正在查看该订单的用户 */
    @EventListener
    public void onRiderLocationUpdated(RiderLocationUpdatedEvent event) {
        // 骑手空闲时上报（没有关联订单），不需要推给任何人
        if (event.getOrderNo() == null || event.getOrderNo().isBlank()) {
            return;
        }
        // 位置推送限流：上报防抖是 2s，推送再按 3s 节流（按 订单+骑手 一个窗口），
        // 窗口内只放第一帧，避免用户端被高频位置消息刷屏（接上
        // LOCATION_PUSH_MIN_INTERVAL_MS 常量，此前定义了却没生效）。
        // 订单状态推送走 onOrderStatusChanged，不受此限流影响。
        String throttleKey = "delivery:push:loc:" + event.getOrderNo() + ":" + event.getRiderId();
        Boolean allowed = redis.opsForValue().setIfAbsent(throttleKey, "1",
                Duration.ofMillis(DeliveryConstants.LOCATION_PUSH_MIN_INTERVAL_MS));
        if (!Boolean.TRUE.equals(allowed)) {
            return;
        }
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("orderNo", event.getOrderNo());
            data.put("riderId", event.getRiderId());
            data.put("lng", event.getLng());
            data.put("lat", event.getLat());

            // 推送 key 必须和用户订阅的 key 一致（/ws/user/{orderNo}）
            pushService.pushToOrder(event.getOrderNo(),
                    PushMessage.of("RIDER_LOCATION", data));
        } catch (Exception e) {
            log.warn("推送骑手位置失败 riderId={}", event.getRiderId(), e);
        }
    }
}
