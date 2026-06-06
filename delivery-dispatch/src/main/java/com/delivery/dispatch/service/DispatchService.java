package com.delivery.dispatch.service;

import com.delivery.order.dto.OrderVO;

import java.util.List;

/**
 * 调度服务。
 * F3：自动派单——下单后系统按距离/负载/顺路/评分多维度打分，选最优骑手。
 * F2 已实现：待接单列表、接单、取货、送达、骑手容量管理。
 */
public interface DispatchService {

    /**
     * 自动派单：从在线骑手里选最优的，生成派单任务，订单状态变 DISPATCHING。
     * F3 第一版简化：只派给最高分的1个骑手，不做多轮、不做拒单。
     *
     * @param orderId 订单ID
     * @return 派给的骑手ID，无候选时返回 null
     */
    Long autoDispatch(Long orderId);

    /** 待接单列表（PENDING_DISPATCH + DISPATCHING 状态的订单） */
    List<OrderVO> pendingOrders();

    /** 骑手进行中的订单（PENDING_PICKUP + DELIVERING） */
    List<OrderVO> currentOrders(Long riderId);

    /** 接单：PENDING_DISPATCH/DISPATCHING -> PENDING_PICKUP，设置 riderId，骑手容量+1 */
    OrderVO accept(Long riderId, String orderNo);

    /** 取货：PENDING_PICKUP -> DELIVERING */
    OrderVO pickup(Long riderId, String orderNo);

    /** 送达：DELIVERING -> DELIVERED，骑手容量-1 */
    OrderVO delivered(Long riderId, String orderNo);
}
