package com.delivery.order.service;

import com.delivery.common.result.PageResult;
import com.delivery.order.dto.CreateOrderRequest;
import com.delivery.order.dto.OrderTrackVO;
import com.delivery.order.dto.OrderVO;
import com.delivery.order.entity.DeliveryOrder;
import com.delivery.order.entity.OrderEvent;

import java.util.List;

public interface OrderService {

    /**
     * 创建订单（幂等）。
     *
     * @param idempotencyKey 客户端生成的幂等键，同一用户同一 key 只会产生一笔订单
     */
    OrderVO createOrder(Long userId, String idempotencyKey, CreateOrderRequest req);

    OrderVO getByOrderNo(String orderNo);

    DeliveryOrder getEntityByOrderNo(String orderNo);

    PageResult<OrderVO> pageByUser(Long userId, String status, long pageNum, long pageSize);

    /** 用户取消订单 */
    OrderVO cancel(String orderNo, Long userId, String reason);

    /**
     * 后台标记异常（人工介入），状态流转与容量释放在同一事务内原子完成。
     *
     * <p>若订单已分配骑手（待取货/配送中），会释放该骑手容量；待派单/派单中尚未接单、
     * riderId 为空，则不占用也不释放。
     */
    OrderVO markException(String orderNo, Long adminId, String remark);

    /**
     * 后台处理异常订单：{@code REASSIGN} 回退到待派单并解绑原骑手，{@code CANCEL} 取消订单。
     *
     * <p>本方法只保证「状态流转 + 字段变更」原子；改派后的重新派单由调用方在本事务
     * 提交之后触发，避免重派失败把订单卡在「已回退但未派出」的中间态。容量在
     * {@link #markException} 时已释放，本方法不重复释放。
     *
     * @param action {@code REASSIGN} 或 {@code CANCEL}
     */
    OrderVO resolveException(String orderNo, Long adminId, String action, String reason);

    /** 用户查看轨迹：状态 + 骑手位置（内部调用，不做归属校验） */
    OrderTrackVO track(String orderNo);

    /** 订单事件流水（内部调用，不做归属校验） */
    List<OrderEvent> events(String orderNo);

    /** 用户查看订单详情：校验订单归属，不属于本人一律按「不存在」处理 */
    OrderVO getOrderForUser(String orderNo, Long userId);

    /** 用户查看实时轨迹：校验订单归属 */
    OrderTrackVO trackForUser(String orderNo, Long userId);

    /** 用户查看订单事件流水：校验订单归属 */
    List<OrderEvent> eventsForUser(String orderNo, Long userId);
}
