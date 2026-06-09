package com.delivery.common.auth;

import com.delivery.common.constant.DeliveryConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

/**
 * 登录令牌服务（演示版：UUID 令牌 + Redis 存储）。
 *
 * <p>登录成功后生成一个随机 token 当作"工牌"，以
 * {@code delivery:auth:{role}:{token}} 为 key、身份主体 ID 为 value 存入 Redis 并设过期；
 * 之后每次请求带上 token，拦截器用 {@link #verify} 换出身份 ID。
 *
 * <p><b>为什么这里不用 JWT</b>：单体演示项目用 UUID + Redis 已足够，而且令牌保存在服务端
 * 带来两个直接好处——登出能立即作废、可以滑动续期。JWT"无状态、跨服务自验签"的优势在
 * 单体里用不上。生产环境可平滑替换为 JWT / Spring Security，对上的登录接口形态不变。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenService {

    private final StringRedisTemplate redis;

    /** 令牌有效期：12 小时（配合 {@link #verify} 的滑动续期） */
    private static final Duration TTL = Duration.ofHours(12);

    /** 签发令牌，返回 token 字符串；subjectId 为该身份对应的业务主键（管理员/骑手/游客 ID） */
    public String issue(AuthRole role, Long subjectId) {
        String token = UUID.randomUUID().toString().replace("-", "");
        redis.opsForValue().set(role.redisKey(token), String.valueOf(subjectId), TTL);
        return token;
    }

    /**
     * 校验令牌并返回身份主体 ID；令牌无效 / 过期返回 {@code null}。
     *
     * <p>校验通过会顺手续期（滑动过期）：只要用户保持活跃，就不会在操作途中被突然踢下线。
     */
    public Long verify(AuthRole role, String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        String key = role.redisKey(token);
        String val = redis.opsForValue().get(key);
        if (val == null || val.isBlank()) {
            return null;
        }
        // 滑动续期：把过期时间往后推一个 TTL
        redis.expire(key, TTL);
        try {
            return Long.valueOf(val);
        } catch (NumberFormatException e) {
            log.warn("令牌身份值非法 role={} value={}", role, val);
            return null;
        }
    }

    /** 登出：删除令牌，使其立即失效 */
    public void revoke(AuthRole role, String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        redis.delete(role.redisKey(token));
    }

    /**
     * 分配一个新的游客用户 ID（只增不重复，避开演示用户 1001）。
     *
     * <p>用 Redis 自增序列，保证多个浏览器首次访问并并发领取时也不会拿到同一个 ID。
     */
    public long nextGuestUserId() {
        Long seq = redis.opsForValue().increment(DeliveryConstants.REDIS_KEY_AUTH_GUEST_SEQ);
        long s = seq == null ? 1L : seq;
        return DeliveryConstants.GUEST_USER_ID_START + s;
    }
}
