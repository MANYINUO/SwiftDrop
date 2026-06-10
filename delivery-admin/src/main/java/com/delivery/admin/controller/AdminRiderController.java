package com.delivery.admin.controller;

import com.delivery.common.result.Result;
import com.delivery.rider.dto.RiderVO;
import com.delivery.rider.service.RiderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 调度后台 · 骑手视图。
 */
@RestController
@RequestMapping("/api/admin/riders")
@RequiredArgsConstructor
public class AdminRiderController {

    private final RiderService riderService;

    /** 全部骑手（含实时位置） */
    @GetMapping
    public Result<List<RiderVO>> list() {
        return Result.ok(riderService.listAll());
    }

    /** 在线骑手 */
    @GetMapping("/online")
    public Result<List<RiderVO>> online() {
        return Result.ok(riderService.listOnline());
    }
}
