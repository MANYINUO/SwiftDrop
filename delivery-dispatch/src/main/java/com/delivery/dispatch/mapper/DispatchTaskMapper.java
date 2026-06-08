package com.delivery.dispatch.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.delivery.dispatch.entity.DispatchTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 派单任务 Mapper：一条 dispatch_task 记录表示「某订单在某一轮被推给了某个骑手」。
 *
 * <p>一个订单一轮会推给多名候选骑手，所以一次派单会插入多条任务；最终只有一条变成
 * {@code ACCEPTED}，其余在骑手接单后由 {@code cancelOtherPending} 作废。
 *
 * <p><b>所有状态更新都在 WHERE 里带上 {@code status = 'PENDING'}</b>：这是 CAS 条件更新——
 * 哪怕「超时扫描」和「骑手点接单」同时发生，也只有一个操作能改成功，天然幂等，
 * 不会出现重复接单、重复改状态。
 */
@Mapper
public interface DispatchTaskMapper extends BaseMapper<DispatchTask> {

    /** 扫描已超时且仍待响应的任务（定时任务用） */
    @Select("SELECT * FROM dispatch_task WHERE status = 'PENDING' AND expire_at <= NOW() " +
            "ORDER BY expire_at ASC LIMIT #{limit}")
    List<DispatchTask> selectExpiredPending(@Param("limit") int limit);

    @Select("SELECT * FROM dispatch_task WHERE order_id = #{orderId} " +
            "ORDER BY round ASC, candidate_rank ASC")
    List<DispatchTask> listByOrderId(@Param("orderId") Long orderId);

    /** 骑手待响应的派单（断线重连后补偿拉取用） */
    @Select("SELECT * FROM dispatch_task WHERE rider_id = #{riderId} AND status = 'PENDING' " +
            "AND expire_at > NOW() ORDER BY id DESC")
    List<DispatchTask> listPendingByRider(@Param("riderId") Long riderId);

    /** 本订单还剩几个待响应的派单 */
    @Select("SELECT COUNT(*) FROM dispatch_task WHERE order_id = #{orderId} AND status = 'PENDING'")
    long countPendingByOrder(@Param("orderId") Long orderId);

    /**
     * 已经<b>拒单</b>过的骑手 —— 下一轮要永久排除。
     *
     * <p>拒单是骑手明确的意愿，必须尊重；超时则不同（见 {@link #selectTimedOutRiderIds}）。
     */
    @Select("SELECT DISTINCT rider_id FROM dispatch_task " +
            "WHERE order_id = #{orderId} AND status = 'REJECTED'")
    List<Long> selectRejectedRiderIds(@Param("orderId") Long orderId);

    /**
     * 已经<b>超时</b>过的骑手 —— 下一轮只降权，不排除。
     *
     * <p>超时可能只是当时在忙 / 信号差 / 没看到推送，直接排除会让订单
     * 很快陷入「无候选 → 转人工」。
     */
    @Select("SELECT DISTINCT rider_id FROM dispatch_task " +
            "WHERE order_id = #{orderId} AND status = 'TIMEOUT'")
    List<Long> selectTimedOutRiderIds(@Param("orderId") Long orderId);

    // ---------------- 状态流转（全部带 status 条件，天然幂等）----------------

    @Update("UPDATE dispatch_task SET status = 'ACCEPTED', responded_at = NOW() " +
            "WHERE id = #{id} AND status = 'PENDING'")
    int markAccepted(@Param("id") Long id);

    @Update("UPDATE dispatch_task SET status = 'REJECTED', responded_at = NOW() " +
            "WHERE id = #{id} AND status = 'PENDING'")
    int markRejected(@Param("id") Long id);

    @Update("UPDATE dispatch_task SET status = 'TIMEOUT', responded_at = NOW() " +
            "WHERE id = #{id} AND status = 'PENDING'")
    int markTimeout(@Param("id") Long id);

    /** 接单成功后，作废同一轮的其他派单任务 */
    @Update("UPDATE dispatch_task SET status = 'CANCELLED' " +
            "WHERE order_id = #{orderId} AND status = 'PENDING' AND id <> #{exceptId}")
    int cancelOtherPending(@Param("orderId") Long orderId, @Param("exceptId") Long exceptId);

    /** 后台手动派单 / 订单取消时，作废该订单全部待响应任务 */
    @Update("UPDATE dispatch_task SET status = 'CANCELLED' " +
            "WHERE order_id = #{orderId} AND status = 'PENDING'")
    int cancelAllPending(@Param("orderId") Long orderId);

    // ---------------- 指标统计 ----------------

    @Select("SELECT COUNT(*) FROM dispatch_task WHERE status = 'ACCEPTED'")
    long countAccepted();

    @Select("SELECT COUNT(*) FROM dispatch_task")
    long countAll();

    /** 平均响应时长（秒）：从派单到骑手点接单 */
    @Select("SELECT AVG(TIMESTAMPDIFF(SECOND, created_at, responded_at)) FROM dispatch_task " +
            "WHERE status = 'ACCEPTED' AND responded_at IS NOT NULL")
    Double avgAcceptSeconds();

    /** 平均派单轮次：越小说明调度越准 */
    @Select("SELECT AVG(round) FROM dispatch_task WHERE status = 'ACCEPTED'")
    Double avgRound();
}
