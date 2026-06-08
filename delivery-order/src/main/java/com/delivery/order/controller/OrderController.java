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
 * 用户端（顾客）接口：下单、查单、取消、实时轨迹、事件流水。
 *
 * <p>方法里的 {@code X-User-Id} 不是前端能随便指定的——请求先经过 {@code AuthFilter}，
 * 由它校验游客登录令牌，再把 {@code X-User-Id} <b>覆写</b>成令牌里的真实用户 ID。
 * 所以 detail / track / events 即使被人篡改请求头，也只能查到属于自己的订单。
 */
@RestController
@RequestMapping("/api/user/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /**
     * 创建订单。
     *
     * <p><b>必须带 Idempotency-Key 请求头</b>，同一用户同一 key 只会产生一笔订单。
     * 这是验收场景「用户重复提交下单请求，不产生重复订单」的实现基础。
     */
    @PostMapping
    public Result<OrderVO> create(@RequestHeader("X-User-Id") Long userId,
                                  @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                  @Valid @RequestBody CreateOrderRequest req) {
        // 客户端没传时兜底生成一个，保证接口不会因为缺头而不可用
        String key = (idempotencyKey == null || idempotencyKey.isBlank())
                ? java.util.UUID.randomUUID().toString()
                : idempotencyKey;
        return Result.ok(orderService.createOrder(userId, key, req));
    }

    /** 订单详情（仅订单所属用户可查，X-User-Id 由鉴权过滤器按登录令牌写入） */
    @GetMapping("/{orderNo}")
    public Result<OrderVO> detail(@RequestHeader("X-User-Id") Long userId,
                                  @PathVariable String orderNo) {
        return Result.ok(orderService.getOrderForUser(orderNo, userId));
    }

    /** 我的订单列表 */
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

    /** 实时轨迹：订单状态 + 骑手位置（仅订单所属用户可查） */
    @GetMapping("/{orderNo}/track")
    public Result<OrderTrackVO> track(@RequestHeader("X-User-Id") Long userId,
                                      @PathVariable String orderNo) {
        return Result.ok(orderService.trackForUser(orderNo, userId));
    }

    /** 订单事件流水（仅订单所属用户可查） */
    @GetMapping("/{orderNo}/events")
    public Result<List<OrderEvent>> events(@RequestHeader("X-User-Id") Long userId,
                                           @PathVariable String orderNo) {
        return Result.ok(orderService.eventsForUser(orderNo, userId));
    }
}
