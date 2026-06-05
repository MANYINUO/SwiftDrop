package com.delivery.order.controller;

import com.delivery.common.result.PageResult;
import com.delivery.common.result.Result;
import com.delivery.order.dto.CreateOrderRequest;
import com.delivery.order.dto.OrderTrackVO;
import com.delivery.order.dto.OrderVO;
import com.delivery.order.entity.OrderEvent;
import com.delivery.order.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户端（顾客）订单接口：下单、查单、取消、事件流水
 *
 * X-User-Id 请求头：F1 简化版直接由前端传用户ID，
 * 后面鉴权阶段会改成 AuthFilter 校验 token 后自动写入真实用户ID
 */
@RestController
@RequestMapping("/api/user/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /** 创建订单 */
    @PostMapping
    public Result<OrderVO> create(@RequestHeader("X-User-Id") Long userId,
                                  @Valid @RequestBody CreateOrderRequest req) {
        return Result.ok(orderService.createOrder(userId, req));
    }

    /** 订单详情（仅订单所属用户可查） */
    @GetMapping("/{orderNo}")
    public Result<OrderVO> detail(@RequestHeader("X-User-Id") Long userId,
                                  @PathVariable String orderNo) {
        return Result.ok(orderService.getOrderForUser(orderNo, userId));
    }

    /** 我的订单列表（分页） */
    @GetMapping
    public Result<PageResult<OrderVO>> page(@RequestHeader("X-User-Id") Long userId,
                                            @RequestParam(required = false) String status,
                                            @RequestParam(defaultValue = "1") long pageNum,
                                            @RequestParam(defaultValue = "10") long pageSize) {
        return Result.ok(orderService.pageByUser(userId, status, pageNum, pageSize));
    }

    /** 取消订单 */
    @PostMapping("/{orderNo}/cancel")
    public Result<OrderVO> cancel(@RequestHeader("X-User-Id") Long userId,
                                  @PathVariable String orderNo,
                                  @RequestParam(required = false, defaultValue = "用户主动取消") String reason) {
        return Result.ok(orderService.cancel(orderNo, userId, reason));
    }

    /** 订单事件流水（仅订单所属用户可查） */
    @GetMapping("/{orderNo}/events")
    public Result<List<OrderEvent>> events(@RequestHeader("X-User-Id") Long userId,
                                           @PathVariable String orderNo) {
        return Result.ok(orderService.eventsForUser(orderNo, userId));
    }

    /** 实时轨迹（仅订单所属用户可查，F1 简化版只返回状态） */
    @GetMapping("/{orderNo}/track")
    public Result<OrderTrackVO> track(@RequestHeader("X-User-Id") Long userId,
                                      @PathVariable String orderNo) {
        return Result.ok(orderService.trackForUser(orderNo, userId));
    }
}
