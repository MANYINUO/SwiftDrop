package com.delivery.order.statemachine;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.delivery.common.enums.OperatorType;
import com.delivery.common.enums.OrderEventType;
import com.delivery.common.enums.OrderStatus;
import com.delivery.common.exception.BizException;
import com.delivery.common.exception.ErrorCode;
import com.delivery.order.entity.DeliveryOrder;
import com.delivery.order.entity.OrderEvent;
import com.delivery.order.event.OrderStatusChangedEvent;
import com.delivery.order.mapper.DeliveryOrderMapper;
import com.delivery.order.mapper.OrderEventMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * 订单状态机 —— 整个项目的地基。
 *
 * <p><b>设计要点：两层防护</b>
 * <ol>
 *   <li><b>第一层：合法性校验</b>（内存中的 {@link #ALLOWED} 转移表）
 *       挡住「已送达又变回配送中」这类非法流转，快速失败并给出清晰错误。</li>
 *   <li><b>第二层：CAS 落库</b>（{@code WHERE status = 原状态}）
 *       挡住<b>并发</b>——两个请求同时改同一订单时，只有一个能成功。
 *       合法性校验挡不住并发，这一层才是关键。</li>
 * </ol>
 *
 * <p>约定：<b>任何业务代码都不允许直接 update status</b>，必须走本类。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderStateMachine {

    private final DeliveryOrderMapper orderMapper;
    private final OrderEventMapper eventMapper;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 允许的状态转移表。
     *
     * <p>两条铁律：
     * <ul>
     *   <li>除「派单失败回到待派单」外，状态只前进不后退</li>
     *   <li>配送中不允许直接取消（骑手已在路上），只能走异常或等送达</li>
     * </ul>
     */
    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED = new EnumMap<>(OrderStatus.class);

    static {
        ALLOWED.put(OrderStatus.PENDING_DISPATCH,
                EnumSet.of(OrderStatus.DISPATCHING, OrderStatus.CANCELLED, OrderStatus.EXCEPTION));

        // 回到 PENDING_DISPATCH = 骑手拒单 / 超时重派
        ALLOWED.put(OrderStatus.DISPATCHING,
                EnumSet.of(OrderStatus.PENDING_PICKUP, OrderStatus.PENDING_DISPATCH,
                        OrderStatus.CANCELLED, OrderStatus.EXCEPTION));

        // 回到 PENDING_DISPATCH = 骑手接单后又取消（尚未取货）
        ALLOWED.put(OrderStatus.PENDING_PICKUP,
                EnumSet.of(OrderStatus.DELIVERING, OrderStatus.PENDING_DISPATCH,
                        OrderStatus.CANCELLED, OrderStatus.EXCEPTION));

        ALLOWED.put(OrderStatus.DELIVERING,
                EnumSet.of(OrderStatus.DELIVERED, OrderStatus.EXCEPTION));

        // 终态：不允许任何流转
        ALLOWED.put(OrderStatus.DELIVERED, EnumSet.noneOf(OrderStatus.class));
        ALLOWED.put(OrderStatus.CANCELLED, EnumSet.noneOf(OrderStatus.class));

        // 异常是非终态，必须能改派或取消
        ALLOWED.put(OrderStatus.EXCEPTION,
                EnumSet.of(OrderStatus.PENDING_DISPATCH, OrderStatus.CANCELLED));
    }

    /** 第一层：纯内存判断，不发 SQL */
    public boolean canTransit(OrderStatus from, OrderStatus to) {
        if (from == null || to == null) {
            return false;
        }
        return ALLOWED.getOrDefault(from, EnumSet.noneOf(OrderStatus.class)).contains(to);
    }

    /**
     * 执行状态流转（无附加字段）。
     */
    public void transit(Long orderId, String orderNo, OrderStatus from, OrderStatus to,
                        OperatorType operatorType, Long operatorId, String remark) {
        transit(orderId, orderNo, from, to, operatorType, operatorId, remark, null, OrderEventType.STATUS_CHANGED);
    }

    /**
     * 执行状态流转，并允许附加要一起更新的字段。
     *
     * @param customizer 用于追加 set 子句，例如
     *                   {@code w -> w.set(DeliveryOrder::getAcceptedAt, LocalDateTime.now())}
     */
    public void transit(Long orderId, String orderNo, OrderStatus from, OrderStatus to,
                        OperatorType operatorType, Long operatorId, String remark,
                        Consumer<LambdaUpdateWrapper<DeliveryOrder>> customizer,
                        OrderEventType eventType) {

        // ---------- 第一层：合法性校验 ----------
        if (!canTransit(from, to)) {
            throw new BizException(ErrorCode.ILLEGAL_TRANSITION,
                    String.format("%s(%s) -> %s(%s)", from.getDesc(), from, to.getDesc(), to));
        }

        // ---------- 第二层：CAS 落库 ----------
        LambdaUpdateWrapper<DeliveryOrder> w = Wrappers.<DeliveryOrder>lambdaUpdate()
                .eq(DeliveryOrder::getId, orderId)
                // 关键：只有当前状态仍是 from 才更新成功
                .eq(DeliveryOrder::getStatus, from.name())
                .set(DeliveryOrder::getStatus, to.name())
                .set(DeliveryOrder::getUpdatedAt, LocalDateTime.now())
                // 关键：版本号自增，任何基于旧版本的后续 CAS 都会失败
                .setSql("version = version + 1");

        if (customizer != null) {
            customizer.accept(w);
        }

        int rows = orderMapper.update(null, w);
        if (rows == 0) {
            // 说明在「读状态」和「写状态」之间，状态已经被别的请求改掉了
            log.warn("CAS 状态流转失败，订单已被并发修改 orderId={} expectFrom={} to={}",
                    orderId, from, to);
            throw new BizException(ErrorCode.CONCURRENT_MODIFIED);
        }

        // ---------- 写事件流水 ----------
        recordEvent(orderId, orderNo, eventType, from, to, operatorType, operatorId, remark);

        // ---------- 发布状态变更事件（notify 模块据此推送给用户）----------
        // 放在事务内发布：如果监听方抛异常会一起回滚，避免"状态改了但推送逻辑出错"的不一致。
        // 若希望推送失败不影响主流程，可改为 TransactionSynchronization 的 afterCommit 回调。
        eventPublisher.publishEvent(new OrderStatusChangedEvent(
                orderId, orderNo, from.name(), to.name()));

        log.info("订单状态流转成功 orderNo={} {} -> {}", orderNo, from, to);
    }

    /**
     * 仅记录事件，不改变状态（例如「生成派单任务」）。
     */
    public void recordEvent(Long orderId, String orderNo, OrderEventType eventType,
                            OrderStatus from, OrderStatus to,
                            OperatorType operatorType, Long operatorId, String remark) {
        OrderEvent event = new OrderEvent();
        event.setOrderId(orderId);
        event.setOrderNo(orderNo);
        event.setEventType(eventType.name());
        event.setFromStatus(from == null ? null : from.name());
        event.setToStatus(to == null ? null : to.name());
        event.setOperatorType(operatorType.name());
        event.setOperatorId(operatorId);
        event.setRemark(remark);
        event.setCreatedAt(LocalDateTime.now());
        eventMapper.insert(event);
    }

    /** 供后台展示转移表 */
    public Set<OrderStatus> allowedTargets(OrderStatus from) {
        return ALLOWED.getOrDefault(from, EnumSet.noneOf(OrderStatus.class));
    }
}
