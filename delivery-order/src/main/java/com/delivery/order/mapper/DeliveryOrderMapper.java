package com.delivery.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.delivery.order.entity.DeliveryOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 订单 Mapper
 */
@Mapper
public interface DeliveryOrderMapper extends BaseMapper<DeliveryOrder> {

    /**
     * 按订单号查订单（订单号是唯一索引，加 LIMIT 1 更安全）
     */
    @Select("SELECT * FROM delivery_order WHERE order_no = #{orderNo} LIMIT 1")
    DeliveryOrder selectByOrderNo(@Param("orderNo") String orderNo);
}
