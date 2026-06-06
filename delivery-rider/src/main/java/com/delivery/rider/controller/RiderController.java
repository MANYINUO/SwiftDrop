package com.delivery.rider.controller;

import com.delivery.common.result.Result;
import com.delivery.rider.dto.LocationReportRequest;
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
 * 骑手端接口：上下线、位置上报、骑手信息。
 * F2 简化版：用 X-Rider-Id 请求头传骑手ID（后面鉴权阶段改成 token）。
 */
@RestController
@RequestMapping("/api/rider")
@RequiredArgsConstructor
public class RiderController {

    private final RiderService riderService;
    private final RiderLocationService locationService;

    /** 骑手详情 */
    @GetMapping("/{riderId}")
    public Result<RiderVO> detail(@PathVariable Long riderId) {
        return Result.ok(riderService.getById(riderId));
    }

    /** 上线（首次上线必须带经纬度） */
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

    /** 位置上报 */
    @PostMapping("/{riderId}/location")
    public Result<Map<String, Object>> reportLocation(@PathVariable Long riderId,
                                                      @Valid @RequestBody LocationReportRequest req) {
        boolean accepted = locationService.report(riderId, req);
        return Result.ok(Map.of(
                "accepted", accepted,
                "message", accepted ? "已接收" : "上报过于频繁，已丢弃"
        ));
    }

    /** 在线骑手列表 */
    @GetMapping("/online")
    public Result<List<RiderVO>> onlineList() {
        return Result.ok(riderService.listOnline());
    }
}
