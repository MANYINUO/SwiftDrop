package com.delivery.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.delivery.common.constant.DeliveryConstants;
import com.delivery.common.enums.OperatorType;
import com.delivery.common.enums.OrderEventType;
import com.delivery.common.enums.OrderStatus;
import com.delivery.common.exception.BizException;
import com.delivery.common.exception.ErrorCode;
import com.delivery.common.geo.RiderGeoPoint;
import com.delivery.common.geo.RiderGeoRepository;
import com.delivery.common.result.PageResult;
import com.delivery.common.util.GeoUtil;
import com.delivery.order.dto.CreateOrderRequest;
import com.delivery.order.dto.OrderTrackVO;
import com.delivery.order.dto.OrderVO;
import com.delivery.order.entity.DeliveryOrder;
import com.delivery.order.entity.OrderEvent;
import com.delivery.order.event.OrderCreatedEvent;
import com.delivery.order.mapper.DeliveryOrderMapper;
import com.delivery.order.mapper.OrderEventMapper;
import com.delivery.order.service.OrderService;
import com.delivery.order.statemachine.OrderStateMachine;
import com.delivery.order.util.OrderNoGenerator;
import com.delivery.rider.entity.Rider;
import com.delivery.rider.entity.RiderLocationLog;
import com.delivery.rider.mapper.RiderLocationLogMapper;
import com.delivery.rider.mapper.RiderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final DeliveryOrderMapper orderMapper;
    private final OrderEventMapper eventMapper;
    private final OrderStateMachine stateMachine;
    private final RiderMapper riderMapper;
    private final RiderLocationLogMapper locationLogMapper;
    private final RiderGeoRepository geoRepository;
    private final StringRedisTemplate redis;
    private final ApplicationEventPublisher eventPublisher;

    /** 起步价（分）：8 元，含 3 公里 */
    private static final int BASE_AMOUNT_FEN = 800;
    private static final int INCLUDED_METERS = 3000;
    /** 超出部分每公里（分） */
    private static final int PER_KM_FEN = 200;

    /** 轨迹最多返回多少个点，避免响应体过大 */
    private static final int TRAIL_LIMIT = 200;

    // ==================================================================
    //  创建订单
    // ==================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderVO createOrder(Long userId, String idempotencyKey, CreateOrderRequest req) {
        String idemKey = DeliveryConstants.REDIS_KEY_IDEMPOTENT_PREFIX + "ORDER_CREATE:"
                + userId + ":" + idempotencyKey;

        // 幂等占位：SETNX 必须是「先于业务执行」的第一步，否则并发下会双写
        Boolean first = redis.opsForValue().setIfAbsent(idemKey, "PROCESSING",
                Duration.ofSeconds(DeliveryConstants.IDEMPOTENT_TTL_SECONDS));

        if (!Boolean.TRUE.equals(first)) {
            OrderVO existing = tryReturnExistingOrder(idemKey);
            if (existing != null) {
                return existing;
            }
            // 走到这里说明幂等记录指向的订单已经不存在了（典型场景：演示环境清了库，
            // 但 Redis 里的幂等键还没过期）。这时候如果直接报错，用户会看到一个
            // 莫名其妙的「订单不存在」；正确做法是把失效的幂等键清掉，当成新请求处理。
            log.warn("幂等记录已失效（对应订单不存在），清理后重新创建 idemKey={}", idemKey);
            redis.delete(idemKey);
            Boolean retry = redis.opsForValue().setIfAbsent(idemKey, "PROCESSING",
                    Duration.ofSeconds(DeliveryConstants.IDEMPOTENT_TTL_SECONDS));
            if (!Boolean.TRUE.equals(retry)) {
                // 有别的线程抢先了，让它去创建，本次直接返回重复请求
                throw new BizException(ErrorCode.DUPLICATE_REQUEST);
            }
        }

        try {
            OrderVO vo = doCreate(userId, req);
            // 把订单号写回幂等键，后续重复请求直接返回这笔订单
            redis.opsForValue().set(idemKey, vo.getOrderNo(),
                    Duration.ofSeconds(DeliveryConstants.IDEMPOTENT_TTL_SECONDS));
            return vo;
        } catch (Exception e) {
            // 创建失败则释放幂等键，允许客户端重试
            redis.delete(idemKey);
            throw e;
        }
    }

    /**
     * 尝试返回幂等键对应的已存在订单。
     *
     * @return 订单不存在（或首次请求仍在处理中）时返回 null
     */
    private OrderVO tryReturnExistingOrder(String idemKey) {
        for (int i = 0; i < 10; i++) {
            String value = redis.opsForValue().get(idemKey);
            if (value == null) {
                // 键已过期，当作新请求
                return null;
            }
            if (!"PROCESSING".equals(value)) {
                DeliveryOrder order = orderMapper.selectByOrderNo(value);
                if (order == null) {
                    // 幂等键还在，但订单没了 —— 交给调用方清理
                    return null;
                }
                log.info("幂等命中，返回首次创建的订单 orderNo={}", value);
                return OrderVO.of(order);
            }
            // 首次请求还在处理中，短暂自旋等它写完订单号
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        // 一直处于 PROCESSING：可能是并发重复提交，按重复请求处理
        throw new BizException(ErrorCode.DUPLICATE_REQUEST);
    }

    private OrderVO doCreate(Long userId, CreateOrderRequest req) {
        CreateOrderRequest.Address p = req.getPickup();
        CreateOrderRequest.Address d = req.getDeliver();

        double distance = GeoUtil.distanceMeters(
                p.getLng().doubleValue(), p.getLat().doubleValue(),
                d.getLng().doubleValue(), d.getLat().doubleValue());

        int amountFen = calcAmountFen(distance);
        int etaSeconds = GeoUtil.estimateSeconds(distance,
                DeliveryConstants.AVG_DELIVERY_SPEED_MPS,
                DeliveryConstants.PICKUP_FIXED_SECONDS);

        DeliveryOrder order = new DeliveryOrder();
        order.setOrderNo(OrderNoGenerator.next());
        order.setUserId(userId);
        order.setStatus(OrderStatus.PENDING_DISPATCH.name());

        order.setPickupLng(p.getLng());
        order.setPickupLat(p.getLat());
        order.setPickupAddress(p.getAddress());
        order.setPickupContact(p.getContact());
        order.setPickupPhone(p.getPhone());

        order.setDeliverLng(d.getLng());
        order.setDeliverLat(d.getLat());
        order.setDeliverAddress(d.getAddress());
        order.setDeliverContact(d.getContact());
        order.setDeliverPhone(d.getPhone());

        order.setGoodsDesc(req.getGoodsDesc());
        order.setGoodsWeightG(req.getGoodsWeightG());
        order.setAmountFen(amountFen);
        order.setDistanceM((int) Math.round(distance));
        order.setExpectDeliverAt(LocalDateTime.now().plusSeconds(etaSeconds));
        order.setDispatchRound(0);
        order.setNeedManual(0);
        order.setVersion(0);

        orderMapper.insert(order);

        stateMachine.recordEvent(order.getId(), order.getOrderNo(),
                OrderEventType.ORDER_CREATED, null, OrderStatus.PENDING_DISPATCH,
                OperatorType.USER, userId,
                String.format("下单成功，距离 %d 米，预估 %d 分钟", Math.round(distance), etaSeconds / 60));

        // 发布订单创建事件：由 dispatch 模块在事务提交后触发自动派单
        eventPublisher.publishEvent(new OrderCreatedEvent(order.getId(), order.getOrderNo()));

        log.info("订单创建成功 orderNo={} 距离={}米 金额={}分", order.getOrderNo(),
                Math.round(distance), amountFen);
        return OrderVO.of(order);
    }

    /** 计价：起步价含 3 公里，超出部分按公里累加 */
    private int calcAmountFen(double distanceMeters) {
        if (distanceMeters <= INCLUDED_METERS) {
            return BASE_AMOUNT_FEN;
        }
        double extraKm = (distanceMeters - INCLUDED_METERS) / 1000.0;
        return BASE_AMOUNT_FEN + (int) Math.ceil(extraKm * PER_KM_FEN);
    }

    // ==================================================================
    //  查询
    // ==================================================================

    @Override
    public OrderVO getByOrderNo(String orderNo) {
        return OrderVO.of(getEntityByOrderNo(orderNo));
    }

    @Override
    public DeliveryOrder getEntityByOrderNo(String orderNo) {
        DeliveryOrder order = orderMapper.selectByOrderNo(orderNo);
        if (order == null) {
            throw new BizException(ErrorCode.ORDER_NOT_FOUND, "orderNo=" + orderNo);
        }
        return order;
    }

    @Override
    public OrderVO getOrderForUser(String orderNo, Long userId) {
        DeliveryOrder order = getEntityByOrderNo(orderNo);
        assertOrderOwner(order, userId);
        return OrderVO.of(order);
    }

    /**
     * 用户态归属校验：订单不属于当前用户时统一按「不存在」处理。
     * 故意不抛 403，否则接口会变成「订单号是否存在」的探测器。
     */
    private void assertOrderOwner(DeliveryOrder order, Long userId) {
        if (userId == null || !userId.equals(order.getUserId())) {
            throw new BizException(ErrorCode.ORDER_NOT_FOUND, "orderNo=" + order.getOrderNo());
        }
    }

    @Override
    public PageResult<OrderVO> pageByUser(Long userId, String status, long pageNum, long pageSize) {
        LambdaQueryWrapper<DeliveryOrder> w = Wrappers.<DeliveryOrder>lambdaQuery()
                .eq(DeliveryOrder::getUserId, userId)
                .eq(status != null && !status.isBlank(), DeliveryOrder::getStatus, status)
                .orderByDesc(DeliveryOrder::getId);

        IPage<DeliveryOrder> page = orderMapper.selectPage(new Page<>(pageNum, pageSize), w);
        List<OrderVO> records = page.getRecords().stream()
                .map(OrderVO::of)
                .collect(Collectors.toList());
        return PageResult.of(records, page.getTotal(), pageNum, pageSize);
    }

    @Override
    public List<OrderEvent> events(String orderNo) {
        DeliveryOrder order = getEntityByOrderNo(orderNo);
        return eventMapper.listByOrderId(order.getId());
    }

    @Override
    public List<OrderEvent> eventsForUser(String orderNo, Long userId) {
        DeliveryOrder order = getEntityByOrderNo(orderNo);
        assertOrderOwner(order, userId);
        return eventMapper.listByOrderId(order.getId());
    }

    // ==================================================================
    //  取消
    // ==================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderVO cancel(String orderNo, Long userId, String reason) {
        DeliveryOrder order = getEntityByOrderNo(orderNo);

        if (!order.getUserId().equals(userId)) {
            throw new BizException(ErrorCode.ORDER_NOT_FOUND, "订单不属于当前用户");
        }

        OrderStatus from = order.statusEnum();
        // 第一层校验：状态机本身就会拦住「配送中取消」「已送达取消」
        if (!stateMachine.canTransit(from, OrderStatus.CANCELLED)) {
            throw new BizException(ErrorCode.ORDER_CANNOT_CANCEL,
                    "当前状态【" + from.getDesc() + "】不允许取消");
        }

        // 第二层：CAS 落库
        stateMachine.transit(order.getId(), orderNo, from, OrderStatus.CANCELLED,
                OperatorType.USER, userId, reason,
                w -> w.set(DeliveryOrder::getCancelledAt, LocalDateTime.now())
                        .set(DeliveryOrder::getCancelReason, reason),
                OrderEventType.ORDER_CANCELLED);

        // 若已分配骑手（待取货阶段取消），需要释放骑手容量
        releaseCapacityIfPresent(order, "订单取消");

        return getByOrderNo(orderNo);
    }

    // ==================================================================
    //  后台异常处理
    // ==================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderVO markException(String orderNo, Long adminId, String remark) {
        DeliveryOrder order = getEntityByOrderNo(orderNo);
        OrderStatus from = order.statusEnum();

        if (!stateMachine.canTransit(from, OrderStatus.EXCEPTION)) {
            throw new BizException(ErrorCode.ILLEGAL_TRANSITION,
                    "当前状态【" + from.getDesc() + "】不能标记为异常");
        }

        stateMachine.transit(order.getId(), orderNo, from, OrderStatus.EXCEPTION,
                OperatorType.ADMIN, adminId,
                remark == null ? "后台标记异常" : remark,
                null, OrderEventType.ADMIN_EXCEPTION);

        // 只有骑手已接单（待取货/配送中）才占用容量；待派单/派单中 riderId 为空，不释放
        releaseCapacityIfPresent(order, "标记异常");

        return getByOrderNo(orderNo);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderVO resolveException(String orderNo, Long adminId, String action, String reason) {
        DeliveryOrder order = getEntityByOrderNo(orderNo);
        if (order.statusEnum() != OrderStatus.EXCEPTION) {
            throw new BizException(ErrorCode.ILLEGAL_TRANSITION,
                    "只有异常订单需要处理，当前状态【" + order.statusEnum().getDesc() + "】");
        }

        if ("REASSIGN".equalsIgnoreCase(action)) {
            // 异常 -> 待派单，并解绑原骑手，否则新骑手接单会被「订单已被接走」拦住。
            // 容量在标记异常时已释放，这里不重复释放。
            stateMachine.transit(order.getId(), orderNo,
                    OrderStatus.EXCEPTION, OrderStatus.PENDING_DISPATCH,
                    OperatorType.ADMIN, adminId,
                    reason == null ? "后台改派" : reason,
                    w -> w.set(DeliveryOrder::getRiderId, null)
                            .set(DeliveryOrder::getNeedManual, 0),
                    OrderEventType.ADMIN_RESOLVED);
        } else if ("CANCEL".equalsIgnoreCase(action)) {
            // 异常 -> 已取消；容量在标记异常时已释放，这里不重复释放
            stateMachine.transit(order.getId(), orderNo,
                    OrderStatus.EXCEPTION, OrderStatus.CANCELLED,
                    OperatorType.ADMIN, adminId,
                    reason == null ? "后台取消异常订单" : reason,
                    w -> w.set(DeliveryOrder::getCancelledAt, LocalDateTime.now())
                            .set(DeliveryOrder::getCancelReason, reason),
                    OrderEventType.ADMIN_RESOLVED);
        } else {
            throw new BizException(ErrorCode.PARAM_INVALID, "action 只能是 REASSIGN 或 CANCEL");
        }

        return getByOrderNo(orderNo);
    }

    /**
     * 释放订单当前占用的骑手容量（幂等：riderId 为空说明本单从未占用容量，直接跳过）。
     *
     * <p>容量与「骑手已接单」严格配对：接单时 {@code increaseLoad}（DispatchServiceImpl.accept），
     * 任何让订单离开「待取货/配送中」的终局路径（送达 / 取消 / 标记异常）都必须且只能
     * 调一次本方法。decreaseLoad 的 SQL 已用 GREATEST 防止减成负数，但计数器本身无法
     * 区分订单，因此「只释放一次」由状态机的一次性流转在业务层保证。
     */
    private void releaseCapacityIfPresent(DeliveryOrder order, String scene) {
        if (order.getRiderId() != null) {
            riderMapper.decreaseLoad(order.getRiderId());
            log.info("{}，释放骑手容量 riderId={} orderNo={}",
                    scene, order.getRiderId(), order.getOrderNo());
        }
    }

    // ==================================================================
    //  轨迹
    // ==================================================================

    @Override
    public OrderTrackVO track(String orderNo) {
        return buildTrack(getEntityByOrderNo(orderNo));
    }

    @Override
    public OrderTrackVO trackForUser(String orderNo, Long userId) {
        DeliveryOrder order = getEntityByOrderNo(orderNo);
        assertOrderOwner(order, userId);
        return buildTrack(order);
    }

    private OrderTrackVO buildTrack(DeliveryOrder order) {
        OrderTrackVO vo = new OrderTrackVO();
        vo.setOrderNo(order.getOrderNo());
        vo.setStatus(order.getStatus());
        vo.setStatusDesc(OrderStatus.of(order.getStatus()).getDesc());
        vo.setExpectDeliverAt(order.getExpectDeliverAt());
        vo.setRiderId(order.getRiderId());

        if (order.getRiderId() != null) {
            vo.setRiderAssigned(true);

            Rider rider = riderMapper.selectById(order.getRiderId());
            if (rider != null) {
                vo.setRiderNo(rider.getRiderNo());
                vo.setRiderName(rider.getName());
                vo.setRiderPhone(rider.getPhone());
                vo.setLocationUpdatedAt(rider.getLastReportAt());
            }

            // 实时位置以 Redis GEO 为准（数据库里的是冷备）
            RiderGeoPoint point = geoRepository.getPosition(order.getRiderId());
            if (point != null) {
                vo.setLng(BigDecimal.valueOf(point.getLng()));
                vo.setLat(BigDecimal.valueOf(point.getLat()));
            }

            // 历史轨迹：进页面就能画出骑手走过的路线，不用等实时推送累积
            List<RiderLocationLog> logs = locationLogMapper.listByOrderNo(
                    order.getOrderNo(), TRAIL_LIMIT);
            vo.setTrail(logs.stream()
                    .map(l -> new OrderTrackVO.TrailPoint(
                            l.getLng().doubleValue(), l.getLat().doubleValue()))
                    .collect(Collectors.toList()));
        }

        return vo;
    }
}
