package com.delivery.common.auth;

import com.delivery.common.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 鉴权过滤器测试 —— 覆盖「防越权」这条安全底线。
 *
 * <p>AuthFilter 是整个项目唯一真正的安全关卡（前端路由守卫可被绕过）。
 * 这里验证四类行为：
 * <ol>
 *   <li><b>放行</b>：非 /api、登录接口、OPTIONS 预检、骑手名单</li>
 *   <li><b>身份覆写</b>：前端伪造的 X-User-Id / X-Admin-Id 必须被令牌真实身份覆盖</li>
 *   <li><b>水平越权</b>：骑手 A 不能操作骑手 B 的路径 → 403</li>
 *   <li><b>垂直越权</b>：游客令牌访问 /api/admin → 403（而非 401）</li>
 * </ol>
 *
 * <p>最后一类尤其重要：区分 401 与 403 本身就是安全设计的一部分——
 * 401 表示「你没登录」，403 表示「你登录了但不该来这里」。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("鉴权过滤器 AuthFilter")
class AuthFilterTest {

    @Mock
    private TokenService tokenService;

    private AuthFilter authFilter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockFilterChain chain;

    @BeforeEach
    void setUp() {
        authFilter = new AuthFilter(tokenService, new ObjectMapper());
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        chain = new MockFilterChain();
        // 默认：任何角色下令牌都无效
        when(tokenService.verify(any(AuthRole.class), any())).thenReturn(null);
    }

    /** 执行过滤器，返回是否放行到下一个过滤器 */
    private boolean doFilter() throws Exception {
        authFilter.doFilter(request, response, chain);
        return chain.getRequest() != null;
    }

    /** 取下游实际收到的请求（可能已被 HeaderOverrideWrapper 包装） */
    private HttpServletRequest downstreamRequest() {
        return (HttpServletRequest) chain.getRequest();
    }

    // ==================================================================
    //  放行
    // ==================================================================

    @Nested
    @DisplayName("放行规则")
    class PassThrough {

        @Test
        @DisplayName("非 /api 请求直接放行（如静态资源、WS 握手）")
        void nonApiRequestPasses() throws Exception {
            request.setRequestURI("/ws/user/SD001");
            assertThat(doFilter()).isTrue();
            verify(tokenService, never()).verify(any(), any());
        }

        @ParameterizedTest(name = "{0} 属于登录接口，放行")
        @ValueSource(strings = {
                "/api/auth/admin/login",
                "/api/auth/rider/login",
                "/api/auth/guest",
                "/api/auth/logout"
        })
        @DisplayName("登录相关接口无需令牌")
        void authEndpointsPass(String uri) throws Exception {
            request.setRequestURI(uri);
            assertThat(doFilter()).isTrue();
            verify(tokenService, never()).verify(any(), any());
        }

        @Test
        @DisplayName("OPTIONS 预检请求放行（浏览器不带令牌）")
        void optionsPreflightPasses() throws Exception {
            request.setRequestURI("/api/admin/orders");
            request.setMethod("OPTIONS");
            assertThat(doFilter()).isTrue();
        }

        @Test
        @DisplayName("骑手登录页名单接口公开（不含手机号）")
        void riderOptionsIsPublic() throws Exception {
            request.setRequestURI("/api/rider/options");
            assertThat(doFilter()).isTrue();
        }
    }

    // ==================================================================
    //  身份覆写（防伪造）
    // ==================================================================

    @Nested
    @DisplayName("身份覆写")
    class IdentityOverride {

        @Test
        @DisplayName("顾客端：前端伪造的 X-User-Id 被令牌真实身份覆盖")
        void forgedUserIdIsOverridden() throws Exception {
            request.setRequestURI("/api/user/orders");
            request.addHeader(AuthTokens.HEADER, "Bearer guest-token");
            // 前端谎称自己是 9999
            request.addHeader("X-User-Id", "9999");
            when(tokenService.verify(AuthRole.GUEST, "guest-token")).thenReturn(5001L);

            assertThat(doFilter()).isTrue();

            // 关键：下游看到的是令牌里的 5001，而不是前端写的 9999
            assertThat(downstreamRequest().getHeader("X-User-Id")).isEqualTo("5001");
        }

        @Test
        @DisplayName("后台：前端伪造的 X-Admin-Id 被令牌真实身份覆盖")
        void forgedAdminIdIsOverridden() throws Exception {
            request.setRequestURI("/api/admin/orders");
            request.addHeader(AuthTokens.HEADER, "Bearer admin-token");
            request.addHeader("X-Admin-Id", "9999");
            when(tokenService.verify(AuthRole.ADMIN, "admin-token")).thenReturn(1L);

            assertThat(doFilter()).isTrue();
            assertThat(downstreamRequest().getHeader("X-Admin-Id")).isEqualTo("1");
        }

        @Test
        @DisplayName("令牌大小写 Bearer 前缀都兼容")
        void bearerPrefixIsCaseInsensitive() throws Exception {
            request.setRequestURI("/api/admin/orders");
            request.addHeader(AuthTokens.HEADER, "bearer admin-token");
            when(tokenService.verify(AuthRole.ADMIN, "admin-token")).thenReturn(1L);

            assertThat(doFilter()).isTrue();
            assertThat(downstreamRequest().getHeader("X-Admin-Id")).isEqualTo("1");
        }
    }

    // ==================================================================
    //  水平越权：骑手操作他人编号
    // ==================================================================

    @Nested
    @DisplayName("水平越权")
    class HorizontalPrivilege {

