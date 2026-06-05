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
import com.delivery.common.result.PageResult;
import com.delivery.common.util.GeoUtil;
import com.delivery.order.dto.CreateOrderRequest;
import com.delivery.order.dto.OrderTrackVO;
import com.delivery.order.dto.OrderVO;
import com.delivery.order.entity.DeliveryOrder;
import com.delivery.order.entity.OrderEvent;
import com.delivery.order.mapper.DeliveryOrderMapper;
import com.delivery.order.mapper.OrderEventMapper;
import com.delivery.order.service.OrderService;
import com.delivery.order.util.OrderNoGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 订单服务实现（F1 简化版）
 * 包含：下单、查详情、查列表、取消、事件流水
 * 暂不包含：幂等、状态机、事件发布、骑手容量释放（后续阶段加）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final DeliveryOrderMapper orderMapper;
    private final OrderEventMapper eventMapper;

    // 计价规则：起步价 8 元含 3 公里，超出部分每公里 2 元
    private static final int BASE_AMOUNT_FEN = 800;
    private static final int INCLUDED_METERS = 3000;
    private static final int PER_KM_FEN = 200;

    // ==================================================================
    //  创建订单
    // ==================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderVO createOrder(Long userId, CreateOrderRequest req) {
        CreateOrderRequest.Address p = req.getPickup();
        CreateOrderRequest.Address d = req.getDeliver();

        // 1. 用 Haversine 公式算取件点到送达点的球面距离（米）
        double distance = GeoUtil.distanceMeters(
                p.getLng().doubleValue(), p.getLat().doubleValue(),
                d.getLng().doubleValue(), d.getLat().doubleValue());

        // 2. 计价：起步价含3公里，超出按公里累加
        int amountFen = calcAmountFen(distance);

        // 3. 粗算预计送达时间（秒）
        int etaSeconds = GeoUtil.estimateSeconds(distance,
                DeliveryConstants.AVG_DELIVERY_SPEED_MPS,
                DeliveryConstants.PICKUP_FIXED_SECONDS);

        // 4. 组装订单实体
        DeliveryOrder order = new DeliveryOrder();
        order.setOrderNo(OrderNoGenerator.next());
        order.setUserId(userId);
        order.setStatus(OrderStatus.PENDING_DISPATCH.name());  // 初始状态：待派单

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

        // 5. 插入订单（MyBatis-Plus insert 后会把自增 id 回填到 order.getId()）
        orderMapper.insert(order);

        // 6. 记录订单事件流水（和订单插入在同一个事务里）
        recordEvent(order.getId(), order.getOrderNo(),
                OrderEventType.ORDER_CREATED, null, OrderStatus.PENDING_DISPATCH,
                OperatorType.USER, userId,
                String.format("下单成功，距离 %d 米，预估 %d 分钟",
                        Math.round(distance), etaSeconds / 60));

        log.info("订单创建成功 orderNo={} 距离={}米 金额={}分",
                order.getOrderNo(), Math.round(distance), amountFen);
        return OrderVO.of(order);
    }

    /**
     * 计价：起步价含3公里，超出部分按公里累加
     */
    private int calcAmountFen(double distanceMeters) {
        if (distanceMeters <= INCLUDED_METERS) {
            return BASE_AMOUNT_FEN;
        }
        double extraKm = (distanceMeters - INCLUDED_METERS) / 1000.0;
        // ceil 向上取整：超出 3.1 公里按 4 公里算（对平台有利）
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
     * 归属校验：订单不属于当前用户时统一按「不存在」处理
     * 故意不抛 403，否则接口会变成「订单号是否存在」的探测器
     */
    private void assertOrderOwner(DeliveryOrder order, Long userId) {
        if (userId == null || !userId.equals(order.getUserId())) {
            throw new BizException(ErrorCode.ORDER_NOT_FOUND, "orderNo=" + order.getOrderNo());
        }
    }

    @Override
    public PageResult<OrderVO> pageByUser(Long userId, String status, long pageNum, long pageSize) {
        // 构造查询条件：按用户ID + 可选状态筛选，按 id 倒序（最新的在前）
        LambdaQueryWrapper<DeliveryOrder> w = Wrappers.<DeliveryOrder>lambdaQuery()
                .eq(DeliveryOrder::getUserId, userId)
                .eq(status != null && !status.isBlank(), DeliveryOrder::getStatus, status)
                .orderByDesc(DeliveryOrder::getId);

        // MyBatis-Plus 分页查询（需要 MybatisPlusConfig 里注册的分页插件才会真正分页）
        IPage<DeliveryOrder> page = orderMapper.selectPage(new Page<>(pageNum, pageSize), w);

        // 把实体列表转成 VO 列表
        List<OrderVO> records = page.getRecords().stream()
                .map(OrderVO::of)
                .collect(Collectors.toList());

        return PageResult.of(records, page.getTotal(), pageNum, pageSize);
    }

    @Override
    public List<OrderEvent> eventsForUser(String orderNo, Long userId) {
        DeliveryOrder order = getEntityByOrderNo(orderNo);
        assertOrderOwner(order, userId);
        return eventMapper.listByOrderId(order.getId());
    }

    // ==================================================================
    //  取消订单
    // ==================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderVO cancel(String orderNo, Long userId, String reason) {
        DeliveryOrder order = getEntityByOrderNo(orderNo);

        // 1. 归属校验
        if (!order.getUserId().equals(userId)) {
            throw new BizException(ErrorCode.ORDER_NOT_FOUND, "订单不属于当前用户");
        }

        // 2. 状态校验：只有待派单、派单中、待取货可以取消
        //    配送中、已送达、已取消、异常 都不允许用户取消
        OrderStatus from = order.statusEnum();
        if (from != OrderStatus.PENDING_DISPATCH
                && from != OrderStatus.DISPATCHING
                && from != OrderStatus.PENDING_PICKUP) {
            throw new BizException(ErrorCode.ORDER_CANNOT_CANCEL,
                    "当前状态【" + from.getDesc() + "】不允许取消");
        }

        // 3. 更新订单状态为已取消（用 updateById 会自动更新 updatedAt）
        order.setStatus(OrderStatus.CANCELLED.name());
        order.setCancelledAt(LocalDateTime.now());
        order.setCancelReason(reason);
        orderMapper.updateById(order);

        // 4. 记录取消事件
        recordEvent(order.getId(), orderNo,
                OrderEventType.ORDER_CANCELLED, from, OrderStatus.CANCELLED,
                OperatorType.USER, userId, reason);

        log.info("订单取消成功 orderNo={} from={} reason={}", orderNo, from, reason);
        return getByOrderNo(orderNo);
    }

    // ==================================================================
    //  实时轨迹（F1 简化版：只返回状态，无骑手位置）
    // ==================================================================

    @Override
    public OrderTrackVO trackForUser(String orderNo, Long userId) {
        DeliveryOrder order = getEntityByOrderNo(orderNo);
        assertOrderOwner(order, userId);
        return buildTrack(order);
    }

    /**
     * 组装轨迹视图。F1 阶段没有骑手模块和 Redis GEO，
     * 所以骑手信息、实时位置、历史轨迹都为空，只返回订单状态。
     * F2/F4 阶段会逐步补全。
     */
    private OrderTrackVO buildTrack(DeliveryOrder order) {
        OrderTrackVO vo = new OrderTrackVO();
        vo.setOrderNo(order.getOrderNo());
        vo.setStatus(order.getStatus());
        vo.setStatusDesc(OrderStatus.of(order.getStatus()).getDesc());
        vo.setExpectDeliverAt(order.getExpectDeliverAt());
        vo.setRiderAssigned(order.getRiderId() != null);
        if (order.getRiderId() != null) {
            vo.setRiderId(order.getRiderId());
        }
        // F1 阶段：骑手信息、实时位置、历史轨迹都为空
        vo.setTrail(List.of());
        return vo;
    }

    // ==================================================================
    //  事件流水记录（内部工具方法）
    // ==================================================================

    /**
     * 记录一条订单事件流水
     */
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
