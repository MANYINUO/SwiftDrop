package com.delivery.rider.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 骑手位置上报请求。
 * F2 简化版：同步写库，不做 Redis 防抖和批量缓冲。
 */
@Data
public class LocationReportRequest {

    @NotNull(message = "经度不能为空")
    @DecimalMin(value = "-180", message = "经度不合法")
    @DecimalMax(value = "180", message = "经度不合法")
    private BigDecimal lng;

    @NotNull(message = "纬度不能为空")
    @DecimalMin(value = "-90", message = "纬度不合法")
    @DecimalMax(value = "90", message = "纬度不合法")
    private BigDecimal lat;

    /** 配送中的订单号，可为空（骑手空闲时也上报位置） */
    private String orderNo;
}