        @Test
        @DisplayName("骑手 A 访问 /api/rider/B/... 被 403 拦截")
        void riderCannotAccessOthersPath() throws Exception {
            request.setRequestURI("/api/rider/12/orders");
            request.addHeader(AuthTokens.HEADER, "Bearer rider-token");
            // 令牌属于骑手 11
            when(tokenService.verify(AuthRole.RIDER, "rider-token")).thenReturn(11L);

            assertThat(doFilter()).isFalse();
            assertThat(response.getStatus()).isEqualTo(403);
            assertThat(response.getContentAsString()).contains(String.valueOf(ErrorCode.FORBIDDEN.getCode()));
        }

        @Test
        @DisplayName("骑手访问自己的路径正常放行")
        void riderCanAccessOwnPath() throws Exception {
            request.setRequestURI("/api/rider/11/orders");
            request.addHeader(AuthTokens.HEADER, "Bearer rider-token");
            when(tokenService.verify(AuthRole.RIDER, "rider-token")).thenReturn(11L);

            assertThat(doFilter()).isTrue();
        }

        @Test
        @DisplayName("骑手编号非数字：按未认证拒绝，绝不「解析失败就放行」")
        void nonNumericRiderIdIsRejected() throws Exception {
            request.setRequestURI("/api/rider/abc/orders");
            request.addHeader(AuthTokens.HEADER, "Bearer rider-token");
            when(tokenService.verify(AuthRole.RIDER, "rider-token")).thenReturn(11L);

            assertThat(doFilter()).isFalse();
            assertThat(response.getStatus()).isEqualTo(401);
        }

        @Test
        @DisplayName("骑手未登录访问自己路径：401")
        void unauthenticatedRiderGets401() throws Exception {
            request.setRequestURI("/api/rider/11/orders");
            // 无令牌

            assertThat(doFilter()).isFalse();
            assertThat(response.getStatus()).isEqualTo(401);
        }
    }

    // ==================================================================
    //  垂直越权：角色错配
    // ==================================================================

    @Nested
    @DisplayName("垂直越权")
    class VerticalPrivilege {

        @Test
        @DisplayName("游客令牌访问后台接口：403（已登录但无权），不是 401")
        void guestTokenOnAdminApiGets403() throws Exception {
            request.setRequestURI("/api/admin/orders");
            request.addHeader(AuthTokens.HEADER, "Bearer guest-token");
            // 对 ADMIN 无效
            when(tokenService.verify(AuthRole.ADMIN, "guest-token")).thenReturn(null);
            // 但对 GUEST 有效 —— 说明「已登录，只是走错端」
            when(tokenService.verify(AuthRole.GUEST, "guest-token")).thenReturn(5001L);

            assertThat(doFilter()).isFalse();
            assertThat(response.getStatus()).isEqualTo(403);
        }

        @Test
        @DisplayName("骑手令牌访问后台接口：403")
        void riderTokenOnAdminApiGets403() throws Exception {
            request.setRequestURI("/api/admin/metrics");
            request.addHeader(AuthTokens.HEADER, "Bearer rider-token");
            when(tokenService.verify(AuthRole.ADMIN, "rider-token")).thenReturn(null);
            when(tokenService.verify(AuthRole.RIDER, "rider-token")).thenReturn(11L);

            assertThat(doFilter()).isFalse();
            assertThat(response.getStatus()).isEqualTo(403);
        }

        @Test
        @DisplayName("完全无效的令牌访问后台：401")
        void invalidTokenGets401() throws Exception {
            request.setRequestURI("/api/admin/orders");
            request.addHeader(AuthTokens.HEADER, "Bearer garbage");

            assertThat(doFilter()).isFalse();
            assertThat(response.getStatus()).isEqualTo(401);
            assertThat(response.getContentAsString())
                    .contains(String.valueOf(ErrorCode.UNAUTHORIZED.getCode()));
        }

        @Test
        @DisplayName("无令牌访问顾客端：401")
        void noTokenOnUserApiGets401() throws Exception {
            request.setRequestURI("/api/user/orders");
            assertThat(doFilter()).isFalse();
            assertThat(response.getStatus()).isEqualTo(401);
        }
    }

    // ==================================================================
    //  骑手集合型接口
    // ==================================================================

    @Nested
    @DisplayName("骑手集合型接口")
    class RiderCollectionApis {

        @ParameterizedTest(name = "{0} 只需骑手登录，不比对编号")
        @ValueSource(strings = {
                "/api/rider/list",
                "/api/rider/online",
                "/api/rider/location/stats"
        })
        @DisplayName("集合型接口登录即可访问")
        void collectionApisOnlyNeedLogin(String uri) throws Exception {
            request.setRequestURI(uri);
            request.addHeader(AuthTokens.HEADER, "Bearer rider-token");
            when(tokenService.verify(AuthRole.RIDER, "rider-token")).thenReturn(11L);

            assertThat(doFilter()).isTrue();
        }

        @Test
        @DisplayName("集合型接口未登录：401")
        void collectionApisRequireLogin() throws Exception {
            request.setRequestURI("/api/rider/list");
            assertThat(doFilter()).isFalse();
            assertThat(response.getStatus()).isEqualTo(401);
        }
    }

    // ==================================================================
    //  降级：Redis 不可用
    // ==================================================================

    @Nested
    @DisplayName("依赖降级")
    class Degradation {

        @Test
        @DisplayName("Redis 不可用：返回 503 而不是裸 500")
        void redisDownReturns503() throws Exception {
            request.setRequestURI("/api/admin/orders");
            request.addHeader(AuthTokens.HEADER, "Bearer admin-token");
            when(tokenService.verify(any(AuthRole.class), anyString()))
                    .thenThrow(new RedisConnectionFailureException("connection refused"));

            assertThat(doFilter()).isFalse();
            assertThat(response.getStatus()).isEqualTo(503);
            assertThat(response.getContentAsString())
                    .contains(String.valueOf(ErrorCode.SERVICE_UNAVAILABLE.getCode()));
        }
    }
}
