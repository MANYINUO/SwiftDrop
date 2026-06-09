package com.delivery.auth.controller;

import com.delivery.auth.dto.AdminLoginRequest;
import com.delivery.auth.dto.LoginVO;
import com.delivery.auth.dto.RiderLoginRequest;
import com.delivery.common.auth.AuthRole;
import com.delivery.common.auth.AuthTokens;
import com.delivery.common.auth.TokenService;
import com.delivery.common.config.DeliveryProperties;
import com.delivery.common.exception.BizException;
import com.delivery.common.exception.ErrorCode;
import com.delivery.common.result.Result;
import com.delivery.rider.entity.Rider;
import com.delivery.rider.service.RiderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 登录 / 登出入口。
 *
 * <ul>
 *   <li>POST /api/auth/admin/login  后台账号密码登录</li>
 *   <li>POST /api/auth/rider/login  骑手编号 + 手机号后 4 位登录</li>
 *   <li>POST /api/auth/guest        顾客端匿名登录（前端首次打开时静默调用）</li>
 *   <li>POST /api/auth/logout       登出并作废令牌</li>
 * </ul>
 *
 * <p>这些接口本身在拦截器里被放行（登录前当然不能要求已登录）。
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final TokenService tokenService;
    private final DeliveryProperties properties;
    private final RiderService riderService;

    /** 后台登录：校验配置文件里的演示账号，签发管理员令牌 */
    @PostMapping("/admin/login")
    public Result<LoginVO> adminLogin(@Valid @RequestBody AdminLoginRequest req) {
        DeliveryProperties.Auth.Admin cfg = properties.getAuth().getAdmin();
        if (!cfg.getUsername().equals(req.getUsername())
                || !cfg.getPassword().equals(req.getPassword())) {
            throw new BizException(ErrorCode.AUTH_LOGIN_FAILED, "账号或密码错误");
        }
        String token = tokenService.issue(AuthRole.ADMIN, cfg.getId());
        log.info("后台管理员登录成功 adminId={} username={}", cfg.getId(), cfg.getUsername());
        return Result.ok(LoginVO.of(token, AuthRole.ADMIN.name().toLowerCase(),
                cfg.getId(), cfg.getName()));
    }

    /**
     * 骑手登录：骑手编号 + 手机号后 4 位。
     * 骑手不存在时 getEntity 会抛 RIDER_NOT_FOUND；校验码错误抛 AUTH_LOGIN_FAILED。
     */
    @PostMapping("/rider/login")
    public Result<LoginVO> riderLogin(@Valid @RequestBody RiderLoginRequest req) {
        Rider rider = riderService.getEntity(req.getRiderId());
        String expected = last4(rider.getPhone());
        if (req.getPin() == null || !req.getPin().trim().equals(expected)) {
            // 不区分"编号不存在"还是"校验码错"，统一模糊提示，避免被枚举
            throw new BizException(ErrorCode.AUTH_LOGIN_FAILED, "骑手编号或校验码错误");
        }
        String token = tokenService.issue(AuthRole.RIDER, rider.getId());
        log.info("骑手登录成功 riderId={} riderNo={}", rider.getId(), rider.getRiderNo());
        return Result.ok(LoginVO.of(token, AuthRole.RIDER.name().toLowerCase(),
                rider.getId(), rider.getName(), rider.getRiderNo()));
    }

    /** 顾客端匿名登录：无需入参，后台分配一个只增、不重复的游客 ID */
    @PostMapping("/guest")
    public Result<LoginVO> guest() {
        long guestId = tokenService.nextGuestUserId();
        String token = tokenService.issue(AuthRole.GUEST, guestId);
        return Result.ok(LoginVO.of(token, AuthRole.GUEST.name().toLowerCase(),
                guestId, "游客" + guestId));
    }

    /** 登出：作废当前令牌。遍历三种角色删除，调用方无需告知自己的角色 */
    @PostMapping("/logout")
    public Result<Void> logout(
            @RequestHeader(value = AuthTokens.HEADER, required = false) String authorization) {
        String token = AuthTokens.bearer(authorization);
        if (token != null) {
            for (AuthRole role : AuthRole.values()) {
                tokenService.revoke(role, token);
            }
        }
        return Result.ok();
    }

    /** 取手机号后 4 位作为骑手演示版登录校验码 */
    private static String last4(String phone) {
        if (phone == null || phone.length() < 4) {
            return "";
        }
        return phone.substring(phone.length() - 4);
    }
}
