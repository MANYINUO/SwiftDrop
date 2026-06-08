package com.delivery.dispatch.listener;

import com.delivery.common.config.DeliveryProperties;
import com.delivery.dispatch.service.DispatchService;
import com.delivery.order.event.OrderCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 下单后自动触发派单。
 *
 * <p>用 {@code AFTER_COMMIT} 而不是普通 {@code @EventListener}：
 * 订单事务还没提交就开始派单，会读到「不存在」的订单；
 * 而且派单失败会把下单一起回滚 —— 这不是我们想要的。
 *
 * <p>这里派单失败只记日志、不回滚下单：订单已经收下了，
 * 派不出去应该由调度重试/人工兜底，而不是让用户下单失败。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCreatedListener {

    private final DispatchService dispatchService;
    private final DeliveryProperties properties;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderCreated(OrderCreatedEvent event) {
        if (!properties.getDispatch().isAutoDispatchEnabled()) {
            log.info("自动派单已关闭，订单 {} 等待后台手动派单", event.getOrderNo());
            return;
        }
        try {
            dispatchService.dispatchOrder(event.getOrderId(), 1);
        } catch (Exception e) {
            // 下单已经成功，派单失败不能回滚订单；交给后台人工调度兜底
            log.error("自动派单失败，订单 {} 需后台人工处理", event.getOrderNo(), e);
        }
    }
}
