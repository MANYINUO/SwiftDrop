package com.delivery.rider.controller;

import com.delivery.common.result.Result;
import com.delivery.rider.dto.LocationReportRequest;
import com.delivery.rider.dto.RiderOptionVO;
import com.delivery.rider.dto.RiderVO;
import com.delivery.rider.service.RiderLocationService;
import com.delivery.rider.service.RiderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 骑手端接口：上下线、位置上报、骑手名单。
 *
 * <p>路径里的 {@code {riderId}} 必须和登录令牌中的骑手一致，由 {@code AuthFilter} 校验，
 * 骑手 A 无法操作骑手 B 的资源（水平越权返回 403）。{@code /options} 是唯一的公开接口
 * （登录页选骑手用，字段精简、不含手机号），其余都要求骑手登录。
 * 「接单 / 拒单 / 取货 / 送达」相关接口在 dispatch 模块的 {@code RiderOrderController}。
 */
@RestController
@RequestMapping("/api/rider")
@RequiredArgsConstructor
public class RiderController {

    private final RiderService riderService;
    private final RiderLocationService locationService;

    /**
     * 上线。首次上线必须带经纬度。
     * POST /api/rider/1/online?lng=116.404&lat=39.913
     */
    @PostMapping("/{riderId}/online")
    public Result<RiderVO> online(@PathVariable Long riderId,
                                  @RequestParam(required = false) BigDecimal lng,
                                  @RequestParam(required = false) BigDecimal lat) {
        return Result.ok(riderService.online(riderId, lng, lat));
    }

    /** 下线 */
    @PostMapping("/{riderId}/offline")
    public Result<RiderVO> offline(@PathVariable Long riderId) {
        return Result.ok(riderService.offline(riderId));
    }

    /**
     * 上报位置（最高频接口）。
     * POST /api/rider/1/location
     */
    @PostMapping("/{riderId}/location")
    public Result<Map<String, Object>> reportLocation(@PathVariable Long riderId,
                                                      @Valid @RequestBody LocationReportRequest req) {
        boolean accepted = locationService.report(riderId, req);
        return Result.ok(Map.of(
                "accepted", accepted,
                "message", accepted ? "已接收" : "上报过于频繁，已按防抖策略丢弃"
        ));
    }

    /** 骑手详情（含实时位置） */
    @GetMapping("/{riderId}")
    public Result<RiderVO> detail(@PathVariable Long riderId) {
        return Result.ok(riderService.getById(riderId));
    }

    /**
     * 登录页骑手名单（公开接口，精简字段：不含手机号 / 坐标 / 评分）。
     * 完整名单 /list 需骑手登录后访问。
     */
    @GetMapping("/options")
    public Result<List<RiderOptionVO>> options() {
        return Result.ok(riderService.listOptions());
    }

    /** 全部骑手（需骑手登录） */
    @GetMapping("/list")
    public Result<List<RiderVO>> list() {
        return Result.ok(riderService.listAll());
    }

    /** 在线骑手（需骑手登录） */
    @GetMapping("/online")
    public Result<List<RiderVO>> onlineList() {
        return Result.ok(riderService.listOnline());
    }

    /** 位置上报吞吐统计（演示/压测用，需骑手登录） */
    @GetMapping("/location/stats")
    public Result<Map<String, Object>> locationStats() {
        return Result.ok(Map.of(
                "stats", locationService.stats(),
                "onlineRiders", locationService.onlineCount()
        ));
    }
}
