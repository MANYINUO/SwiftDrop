package com.delivery.dispatch.service;

import com.delivery.dispatch.dto.AcceptResult;
import com.delivery.dispatch.dto.DispatchRoundResult;
import com.delivery.dispatch.dto.DispatchTaskVO;
import com.delivery.dispatch.entity.DispatchTask;
import com.delivery.order.dto.OrderVO;

import java.util.List;

public interface DispatchService {

    /**
     * 对指定订单执行一轮派单。
     *
     * @param round 第几轮（从 1 开始），决定搜索半径
     */
    DispatchRoundResult dispatchOrder(Long orderId, int round);

    /** 按订单号触发派单（下单后自动调用） */
    DispatchRoundResult dispatchByOrderNo(String orderNo);

    /**
     * 后台手动派单：指定骑手直接指派。
     * 会作废该订单所有待响应任务。
     */
    DispatchRoundResult manualAssign(String orderNo, Long riderId, Long adminId);

    /**
     * 骑手接单 —— 全项目并发最关键的一步。
     *
     * <p>四步同一事务：CAS 抢占订单 → 更新派单任务 → 作废同轮其他任务 → 骑手容量 +1。
     * 任何一步失败都回滚，保证「一个订单只分给一个骑手」且「骑手不超载」。
     */
    AcceptResult accept(Long riderId, Long taskId);

    /** 骑手拒单 */
    void reject(Long riderId, Long taskId, String reason);

    /** 骑手取货 */
    void pickup(Long riderId, String orderNo);

    /** 骑手送达 */
    void delivered(Long riderId, String orderNo);

    /** 处理单个超时任务（由定时任务调用） */
    void handleTimeout(DispatchTask task);

    /** 某订单的全部派单记录 */
    List<DispatchTaskVO> listByOrderNo(String orderNo);

    /** 骑手待响应的派单（断线重连后补偿用） */
    List<DispatchTaskVO> pendingTasks(Long riderId);

    /**
     * 骑手手上进行中的订单（已接单但还没送达）。
     *
     * <p>骑手端刷新页面后要靠这个接口恢复状态 —— 不能只依赖前端内存，
     * 否则一刷新就"忘了"自己手上还有单。
     */
    List<OrderVO> currentOrders(Long riderId);
}
