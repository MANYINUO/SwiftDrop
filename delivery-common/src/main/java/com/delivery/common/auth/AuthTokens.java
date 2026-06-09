package com.delivery.common.auth;

/**
 * 鉴权相关的 HTTP 头 / 令牌解析工具。
 *
 * <p>MVC 接口与 WebSocket 握手共用同一套约定，集中在这里避免魔法字符串散落。
 */
public final class AuthTokens {

    /** 标准授权头 */
    public static final String HEADER = "Authorization";

    public static final String BEARER_PREFIX = "Bearer ";

    /**
     * WebSocket 握手时令牌所在的 query 参数名。
     *
     * <p>浏览器原生 WebSocket API 不支持自定义请求头，所以令牌只能放在 {@code ?token=xxx}；
     * 生产环境更严谨的做法是改用一次性短时 ticket，避免令牌长期出现在访问日志里。
     */
    public static final String QUERY_PARAM = "token";

    private AuthTokens() {
    }

    /**
     * 从请求头里取出令牌。兼容三种写法：{@code Bearer xxx}、{@code bearer xxx}、裸 token。
     * 头为空返回 {@code null}。
     */
    public static String bearer(String headerValue) {
        if (headerValue == null) {
            return null;
        }
        String h = headerValue.trim();
        if (h.isEmpty()) {
            return null;
        }
        if (h.toLowerCase().startsWith(BEARER_PREFIX.toLowerCase())) {
            String token = h.substring(BEARER_PREFIX.length()).trim();
            return token.isEmpty() ? null : token;
        }
        return h;
    }
}
