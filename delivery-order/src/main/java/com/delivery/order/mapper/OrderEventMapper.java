package com.delivery.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.delivery.order.entity.OrderEvent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 订单事件流水 Mapper
 */
@Mapper
public interface OrderEventMapper extends BaseMapper<OrderEvent> {

    /**
     * 查某个订单的全部事件流水，按时间正序（事件发生顺序）
     */
    @Select("SELECT * FROM order_event WHERE order_id = #{orderId} ORDER BY id ASC")
    List<OrderEvent> listByOrderId(@Param("orderId") Long orderId);
}
