package com.delivery.notify.config;

import com.delivery.common.auth.AuthRole;
import com.delivery.common.auth.AuthTokens;
import com.delivery.common.auth.TokenService;
import com.delivery.order.entity.DeliveryOrder;
import com.delivery.order.mapper.DeliveryOrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * WebSocket 握手鉴权。
 *
 * <p><b>为什么要单独写一个</b>：WebSocket 走的是 {@code WebSocketHandlerMapping}，不是 Spring MVC
 * 的 {@code RequestMappingHandlerMapping}，MVC 拦截器管不到 {@code /ws/**}；本项目的
 * {@code AuthFilter} 也只拦截 {@code /api/**}（{@code /ws} 握手请求被它直接放行）。所以 WebSocket
 * 必须通过握手拦截器在升级连接之前校验身份，校验不通过就拒绝握手、连接根本建立不起来。
 *
 * <p>浏览器原生 WebSocket API 不能自定义请求头，因此令牌放在 query string：
 * {@code /ws/rider/3?token=xxx}（生产更严谨可换成一次性短时 ticket，避免令牌长期留在访问日志）。
 *
 * <ul>
 *   <li>骑手通道 {@code /ws/rider/{riderId}}：需骑手令牌，且令牌骑手编号必须等于路径编号，
 *       否则拒绝——堵死 {@code new WebSocket('/ws/rider/3')} 窃听他人取送地址与金额</li>
 *   <li>用户通道 {@code /ws/user/{orderNo}}：需有效游客令牌，<b>且该订单必须属于令牌对应的
 *       用户</b>，否则拒绝握手。订单号会出现在 URL、分享链接、截图、客服工单里，不能只靠
 *       「高熵随机串、不可枚举」兜底</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthHandshakeInterceptor implements HandshakeInterceptor {

    private final TokenService tokenService;
    private final DeliveryOrderMapper orderMapper;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        URI uri = request.getURI();
        String path = uri.getPath();
        String token = queryToken(uri);

        // ---------------- 骑手通道：令牌 + 编号双重校验 ----------------
        if (path != null && path.startsWith("/ws/rider/")) {
            Long pathId = parseLongOrNull(lastSegment(path));
            Long tokenRiderId = tokenService.verify(AuthRole.RIDER, token);
            if (tokenRiderId == null || pathId == null) {
                log.warn("骑手 WS 握手拒绝（未登录或编号非法）path={}", path);
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return false;
            }
            if (!tokenRiderId.equals(pathId)) {
                log.warn("骑手 WS 水平越权握手拒绝 tokenRiderId={} pathRiderId={}",
                        tokenRiderId, pathId);
                response.setStatusCode(HttpStatus.FORBIDDEN);
                return false;
            }
            return true;
        }

        // ---------------- 用户通道：有效游客 + 订单归属双重校验 ----------------
        if (path != null && path.startsWith("/ws/user/")) {
            Long guestId = tokenService.verify(AuthRole.GUEST, token);
            if (guestId == null) {
                log.warn("用户 WS 握手拒绝（未登录）path={}", path);
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return false;
            }
            String orderNo = lastSegment(path);
            DeliveryOrder order = orderNo == null ? null : orderMapper.selectByOrderNo(orderNo);
            // 订单不存在或不属于当前游客，一律 404：既挡住越权订阅他人骑手位置/状态，
            // 又不向探测方确认「这个订单号是否存在」
            if (order == null || !guestId.equals(order.getUserId())) {
                log.warn("用户 WS 握手拒绝（订单不存在或不属于当前游客）guestId={} orderNo={}",
                        guestId, orderNo);
                response.setStatusCode(HttpStatus.NOT_FOUND);
                return false;
            }
            return true;
        }

        // 未识别的 WS 路径：fail-closed，默认拒绝
        log.warn("未知 WS 握手路径，拒绝 path={}", path);
        response.setStatusCode(HttpStatus.NOT_FOUND);
        return false;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // 握手后的处理不需要
    }

    /** 从 query string 解析 token（浏览器 WebSocket 无法自定义请求头） */
    private static String queryToken(URI uri) {
        String query = uri.getQuery();
        if (query == null || query.isEmpty()) {
            return null;
        }
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = urlDecode(pair.substring(0, eq));
            if (AuthTokens.QUERY_PARAM.equals(key)) {
                return urlDecode(pair.substring(eq + 1));
            }
        }
        return null;
    }

    private static String urlDecode(String s) {
        return URLDecoder.decode(s, StandardCharsets.UTF_8);
    }

    private static String lastSegment(String path) {
        int idx = path.lastIndexOf('/');
        String seg = idx >= 0 ? path.substring(idx + 1) : path;
        return seg.isEmpty() ? null : seg;
    }

    private static Long parseLongOrNull(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
