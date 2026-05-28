package com.delivery.dispatch.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.delivery.dispatch.entity.DispatchTask;
import org.apache.ibatis.annotations.Mapper;

/**
 * 派单任务 Mapper
 */
@Mapper
public interface DispatchTaskMapper extends BaseMapper<DispatchTask> {
}
