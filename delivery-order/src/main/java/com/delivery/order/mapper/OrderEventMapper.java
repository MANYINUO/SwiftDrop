package com.delivery.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.delivery.order.entity.OrderEvent;
import org.apache.ibatis.annotations.Mapper;

/**
 * 订单事件流水 Mapper
 */
@Mapper
public interface OrderEventMapper extends BaseMapper<OrderEvent> {
}
