package com.delivery.rider.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 骑手位置上报请求。
 *
 * <p>这是全系统<b>调用频率最高</b>的接口：1 万在线骑手 × 每 3 秒一次 ≈ 3333 QPS。
 * 所以实现上做了两件事：防抖 + 异步批量落库。
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

    /**
     * 配送中的订单号，可为空（骑手空闲时也上报位置）。
     *
     * <p>用 orderNo 而不是 orderId：客户端手里只有订单号（派单任务里给的），
     * 而且推送时也要用订单号做订阅 key —— 全链路统一用 orderNo 才不会有转换缺口。
     */
    private String orderNo;
}
