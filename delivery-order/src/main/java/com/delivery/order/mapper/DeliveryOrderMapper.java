package com.delivery.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.delivery.order.entity.DeliveryOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 订单 Mapper。
 *
 * <p>注意：状态流转<b>不要</b>用这里的普通 update，
 * 必须走 {@code OrderStateMachine}（带 CAS 条件）。
 */
@Mapper
public interface DeliveryOrderMapper extends BaseMapper<DeliveryOrder> {

    @Select("SELECT * FROM delivery_order WHERE order_no = #{orderNo} LIMIT 1")
    DeliveryOrder selectByOrderNo(@Param("orderNo") String orderNo);

    /**
     * 统计各状态订单数，用于后台指标看板。
     */
    @Select("SELECT status, COUNT(*) AS cnt FROM delivery_order GROUP BY status")
    List<Map<String, Object>> countGroupByStatus();

    /**
     * 平均送达时长（秒）：只统计已送达的订单。
     */
    @Select("SELECT AVG(TIMESTAMPDIFF(SECOND, created_at, delivered_at)) " +
            "FROM delivery_order WHERE status = 'DELIVERED' AND delivered_at IS NOT NULL")
    Double avgDeliverSeconds();

    /**
     * 待人工调度的订单数。
     */
    @Select("SELECT COUNT(*) FROM delivery_order WHERE need_manual = 1 AND status = 'PENDING_DISPATCH'")
    long countNeedManual();

    /** 订单总数 */
    @Select("SELECT COUNT(*) FROM delivery_order")
    long countOrders();

    /**
     * 成功派出（已有骑手接单）的订单数。
     *
     * <p>派单成功率应该用「订单口径」算：{@code 已派出订单 / 订单总数}。
     * 不能用「派单任务口径」（ACCEPTED / 全部任务）—— 一个订单会推给多个候选骑手，
     * 那个算法的上限只有 1/候选数，是误导性的。
     */
    @Select("SELECT COUNT(*) FROM delivery_order WHERE rider_id IS NOT NULL")
    long countDispatchedOrders();
}
