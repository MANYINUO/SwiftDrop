package com.delivery.rider.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.delivery.rider.entity.Rider;
import org.apache.ibatis.annotations.Mapper;

/**
 * 骑手 Mapper
 * 继承 BaseMapper<Rider> 后自动拥有 CRUD 方法：
 *   insert、deleteById、updateById、selectById、selectList 等
 * 简单查询不用写 SQL，复杂查询才在 XML 或注解里写
 */
@Mapper
public interface RiderMapper extends BaseMapper<Rider> {
}
