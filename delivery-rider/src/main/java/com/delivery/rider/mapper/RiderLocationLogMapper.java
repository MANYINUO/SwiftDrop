package com.delivery.rider.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.delivery.rider.entity.RiderLocationLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 骑手位置流水 Mapper
 */
@Mapper
public interface RiderLocationLogMapper extends BaseMapper<RiderLocationLog> {
}
