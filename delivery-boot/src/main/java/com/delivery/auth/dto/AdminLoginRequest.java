package com.delivery.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 后台管理员登录请求。 */
@Data
public class AdminLoginRequest {

    @NotBlank(message = "请输入账号")
    private String username;

    @NotBlank(message = "请输入密码")
    private String password;
}
