package com.delivery.rider.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.delivery.rider.entity.Rider;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.List;

/**
 * 骑手 Mapper
 * 继承 BaseMapper<Rider> 后自动拥有 CRUD 方法。
 * 下面是自定义 SQL：上下线、位置更新、容量增减、在线列表。
 */
@Mapper
public interface RiderMapper extends BaseMapper<Rider> {

    /** 查在线骑手 */
    @Select("SELECT * FROM rider WHERE online_status = 1")
    List<Rider> selectOnlineRiders();

    /**
     * 接单时容量 +1。
     * AND delivering_count < max_capacity 是数据库层面的容量检查，
     * 影响行数为 0 说明骑手已满，接单事务必须回滚。
     */
    @Update("UPDATE rider SET delivering_count = delivering_count + 1, work_status = 1, " +
            "version = version + 1, updated_at = NOW() " +
            "WHERE id = #{riderId} AND delivering_count < max_capacity")
    int increaseLoad(@Param("riderId") Long riderId);

    /**
     * 送达/取消后容量 -1。
     * work_status 要写在 delivering_count 前面（MySQL SET 从左到右求值），
     *  delivering_count 用 GREATEST(...-1, 0) 避免无符号下溢。
     */
    @Update("UPDATE rider SET " +
            "work_status = CASE WHEN delivering_count <= 1 THEN 0 ELSE 1 END, " +
            "delivering_count = GREATEST(delivering_count - 1, 0), " +
            "version = version + 1, updated_at = NOW() " +
            "WHERE id = #{riderId}")
    int decreaseLoad(@Param("riderId") Long riderId);

    /** 上线/下线 */
    @Update("UPDATE rider SET online_status = #{online}, " +
            "work_status = CASE WHEN #{online} = 0 THEN 0 ELSE work_status END, " +
            "version = version + 1, updated_at = NOW() WHERE id = #{riderId}")
    int updateOnlineStatus(@Param("riderId") Long riderId, @Param("online") int online);

    /** 更新最后位置 */
    @Update("UPDATE rider SET last_lng = #{lng}, last_lat = #{lat}, last_report_at = NOW(), " +
            "version = version + 1, updated_at = NOW() WHERE id = #{riderId}")
    int updateLastPosition(@Param("riderId") Long riderId,
                           @Param("lng") BigDecimal lng,
                           @Param("lat") BigDecimal lat);
}
