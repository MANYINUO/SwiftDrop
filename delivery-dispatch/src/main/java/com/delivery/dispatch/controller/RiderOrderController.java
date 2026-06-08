package com.delivery.dispatch.controller;

import com.delivery.common.result.Result;
import com.delivery.dispatch.dto.AcceptResult;
import com.delivery.dispatch.dto.DispatchTaskVO;
import com.delivery.dispatch.service.DispatchService;
import com.delivery.order.dto.OrderVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 骑手接单与配送操作接口。
 *
 * <p>这些接口之所以放在 dispatch 模块而不是 rider 模块，
 * 是因为它们要同时改动「订单状态」和「骑手容量」，
 * 而只有 dispatch 模块同时依赖 order 和 rider，模块边界才不会被打破。
 */
@RestController
@RequestMapping("/api/rider")
@RequiredArgsConstructor
public class RiderOrderController {

    private final DispatchService dispatchService;

    /**
     * 拉取待响应的派单（断线重连后的补偿手段）。
     * GET /api/rider/1/dispatch-tasks
     */
    @GetMapping("/{riderId}/dispatch-tasks")
    public Result<List<DispatchTaskVO>> pendingTasks(@PathVariable Long riderId) {
        return Result.ok(dispatchService.pendingTasks(riderId));
    }

    /**
     * 骑手手上进行中的订单（已接单未送达）。
     * GET /api/rider/1/orders
     */
    @GetMapping("/{riderId}/orders")
    public Result<List<OrderVO>> currentOrders(@PathVariable Long riderId) {
        return Result.ok(dispatchService.currentOrders(riderId));
    }

    /**
     * 接单。
     * POST /api/rider/1/dispatch-tasks/100/accept
     *
     * <p>并发场景下同一订单只会有一个人成功，其余返回 40006「已被其他骑手接走」。
     * 重复提交同一 taskId 会命中幂等，返回首次结果。
     */
    @PostMapping("/{riderId}/dispatch-tasks/{taskId}/accept")
    public Result<AcceptResult> accept(@PathVariable Long riderId,
                                       @PathVariable Long taskId) {
        return Result.ok(dispatchService.accept(riderId, taskId));
    }

    /**
     * 拒单。
     * POST /api/rider/1/dispatch-tasks/100/reject?reason=距离太远
     */
    @PostMapping("/{riderId}/dispatch-tasks/{taskId}/reject")
    public Result<Void> reject(@PathVariable Long riderId,
                               @PathVariable Long taskId,
                               @RequestParam(required = false) String reason) {
        dispatchService.reject(riderId, taskId, reason);
        return Result.ok();
    }

    /** 取货：待取货 -> 配送中 */
    @PostMapping("/{riderId}/orders/{orderNo}/pickup")
    public Result<Void> pickup(@PathVariable Long riderId, @PathVariable String orderNo) {
        dispatchService.pickup(riderId, orderNo);
        return Result.ok();
    }

    /** 送达：配送中 -> 已送达（同时释放骑手容量） */
    @PostMapping("/{riderId}/orders/{orderNo}/delivered")
    public Result<Void> delivered(@PathVariable Long riderId, @PathVariable String orderNo) {
        dispatchService.delivered(riderId, orderNo);
        return Result.ok();
    }
}
