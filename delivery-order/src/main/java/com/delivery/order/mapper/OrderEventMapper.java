package com.delivery.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.delivery.order.entity.OrderEvent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface OrderEventMapper extends BaseMapper<OrderEvent> {

    @Select("SELECT * FROM order_event WHERE order_id = #{orderId} ORDER BY id ASC")
    List<OrderEvent> listByOrderId(@Param("orderId") Long orderId);
}
