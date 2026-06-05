package com.delivery.order.service;

import com.delivery.common.result.PageResult;
import com.delivery.order.dto.CreateOrderRequest;
import com.delivery.order.dto.OrderTrackVO;
import com.delivery.order.dto.OrderVO;
import com.delivery.order.entity.DeliveryOrder;
import com.delivery.order.entity.OrderEvent;

import java.util.List;

/**
 * 订单服务接口
 */
public interface OrderService {

    /** 创建订单 */
    OrderVO createOrder(Long userId, CreateOrderRequest req);

    /** 按订单号查订单（内部用，不做归属校验） */
    OrderVO getByOrderNo(String orderNo);

    /** 按订单号查实体（内部用） */
    DeliveryOrder getEntityByOrderNo(String orderNo);

    /** 用户查订单详情（校验归属） */
    OrderVO getOrderForUser(String orderNo, Long userId);

    /** 用户查订单列表（分页） */
    PageResult<OrderVO> pageByUser(Long userId, String status, long pageNum, long pageSize);

    /** 用户取消订单 */
    OrderVO cancel(String orderNo, Long userId, String reason);

    /** 用户查订单事件流水（校验归属） */
    List<OrderEvent> eventsForUser(String orderNo, Long userId);

    /** 用户查实时轨迹（校验归属，F1 简化版只返回状态，无骑手位置） */
    OrderTrackVO trackForUser(String orderNo, Long userId);
}
