package com.delivery.dispatch.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.delivery.common.constant.DeliveryConstants;
import com.delivery.common.enums.DispatchTaskStatus;
import com.delivery.common.enums.OperatorType;
import com.delivery.common.enums.OrderEventType;
import com.delivery.common.enums.OrderStatus;
import com.delivery.common.exception.BizException;
import com.delivery.common.exception.ErrorCode;
import com.delivery.common.util.GeoUtil;
import com.delivery.dispatch.entity.DispatchTask;
import com.delivery.dispatch.mapper.DispatchTaskMapper;
import com.delivery.dispatch.service.DispatchService;
import com.delivery.order.dto.OrderVO;
import com.delivery.order.entity.DeliveryOrder;
import com.delivery.order.entity.OrderEvent;
import com.delivery.order.event.OrderCreatedEvent;
import com.delivery.order.mapper.DeliveryOrderMapper;
import com.delivery.order.mapper.OrderEventMapper;
import com.delivery.rider.entity.Rider;
import com.delivery.rider.mapper.RiderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 调度服务实现。
 *
 * F3：自动派单——下单后从在线骑手里按多维度打分选最优，生成派单任务。
 * F2：接单/取货/送达，骑手容量管理。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DispatchServiceImpl implements DispatchService {

    private final DeliveryOrderMapper orderMapper;
    private final OrderEventMapper eventMapper;
    private final RiderMapper riderMapper;
    private final DispatchTaskMapper taskMapper;

    // ==================================================================
    //  自动派单（F3）
    // ==================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long autoDispatch(Long orderId) {
        // 1. 查订单，确认状态是待派单
        DeliveryOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            log.warn("自动派单：订单不存在 orderId={}", orderId);
            return null;
        }
        if (order.statusEnum() != OrderStatus.PENDING_DISPATCH) {
            log.info("自动派单：订单状态不是待派单，跳过 orderNo={} status={}",
                    order.getOrderNo(), order.getStatus());
            return null;
        }

        // 2. 查所有在线骑手，过滤掉满负载和没有位置信息的
        List<Rider> candidates = riderMapper.selectOnlineRiders().stream()
                .filter(r -> r.getDeliveringCount() != null
                        && r.getMaxCapacity() != null
                        && r.getDeliveringCount() < r.getMaxCapacity())
                .filter(r -> r.getLastLng() != null && r.getLastLat() != null)
                .collect(Collectors.toList());

        if (candidates.isEmpty()) {
            log.info("自动派单：无可用候选骑手 orderNo={}", order.getOrderNo());
            return null;
        }

        // 3. 对每个候选骑手打分，选最高分
        Rider best = candidates.stream()
                .max(Comparator.comparingDouble(r -> scoreRider(r, order)))
                .orElse(null);

        if (best == null) {
            return null;
        }

        double score = scoreRider(best, order);

        // 4. 生成派单任务
        DispatchTask task = new DispatchTask();
        task.setOrderId(order.getId());
        task.setOrderNo(order.getOrderNo());
        task.setRound(1);
        task.setRiderId(best.getId());
        task.setCandidateRank(1);
        task.setCandidateScore(BigDecimal.valueOf(score));
        task.setStatus(DispatchTaskStatus.PENDING.name());
        task.setExpireAt(LocalDateTime.now()
                .plusSeconds(DeliveryConstants.DISPATCH_RESPONSE_TIMEOUT_SECONDS));
        task.setCreatedAt(LocalDateTime.now());
        taskMapper.insert(task);

        // 5. 订单状态：待派单 -> 派单中
        OrderStatus from = order.statusEnum();
        order.setStatus(OrderStatus.DISPATCHING.name());
        order.setDispatchRound(1);
        order.setDispatchedAt(LocalDateTime.now());
        orderMapper.updateById(order);

        // 6. 记录事件流水
        recordEvent(order.getId(), order.getOrderNo(), OrderEventType.DISPATCH_CREATED,
                from, OrderStatus.DISPATCHING, OperatorType.SYSTEM, null,
                String.format("自动派单给骑手 %s（%s），得分 %.4f",
                        best.getRiderNo(), best.getName(), score));

        log.info("自动派单成功 orderNo={} riderId={} riderNo={} score={}",
                order.getOrderNo(), best.getId(), best.getRiderNo(),
                String.format("%.4f", score));

        return best.getId();
    }

    /**
     * 给候选骑手打分，总分越高越优先。
     *
     * 五个维度（权重来自 DeliveryConstants）：
     *   距离 0.35 —— 骑手到取货点越近越好
     *   负载 0.20 —— 在手单越少越好
     *   顺路 0.25 —— 骑手→取货点→送货点方向越一致越好
     *   评分 0.10 —— 历史服务评分越高越好
     *   时效 0.10 —— F3 第一版简化为固定 1.0（后续用 ETA 粗算）
     */
    private double scoreRider(Rider rider, DeliveryOrder order) {
        double riderLng = rider.getLastLng().doubleValue();
        double riderLat = rider.getLastLat().doubleValue();
        double pickupLng = order.getPickupLng().doubleValue();
        double pickupLat = order.getPickupLat().doubleValue();
        double deliverLng = order.getDeliverLng().doubleValue();
        double deliverLat = order.getDeliverLat().doubleValue();

        // 距离分：1 / (距离km + 1)，0km=1分，1km=0.5分，3km=0.25分
        double distanceM = GeoUtil.distanceMeters(riderLng, riderLat, pickupLng, pickupLat);
        double distanceScore = 1.0 / (distanceM / 1000.0 + 1.0);

        // 负载分：(最大容量 - 在手单) / 最大容量
        double loadScore = (double) (rider.getMaxCapacity() - rider.getDeliveringCount())
                / rider.getMaxCapacity();

        // 评分分：评分 / 5.0
        double scoreScore = rider.getScore() == null ? 0.5
                : rider.getScore().doubleValue() / 5.0;

        // 顺路分：向量1=取货点-骑手位置，向量2=送货点-取货点
        // 余弦相似度高说明骑手朝取货点走的方向和取货→送货方向一致
        double dx1 = pickupLng - riderLng;
        double dy1 = pickupLat - riderLat;
        double dx2 = deliverLng - pickupLng;
        double dy2 = deliverLat - pickupLat;
        double cosine = GeoUtil.cosineSimilarity(dx1, dy1, dx2, dy2);
        double routeScore = GeoUtil.normalizeCosine(cosine);

        // 时效分：F3 第一版简化为固定值
        double timeScore = 1.0;

        return distanceScore * DeliveryConstants.WEIGHT_DISTANCE
                + loadScore * DeliveryConstants.WEIGHT_LOAD
                + routeScore * DeliveryConstants.WEIGHT_ROUTE_MATCH
                + scoreScore * DeliveryConstants.WEIGHT_SCORE
                + timeScore * DeliveryConstants.WEIGHT_TIME_RISK;
    }

    // ==================================================================
    //  事件监听：下单后自动派单
    // ==================================================================

    /**
     * 监听订单创建事件，在事务提交后触发自动派单。
     *
     * 为什么用 AFTER_COMMIT：
     *   下单和派单不在同一个事务里——派单失败不应该让已经创建成功的订单回滚。
     *   订单先落库提交，然后再尝试派单；派单失败订单保持「待派单」状态，
     *   后续可以由超时扫描任务或后台人工处理兜底。
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderCreated(OrderCreatedEvent event) {
        try {
            autoDispatch(event.getOrderId());
        } catch (Exception e) {
            // 派单异常不能影响主流程，记日志即可
            log.error("自动派单异常 orderId={}", event.getOrderId(), e);
        }
    }

    // ==================================================================
    //  查询
    // ==================================================================

    @Override
    public List<OrderVO> pendingOrders() {
        // F3 自动派单后订单是 DISPATCHING 状态，骑手端也能看到
        List<DeliveryOrder> orders = orderMapper.selectList(
                new LambdaQueryWrapper<DeliveryOrder>()
                        .in(DeliveryOrder::getStatus,
                                OrderStatus.PENDING_DISPATCH.name(),
                                OrderStatus.DISPATCHING.name())
                        .orderByDesc(DeliveryOrder::getCreatedAt)
        );
        return orders.stream().map(OrderVO::of).collect(Collectors.toList());
    }

    @Override
    public List<OrderVO> currentOrders(Long riderId) {
        List<DeliveryOrder> orders = orderMapper.selectList(
                new LambdaQueryWrapper<DeliveryOrder>()
                        .eq(DeliveryOrder::getRiderId, riderId)
                        .in(DeliveryOrder::getStatus,
                                OrderStatus.PENDING_PICKUP.name(),
                                OrderStatus.DELIVERING.name())
                        .orderByDesc(DeliveryOrder::getCreatedAt)
        );
        return orders.stream().map(OrderVO::of).collect(Collectors.toList());
    }

    // ==================================================================
    //  接单 / 取货 / 送达
    // ==================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderVO accept(Long riderId, String orderNo) {
        // 1. 查订单，确认状态是待派单或派单中
        DeliveryOrder order = orderMapper.selectByOrderNo(orderNo);
        if (order == null) {
            throw new BizException(ErrorCode.ORDER_NOT_FOUND, "orderNo=" + orderNo);
        }
        if (order.statusEnum() != OrderStatus.PENDING_DISPATCH
                && order.statusEnum() != OrderStatus.DISPATCHING) {
            throw new BizException(ErrorCode.ORDER_ALREADY_TAKEN, "订单已被其他骑手接走");
        }

        // 2. 查骑手，确认在线
        Rider rider = riderMapper.selectById(riderId);
        if (rider == null) {
            throw new BizException(ErrorCode.RIDER_NOT_FOUND, "riderId=" + riderId);
        }
        if (rider.getOnlineStatus() == null || rider.getOnlineStatus() != 1) {
            throw new BizException(ErrorCode.RIDER_OFFLINE, "骑手当前不在线");
        }

        // 3. 骑手容量+1（数据库层面检查容量，影响行数为0说明已满）
        int updated = riderMapper.increaseLoad(riderId);
        if (updated == 0) {
            throw new BizException(ErrorCode.RIDER_CAPACITY_FULL, "骑手已达接单上限");
        }

        // 4. 更新订单状态和骑手
        OrderStatus from = order.statusEnum();
        order.setStatus(OrderStatus.PENDING_PICKUP.name());
        order.setRiderId(riderId);
        order.setAcceptedAt(LocalDateTime.now());
        orderMapper.updateById(order);

        // 5. 记录事件流水
        recordEvent(order.getId(), orderNo, OrderEventType.DISPATCH_ACCEPTED,
                from, OrderStatus.PENDING_PICKUP, OperatorType.RIDER, riderId, null);

        log.info("骑手接单 riderId={} orderNo={}", riderId, orderNo);
        return OrderVO.of(orderMapper.selectById(order.getId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderVO pickup(Long riderId, String orderNo) {
        DeliveryOrder order = requireOrderAndCheckRider(orderNo, riderId);

        if (order.statusEnum() != OrderStatus.PENDING_PICKUP) {
            throw new BizException(ErrorCode.ILLEGAL_TRANSITION,
                    "当前状态【" + order.statusEnum().getDesc() + "】不允许取货");
        }

        OrderStatus from = order.statusEnum();
        order.setStatus(OrderStatus.DELIVERING.name());
        order.setPickedUpAt(LocalDateTime.now());
        orderMapper.updateById(order);

        recordEvent(order.getId(), orderNo, OrderEventType.RIDER_PICKED_UP,
                from, OrderStatus.DELIVERING, OperatorType.RIDER, riderId, null);

        log.info("骑手取货 riderId={} orderNo={}", riderId, orderNo);
        return OrderVO.of(orderMapper.selectById(order.getId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderVO delivered(Long riderId, String orderNo) {
        DeliveryOrder order = requireOrderAndCheckRider(orderNo, riderId);

        if (order.statusEnum() != OrderStatus.DELIVERING) {
            throw new BizException(ErrorCode.ILLEGAL_TRANSITION,
                    "当前状态【" + order.statusEnum().getDesc() + "】不允许送达");
        }

        OrderStatus from = order.statusEnum();
        order.setStatus(OrderStatus.DELIVERED.name());
        order.setDeliveredAt(LocalDateTime.now());
        orderMapper.updateById(order);

        // 释放骑手容量
        riderMapper.decreaseLoad(riderId);

        recordEvent(order.getId(), orderNo, OrderEventType.RIDER_DELIVERED,
                from, OrderStatus.DELIVERED, OperatorType.RIDER, riderId, null);

        log.info("骑手送达 riderId={} orderNo={}", riderId, orderNo);
        return OrderVO.of(orderMapper.selectById(order.getId()));
    }

    // ==================== 私有方法 ====================

    /** 查订单并校验骑手归属 */
    private DeliveryOrder requireOrderAndCheckRider(String orderNo, Long riderId) {
        DeliveryOrder order = orderMapper.selectByOrderNo(orderNo);
        if (order == null) {
            throw new BizException(ErrorCode.ORDER_NOT_FOUND, "orderNo=" + orderNo);
        }
        if (order.getRiderId() == null || !order.getRiderId().equals(riderId)) {
            throw new BizException(ErrorCode.RIDER_NOT_OWNER, "该订单不属于当前骑手");
        }
        return order;
    }

    /** 记录订单事件流水 */
    private void recordEvent(Long orderId, String orderNo,
                             OrderEventType eventType,
                             OrderStatus fromStatus, OrderStatus toStatus,
                             OperatorType operatorType, Long operatorId,
                             String remark) {
        OrderEvent event = new OrderEvent();
        event.setOrderId(orderId);
        event.setOrderNo(orderNo);
        event.setEventType(eventType.name());
        event.setFromStatus(fromStatus == null ? null : fromStatus.name());
        event.setToStatus(toStatus == null ? null : toStatus.name());
        event.setOperatorType(operatorType.name());
        event.setOperatorId(operatorId);
        event.setRemark(remark);
        eventMapper.insert(event);
    }
}
