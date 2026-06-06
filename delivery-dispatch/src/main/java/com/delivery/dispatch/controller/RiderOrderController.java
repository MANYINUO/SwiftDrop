package com.delivery.dispatch.controller;

import com.delivery.common.result.Result;
import com.delivery.dispatch.service.DispatchService;
import com.delivery.order.dto.OrderVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 骑手接单与配送操作接口。
 * 放在 dispatch 模块是因为要同时操作订单和骑手容量。
 * F2 简化版：没有 dispatch_task 表，待接单直接返回订单，接单用 orderNo。
 */
@RestController
@RequestMapping("/api/rider")
@RequiredArgsConstructor
public class RiderOrderController {

    private final DispatchService dispatchService;

    /** 待接单列表（所有 PENDING_DISPATCH 的订单） */
    @GetMapping("/{riderId}/pending-orders")
    public Result<List<OrderVO>> pendingOrders(@PathVariable Long riderId) {
        return Result.ok(dispatchService.pendingOrders());
    }

    /** 骑手进行中的订单（PENDING_PICKUP + DELIVERING） */
    @GetMapping("/{riderId}/orders")
    public Result<List<OrderVO>> currentOrders(@PathVariable Long riderId) {
        return Result.ok(dispatchService.currentOrders(riderId));
    }

    /** 接单：PENDING_DISPATCH -> PENDING_PICKUP */
    @PostMapping("/{riderId}/orders/{orderNo}/accept")
    public Result<OrderVO> accept(@PathVariable Long riderId,
                                  @PathVariable String orderNo) {
        return Result.ok(dispatchService.accept(riderId, orderNo));
    }

    /** 取货：PENDING_PICKUP -> DELIVERING */
    @PostMapping("/{riderId}/orders/{orderNo}/pickup")
    public Result<OrderVO> pickup(@PathVariable Long riderId,
                                  @PathVariable String orderNo) {
        return Result.ok(dispatchService.pickup(riderId, orderNo));
    }

    /** 送达：DELIVERING -> DELIVERED */
    @PostMapping("/{riderId}/orders/{orderNo}/delivered")
    public Result<OrderVO> delivered(@PathVariable Long riderId,
                                     @PathVariable String orderNo) {
        return Result.ok(dispatchService.delivered(riderId, orderNo));
    }
}
