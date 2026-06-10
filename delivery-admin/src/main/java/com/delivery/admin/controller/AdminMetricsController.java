package com.delivery.admin.controller;

import com.delivery.admin.service.MetricsService;
import com.delivery.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 调度后台 · 指标看板。
 *
 * <p>派单成功率、平均接单响应、平均送达时长等指标都从这个接口查。
 * 数字一律从运行库实时统计，不预设、不读缓存。
 */
@RestController
@RequestMapping("/api/admin/metrics")
@RequiredArgsConstructor
public class AdminMetricsController {

    private final MetricsService metricsService;

    /** GET /api/admin/metrics */
    @GetMapping
    public Result<Map<String, Object>> overview() {
        return Result.ok(metricsService.overview());
    }

    /** GET /api/admin/metrics/order-status */
    @GetMapping("/order-status")
    public Result<Map<String, Long>> orderStatus() {
        return Result.ok(metricsService.orderStatusDistribution());
    }
}
