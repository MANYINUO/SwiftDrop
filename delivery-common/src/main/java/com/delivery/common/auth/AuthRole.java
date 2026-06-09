package com.delivery.common.auth;

import com.delivery.common.constant.DeliveryConstants;

/**
 * 登录身份角色（RBAC 的最简形态）。
 *
 * <ul>
 *   <li>{@link #ADMIN} 调度后台员工</li>
 *   <li>{@link #RIDER} 骑手</li>
 *   <li>{@link #GUEST} 匿名游客（顾客端免登录，由后台自动分配身份）</li>
 * </ul>
 */
public enum AuthRole {

    ADMIN,
    RIDER,
    GUEST;

    /** 该角色某个令牌在 Redis 中的 key：delivery:auth:{role}:{token} */
    public String redisKey(String token) {
        return DeliveryConstants.REDIS_KEY_AUTH_PREFIX + name().toLowerCase() + ":" + token;
    }
}
