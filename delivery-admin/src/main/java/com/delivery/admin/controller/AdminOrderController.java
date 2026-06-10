package com.delivery.admin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.delivery.common.result.PageResult;
import com.delivery.common.result.Result;
import com.delivery.dispatch.dto.DispatchRoundResult;
import com.delivery.dispatch.dto.DispatchTaskVO;
import com.delivery.dispatch.service.DispatchService;
import com.delivery.order.dto.OrderVO;
import com.delivery.order.entity.DeliveryOrder;
import com.delivery.order.entity.OrderEvent;
import com.delivery.order.mapper.DeliveryOrderMapper;
import com.delivery.order.mapper.OrderEventMapper;
import com.delivery.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 调度后台 · 订单管理与异常处理。
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/orders")
@RequiredArgsConstructor
public class AdminOrderController {

    private final DeliveryOrderMapper orderMapper;
    private final OrderEventMapper eventMapper;
    private final OrderService orderService;
    private final DispatchService dispatchService;

    /**
     * 订单列表。
     * GET /api/admin/orders?status=DISPATCHING&needManual=true
     */
    @GetMapping
    public Result<PageResult<OrderVO>> page(@RequestParam(required = false) String status,
                                            @RequestParam(required = false) Boolean needManual,
                                            @RequestParam(defaultValue = "1") long pageNum,
                                            @RequestParam(defaultValue = "10") long pageSize) {
        LambdaQueryWrapper<DeliveryOrder> w = Wrappers.<DeliveryOrder>lambdaQuery()
                .eq(status != null && !status.isBlank(), DeliveryOrder::getStatus, status)
                .eq(Boolean.TRUE.equals(needManual), DeliveryOrder::getNeedManual, 1)
                .orderByDesc(DeliveryOrder::getId);

        IPage<DeliveryOrder> page = orderMapper.selectPage(new Page<>(pageNum, pageSize), w);
        List<OrderVO> records = page.getRecords().stream()
                .map(OrderVO::of).collect(Collectors.toList());
        return Result.ok(PageResult.of(records, page.getTotal(), pageNum, pageSize));
    }

    /** 订单详情 */
    @GetMapping("/{orderNo}")
    public Result<OrderVO> detail(@PathVariable String orderNo) {
        return Result.ok(orderService.getByOrderNo(orderNo));
    }

    /** 订单事件流水 */
    @GetMapping("/{orderNo}/events")
    public Result<List<OrderEvent>> events(@PathVariable String orderNo) {
        return Result.ok(orderService.events(orderNo));
    }

    /** 该订单的完整派单记录（含每轮每人的打分） */
    @GetMapping("/{orderNo}/dispatch-tasks")
    public Result<List<DispatchTaskVO>> dispatchTasks(@PathVariable String orderNo) {
        return Result.ok(dispatchService.listByOrderNo(orderNo));
    }

    /**
     * 手动派单：不指定骑手时按算法重派一轮。
     * POST /api/admin/orders/{orderNo}/dispatch?riderId=2
     */
    @PostMapping("/{orderNo}/dispatch")
    public Result<DispatchRoundResult> dispatch(@PathVariable String orderNo,
                                                @RequestParam(required = false) Long riderId,
                                                @RequestHeader(value = "X-Admin-Id", required = false) Long adminId) {
        Long op = adminId == null ? 0L : adminId;
        if (riderId != null) {
            return Result.ok(dispatchService.manualAssign(orderNo, riderId, op));
        }
        return Result.ok(dispatchService.dispatchByOrderNo(orderNo));
    }

    /**
     * 标记异常（人工介入）。
     * POST /api/admin/orders/{orderNo}/exception?remark=骑手失联
     *
     * <p>状态流转与骑手容量释放在 order 服务的同一事务内原子完成，避免标记异常后
     * 骑手容量泄漏、从此接不到单。
     */
    @PostMapping("/{orderNo}/exception")
    public Result<OrderVO> markException(@PathVariable String orderNo,
                                         @RequestParam(required = false) String remark,
                                         @RequestHeader(value = "X-Admin-Id", required = false) Long adminId) {
        return Result.ok(orderService.markException(orderNo, adminId, remark));
    }

    /**
     * 异常处理：改派 或 取消。
     * POST /api/admin/orders/{orderNo}/resolve?action=REASSIGN
     * POST /api/admin/orders/{orderNo}/resolve?action=CANCEL&reason=无人接单
     */
    @PostMapping("/{orderNo}/resolve")
    public Result<OrderVO> resolve(@PathVariable String orderNo,
                                   @RequestParam String action,
                                   @RequestParam(required = false) String reason,
                                   @RequestParam(required = false) Long riderId,
                                   @RequestHeader(value = "X-Admin-Id", required = false) Long adminId) {
        // 第一步：状态流转（改派回退并解绑原骑手 / 取消）在 order 服务的独立事务内原子提交
        orderService.resolveException(orderNo, adminId, action, reason);

        // 第二步（仅改派）：回退事务提交后再重新派单。此时订单已稳定落在「待派单」，
        // 即使重派失败也不会卡在「已回退但没派出」的中间态，可再次人工派单兜底。
        if ("REASSIGN".equalsIgnoreCase(action)) {
            if (riderId != null) {
                dispatchService.manualAssign(orderNo, riderId, adminId);
            } else {
                dispatchService.dispatchByOrderNo(orderNo);
            }
        }

        return Result.ok(orderService.getByOrderNo(orderNo));
    }
}
