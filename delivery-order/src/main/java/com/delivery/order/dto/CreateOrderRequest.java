package com.delivery.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 创建订单请求体：取件地址 + 送达地址 + 物品信息
 * 字段上的 @NotNull/@NotBlank/@Pattern 是参数校验注解，
 * Controller 加 @Valid 后框架自动校验，失败由 GlobalExceptionHandler 统一返回中文提示
 */
@Data
public class CreateOrderRequest {

    @NotNull(message = "取件信息不能为空")
    @Valid  // 级联校验：不加这个，嵌套对象 Address 里的校验规则不会生效
    private Address pickup;

    @NotNull(message = "送达信息不能为空")
    @Valid
    private Address deliver;

    @Size(max = 255, message = "物品描述过长")
    private String goodsDesc;

    @Min(value = 1, message = "重量不合法")
    @Max(value = 100_000, message = "重量不合法（单位：克）")
    private Integer goodsWeightG;

    @Data
    public static class Address implements Serializable {

        @NotNull(message = "经度不能为空")
        @DecimalMin(value = "-180", message = "经度不合法")
        @DecimalMax(value = "180", message = "经度不合法")
        private BigDecimal lng;

        @NotNull(message = "纬度不能为空")
        @DecimalMin(value = "-90", message = "纬度不合法")
        @DecimalMax(value = "90", message = "纬度不合法")
        private BigDecimal lat;

        @NotBlank(message = "地址不能为空")
        @Size(max = 255, message = "地址过长")
        private String address;

        @NotBlank(message = "联系人不能为空")
        @Size(max = 64, message = "联系人过长")
        private String contact;

        @NotBlank(message = "联系电话不能为空")
        @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
        private String phone;
    }
}
