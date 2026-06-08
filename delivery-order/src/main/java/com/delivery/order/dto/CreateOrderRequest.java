package com.delivery.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 创建订单请求体：取件地址 + 送达地址 + 物品信息。
 *
 * <p>字段上的 {@code @NotNull / @NotBlank / @Pattern} 是 Jakarta Bean Validation 校验注解，
 * Controller 参数加 {@code @Valid} 后由框架自动校验；{@code Address} 字段上的 {@code @Valid}
 * 是「级联校验」——不加它，嵌套对象内部的规则不会生效。校验失败统一由
 * {@code GlobalExceptionHandler} 转成中文提示返回，业务代码里不用手写一堆 if 判空。
 */
@Data
public class CreateOrderRequest {

    @NotNull(message = "取件信息不能为空")
    @Valid
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
