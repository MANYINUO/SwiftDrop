package com.delivery.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.delivery.order.entity.IdempotentRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 幂等记录 Mapper
 */
@Mapper
public interface IdempotentRecordMapper extends BaseMapper<IdempotentRecord> {
}
