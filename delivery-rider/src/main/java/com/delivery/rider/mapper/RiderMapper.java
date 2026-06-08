package com.delivery.rider.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.delivery.rider.entity.Rider;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface RiderMapper extends BaseMapper<Rider> {

    @Select("SELECT * FROM rider WHERE rider_no = #{riderNo} LIMIT 1")
    Rider selectByRiderNo(@Param("riderNo") String riderNo);

    @Select("SELECT * FROM rider WHERE online_status = 1")
    List<Rider> selectOnlineRiders();

    @Select("SELECT COUNT(*) FROM rider WHERE online_status = 1")
    long countOnline();

    /**
     * 骑手容量 +1 —— <b>接单原子操作的第四步</b>。
     *
     * <p>关键在于 {@code AND delivering_count < max_capacity}：
     * 数据库帮我们做容量检查，影响行数为 0 就说明骑手已满，必须回滚整个接单事务。
     * 少了这个条件，就会出现一个骑手同时接 10 单的超卖问题。
     */
    @Update("UPDATE rider SET delivering_count = delivering_count + 1, work_status = 1, " +
            "version = version + 1, updated_at = NOW() " +
            "WHERE id = #{riderId} AND delivering_count < max_capacity")
    int increaseLoad(@Param("riderId") Long riderId);

    /**
     * 送达 / 取消后释放容量。
     *
     * <p><b>注意两个坑</b>（都踩过）：
     * <ol>
     *   <li>MySQL 的 SET 子句是<b>从左到右求值</b>，后面的子句会看到前面已更新的值。
     *       所以 {@code work_status} 必须写在 {@code delivering_count} <b>前面</b>，
     *       否则它算的是「已经减过一次」的值。</li>
     *   <li>{@code delivering_count} 是无符号列，值为 0 时再做 {@code - 1} 会
     *       <b>无符号下溢</b>（MySQL 报 1690: BIGINT UNSIGNED value is out of range），
     *       直接抛异常导致整个送达事务回滚。所以要先 {@code CAST AS SIGNED} 再减。</li>
     * </ol>
     */
    @Update("UPDATE rider SET " +
            // 先算 work_status（此时 delivering_count 还是原值）
            "work_status = CASE WHEN CAST(delivering_count AS SIGNED) <= 1 THEN 0 ELSE 1 END, " +
            // 再更新计数（先转成有符号，避免 0-1 下溢）
            "delivering_count = GREATEST(CAST(delivering_count AS SIGNED) - 1, 0), " +
            "version = version + 1, updated_at = NOW() " +
            "WHERE id = #{riderId}")
    int decreaseLoad(@Param("riderId") Long riderId);

    /** 上线 / 下线 */
    @Update("UPDATE rider SET online_status = #{online}, " +
            "work_status = CASE WHEN #{online} = 0 THEN 0 ELSE work_status END, " +
            "version = version + 1, updated_at = NOW() WHERE id = #{riderId}")
    int updateOnlineStatus(@Param("riderId") Long riderId, @Param("online") int online);

    /** 冷备最后位置 */
    @Update("UPDATE rider SET last_lng = #{lng}, last_lat = #{lat}, last_report_at = NOW(), " +
            "version = version + 1, updated_at = NOW() WHERE id = #{riderId}")
    int updateLastPosition(@Param("riderId") Long riderId,
                           @Param("lng") BigDecimal lng,
                           @Param("lat") BigDecimal lat);
}
