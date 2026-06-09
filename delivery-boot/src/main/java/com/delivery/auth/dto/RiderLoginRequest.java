package com.delivery.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 骑手登录请求。
 *
 * <p>{@code pin} 为骑手手机号后 4 位（演示版校验码）：光知道骑手编号还不够，
 * 必须持有该骑手手机号后 4 位，避免"点谁就能登谁"。
 */
@Data
public class RiderLoginRequest {

    @NotNull(message = "请选择骑手")
    private Long riderId;

    @NotBlank(message = "请输入校验码")
    private String pin;
}
