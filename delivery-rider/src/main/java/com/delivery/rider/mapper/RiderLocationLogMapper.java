package com.delivery.rider.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.delivery.rider.entity.RiderLocationLog;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 骑手位置流水（历史轨迹）Mapper。
 *
 * <p>和 Redis GEO 里的「实时位置」是两回事：这张表只追加、不更新，由内存缓冲队列批量落库，
 * 用来回放骑手走过的路线。<b>实时调度读 Redis，轨迹展示读这张表</b>，两条链路刻意分开，
 * 避免高频轨迹写入拖垮数据库。
 */
@Mapper
public interface RiderLocationLogMapper extends BaseMapper<RiderLocationLog> {

    /**
     * 查某个订单「最近 {@code limit} 个」轨迹点，最终按时间正序返回。
     *
     * <p>用于用户端进页面时直接把骑手走过的路线画出来，而不是等实时推送慢慢累积。
     *
     * <p><b>为什么套一层子查询</b>：位置约每 2 秒落一条，长订单的点数会超过 {@code limit}。
     * 内层先用 {@code report_at DESC + LIMIT} 取「最新的 N 个点」（保留骑手当前位置附近的
     * 轨迹），外层再 {@code ASC} 转回时间正序，前端拿到即可直接按顺序连线。
     * 若直接 {@code ASC + LIMIT}，取到的是最旧的 N 个点——长订单只会显示出发段，
     * 骑手「现在走到哪」这段最近的轨迹反而丢失。
     */
    @Select("""
            SELECT rider_id, order_no, lng, lat, report_at
            FROM (
                SELECT rider_id, order_no, lng, lat, report_at
                FROM rider_location_log
                WHERE order_no = #{orderNo}
                ORDER BY report_at DESC
                LIMIT #{limit}
            ) t
            ORDER BY t.report_at ASC
            """)
    List<RiderLocationLog> listByOrderNo(@Param("orderNo") String orderNo, @Param("limit") int limit);

    /**
     * 批量插入位置流水。
     *
     * <p>配合 JDBC URL 上的 {@code rewriteBatchedStatements=true}，
     * 单次可写入上千条，比逐条 insert 快一个数量级。
     *
     * <p>用文本块而不是 {@code "<script>" + ...} 拼接：拼接写法在 IDEA 里
     * 只对第一个字符串片段注入 SQL 解析，于是单独看到 {@code "<script>"}
     * 就报「应为 ','、'EXISTS' 或 'DELIMITER'」；文本块能被整体识别，
     * 而且少了一堆 {@code +} 和转义，可读性好得多。
     *
     * @return 实际写入行数，调用方应校验是否等于 list.size()
     */
    @Insert("""
            <script>
            INSERT INTO rider_location_log (rider_id, order_no, lng, lat, report_at) VALUES
            <foreach collection='list' item='it' separator=','>
            (#{it.riderId}, #{it.orderNo}, #{it.lng}, #{it.lat}, #{it.reportAt})
            </foreach>
            </script>
            """)
    int batchInsert(@Param("list") List<RiderLocationLog> list);
}
