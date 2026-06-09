package com.delivery.common.auth;

import com.delivery.common.exception.ErrorCode;
import com.delivery.common.result.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 服务端鉴权过滤器（真正的安全关卡）。
 *
 * <p><b>为什么用 Filter 而不是 MVC 的 HandlerInterceptor</b>：本过滤器需要在认证通过后，
 * 用 {@link HttpServletRequestWrapper} 把 {@code X-User-Id}/{@code X-Admin-Id} 覆写成令牌里的
 * 真实身份再传给下游；只有 Filter 能通过 {@code chain.doFilter(wrappedRequest, response)}
 * 替换请求对象，HandlerInterceptor 不经过 chain、无法替换下游请求。鉴权语义两者等价。
 *
 * <p>前端路由守卫只负责体验（把人引导到登录页），可以被绕过；这里在请求到达 Controller 之前
 * 强制校验令牌——即使绕过页面、用 Postman 直接敲接口也会被挡下，这才是"真上锁"。
 *
 * <ul>
 *   <li>非 /api 请求（含 /ws 握手、静态资源）与 OPTIONS 预检：放行</li>
 *   <li>/api/auth/** 与骑手登录名单 /api/rider/options：放行</li>
 *   <li>/api/admin/**：需管理员令牌，通过后覆写 X-Admin-Id</li>
 *   <li>/api/user/**：需游客令牌，通过后覆写 X-User-Id（前端无法伪造他人 ID）</li>
 *   <li>/api/rider/list、/online、/location/stats：需骑手令牌但不比对编号</li>
 *   <li>/api/rider/{riderId}/**：需骑手令牌且编号必须一致，不一致 403（水平越权），
 *       编号无法解析 401（fail-closed，绝不"解析失败就放行"）</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthFilter extends OncePerRequestFilter {

    private final TokenService tokenService;
    private final ObjectMapper objectMapper;

    private static final AntPathMatcher MATCHER = new AntPathMatcher();
    private static final String RIDER_ONE = "/api/rider/{riderId}";
    private static final String RIDER_SUB = "/api/rider/{riderId}/**";

    /** 登录后骑手可访问、但不针对某个具体骑手的集合型接口（不比对编号） */
    private static final Set<String> RIDER_AUTHED_SET = new HashSet<>(Arrays.asList(
            "/api/rider/list", "/api/rider/online", "/api/rider/location/stats"));

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        // CORS 预检请求不带令牌，必须放行（跨域头由 CorsConfig 处理）
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)) {
            uri = uri.substring(contextPath.length());
        }

        // 非业务接口（含 /ws 的 WebSocket 握手、静态资源）不在此鉴权，WS 由握手拦截器负责
        if (!uri.startsWith("/api/")) {
            chain.doFilter(request, response);
            return;
        }
        // 登录 / 登出接口本身放行（登录前不可能已有令牌）
        if (uri.startsWith("/api/auth/")) {
            chain.doFilter(request, response);
            return;
        }

        String token = AuthTokens.bearer(request.getHeader(AuthTokens.HEADER));

        try {
            // ---------------- 调度后台 ----------------
            if (uri.startsWith("/api/admin/")) {
                Long adminId = tokenService.verify(AuthRole.ADMIN, token);
                if (adminId == null) {
                    rejectAuthedOrAnonymous(AuthRole.ADMIN, token, uri, response);
                    return;
                }
                // 以后端令牌中的管理员为准，覆盖前端可能伪造的 X-Admin-Id
                chain.doFilter(
                        new HeaderOverrideWrapper(request, Map.of("X-Admin-Id", String.valueOf(adminId))),
                        response);
                return;
            }

            // ---------------- 顾客端（匿名游客令牌）----------------
            if (uri.startsWith("/api/user/")) {
                Long userId = tokenService.verify(AuthRole.GUEST, token);
                if (userId == null) {
                    rejectAuthedOrAnonymous(AuthRole.GUEST, token, uri, response);
                    return;
                }
                // 关键：用户身份由后端令牌决定，改 localStorage 也无法冒充别人下单 / 查单
                chain.doFilter(
                        new HeaderOverrideWrapper(request, Map.of("X-User-Id", String.valueOf(userId))),
                        response);
                return;
            }

            // ---------------- 骑手端 ----------------
            if (uri.startsWith("/api/rider/")) {
                // 1) 登录页骑手名单：公开（精简字段，不含手机号）
                if ("/api/rider/options".equals(uri)) {
                    chain.doFilter(request, response);
                    return;
                }
                // 2) 集合型接口：登录骑手可访问，不比对编号（必须排在 {riderId} 模板判断之前）
                if (RIDER_AUTHED_SET.contains(uri)) {
                    if (tokenService.verify(AuthRole.RIDER, token) == null) {
                        rejectAuthedOrAnonymous(AuthRole.RIDER, token, uri, response);
                        return;
                    }
                    chain.doFilter(request, response);
                    return;
                }
                // 3) 针对具体骑手的接口：提取路径编号
                String pathRiderId = extractRiderId(uri);
                if (pathRiderId == null) {
                    // /api/rider/ 下未识别的形态，一律不放行
                    reject(response, HttpServletResponse.SC_UNAUTHORIZED, ErrorCode.UNAUTHORIZED);
                    return;
                }
                long pathId;
                try {
                    pathId = Long.parseLong(pathRiderId);
                } catch (NumberFormatException e) {
                    // /api/rider/abc/... 编号非法：按未认证拒绝，绝不"解析失败就放行"
                    reject(response, HttpServletResponse.SC_UNAUTHORIZED, ErrorCode.UNAUTHORIZED);
                    return;
                }
                Long tokenRiderId = tokenService.verify(AuthRole.RIDER, token);
                if (tokenRiderId == null) {
                    rejectAuthedOrAnonymous(AuthRole.RIDER, token, uri, response);
                    return;
                }
                if (!tokenRiderId.equals(pathId)) {
                    // 已登录骑手试图操作他人编号：水平越权
                    log.warn("骑手水平越权被拦截 tokenRiderId={} pathRiderId={} uri={}",
                            tokenRiderId, pathId, uri);
                    reject(response, HttpServletResponse.SC_FORBIDDEN, ErrorCode.FORBIDDEN);
                    return;
                }
                chain.doFilter(request, response);
                return;
            }

            // 其它 /api 接口（本项目无额外敏感 HTTP 接口）放行
            chain.doFilter(request, response);
        } catch (RedisConnectionFailureException e) {
            // 令牌依赖 Redis，连不上时给明确 503，而不是白屏 500
            log.error("鉴权依赖的 Redis 不可用 uri={}", uri, e);
            reject(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, ErrorCode.SERVICE_UNAVAILABLE);
        }
    }

    /** 从 /api/rider/{riderId} 或 /api/rider/{riderId}/** 提取编号；不匹配返回 null */
    private String extractRiderId(String uri) {
        if (MATCHER.match(RIDER_ONE, uri)) {
            return MATCHER.extractUriTemplateVariables(RIDER_ONE, uri).get("riderId");
        }
        if (MATCHER.match(RIDER_SUB, uri)) {
            return MATCHER.extractUriTemplateVariables(RIDER_SUB, uri).get("riderId");
        }
        return null;
    }

    /**
     * 目标角色校验失败时区分“未认证”与“已认证但无权”：
     * 未携带令牌、或令牌在任何角色下都无效（伪造 / 过期）→ 401；
     * 令牌对其它角色有效（已登录但并非本端身份，属垂直越权）→ 403。
     */
    private void rejectAuthedOrAnonymous(AuthRole required, String token, String uri,
                                         HttpServletResponse response) throws IOException {
        boolean authedAsOther = false;
        if (token != null && !token.isBlank()) {
            for (AuthRole role : AuthRole.values()) {
                if (role == required) {
                    continue;
                }
                if (tokenService.verify(role, token) != null) {
                    authedAsOther = true;
                    break;
                }
            }
        }
        if (authedAsOther) {
            log.warn("垂直越权被拦截：令牌不属于所需角色 {}，uri={}", required, uri);
            reject(response, HttpServletResponse.SC_FORBIDDEN, ErrorCode.FORBIDDEN);
        } else {
            reject(response, HttpServletResponse.SC_UNAUTHORIZED, ErrorCode.UNAUTHORIZED);
        }
    }

    /** 写出真实 HTTP 状态码 + 统一 Result JSON（不进入 Controller） */
    private void reject(HttpServletResponse response, int httpStatus, ErrorCode errorCode) throws IOException {
        response.setStatus(httpStatus);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(Result.fail(errorCode)));
    }

    /**
     * 请求头覆写包装：把身份头改成后端从令牌解析出的真实身份。
     *
     * <p>这样 Controller 依旧用 {@code @RequestHeader("X-User-Id"/"X-Admin-Id")} 取值，
     * 一行都不用改；而前端传入的同名头会被覆盖，无法伪造身份。
     */
    private static final class HeaderOverrideWrapper extends HttpServletRequestWrapper {

        private final Map<String, String> overrides;

        HeaderOverrideWrapper(HttpServletRequest request, Map<String, String> overrides) {
            super(request);
            this.overrides = overrides;
        }

        @Override
        public String getHeader(String name) {
            if (name != null) {
                for (Map.Entry<String, String> e : overrides.entrySet()) {
                    if (e.getKey().equalsIgnoreCase(name)) {
                        return e.getValue();
                    }
                }
            }
            return super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            if (name != null) {
                for (Map.Entry<String, String> e : overrides.entrySet()) {
                    if (e.getKey().equalsIgnoreCase(name)) {
                        return Collections.enumeration(Collections.singletonList(e.getValue()));
                    }
                }
            }
            return super.getHeaders(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            Set<String> names = new HashSet<>();
            Enumeration<String> origin = super.getHeaderNames();
            if (origin != null) {
                names.addAll(Collections.list(origin));
            }
            names.addAll(overrides.keySet());
            return Collections.enumeration(names);
        }
    }
}
