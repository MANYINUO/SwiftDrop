package com.delivery.common.constant;

/**
 * 全局常量：Redis Key、业务超时、默认值。
 *
 * <p>集中管理避免魔法值散落在各处。
 */
public final class DeliveryConstants {

    private DeliveryConstants() {
    }

    // ==================== Redis Key ====================

    /** 骑手实时位置 GEO 集合（在线骑手才有） */
    public static final String REDIS_KEY_RIDER_GEO = "delivery:rider:geo";

    /** 骑手在线标记 Hash：riderId -> 1 */
    public static final String REDIS_KEY_RIDER_ONLINE = "delivery:rider:online";

    /** 骑手在手单数：delivery:rider:load:{riderId} */
    public static final String REDIS_KEY_RIDER_LOAD_PREFIX = "delivery:rider:load:";

    /** 派单抢占锁：delivery:order:lock:{orderId} */
    public static final String REDIS_KEY_ORDER_LOCK_PREFIX = "delivery:order:lock:";

    /** 幂等键前缀：delivery:idem:{bizType}:{key} */
    public static final String REDIS_KEY_IDEMPOTENT_PREFIX = "delivery:idem:";

    /** 登录令牌前缀：delivery:auth:{role}:{token}，值为身份主体 ID */
    public static final String REDIS_KEY_AUTH_PREFIX = "delivery:auth:";

    /** 游客自增序号 key：delivery:auth:guest:seq（与具体令牌 key 不冲突） */
    public static final String REDIS_KEY_AUTH_GUEST_SEQ = "delivery:auth:guest:seq";

    /** 游客用户 ID 起始值（避开演示用户 1001） */
    public static final long GUEST_USER_ID_START = 5000L;

    // ==================== 派单参数 ====================

    /** 派单响应超时（秒）—— 超过这个时间骑手未响应即视为超时 */
    public static final int DISPATCH_RESPONSE_TIMEOUT_SECONDS = 15;

    /** 每轮派单最多推送的候选骑手数（抢单模式） */
    public static final int DISPATCH_CANDIDATE_LIMIT = 10;

    /** 搜索半径阶梯（米）：第 1 轮 1km，第 2 轮 3km，第 3 轮 5km */
    public static final int[] DISPATCH_RADIUS_STEPS_METERS = {1000, 3000, 5000};

    /** 最大派单轮次，超过则转人工调度 */
    public static final int DISPATCH_MAX_ROUND = 3;

    /** 派单超时扫描间隔（毫秒） */
    public static final long DISPATCH_TIMEOUT_SCAN_INTERVAL_MS = 5000L;

    /** 单次扫描最多处理的超时任务数 */
    public static final int DISPATCH_TIMEOUT_SCAN_BATCH = 200;

    // ==================== 多目标打分权重（v2 模型）====================
    // 五个因子权重之和建议为 1.0，便于解释"每个因子贡献多少"

    /** 距离分权重：越近越好 */
    public static final double WEIGHT_DISTANCE = 0.35;

    /** 负载分权重：在手单越少越好 */
    public static final double WEIGHT_LOAD = 0.20;

    /** 顺路分权重：骑手行进方向与取件方向越一致越好 */
    public static final double WEIGHT_ROUTE_MATCH = 0.25;

    /** 评分分权重：历史评分越高越好 */
    public static final double WEIGHT_SCORE = 0.10;

    /** 时效风险分权重：预计送达越不超时越好 */
    public static final double WEIGHT_TIME_RISK = 0.10;

    /**
     * 超时惩罚系数。
     *
     * <p>设计取舍：<b>拒单的骑手永久排除，超时的骑手只降权不排除</b>。
     * 因为超时可能只是当时在忙、信号差或没看到推送，直接永久排除会让订单
     * 很快陷入「无候选 → 转人工」；而拒单是骑手明确的意愿，必须尊重。
     */
    public static final double TIMEOUT_PENALTY = 0.6;

    // ==================== 业务默认值 ====================

    /** 默认最大同时接单数 */
    public static final int DEFAULT_MAX_CAPACITY = 1;

    /** 骑手位置上报最小间隔（毫秒）——低于此间隔的重复上报直接丢弃（防抖） */
    public static final long LOCATION_REPORT_MIN_INTERVAL_MS = 2000L;

    /** 向用户推送位置的最小间隔（毫秒）——避免高频推送打爆前端 */
    public static final long LOCATION_PUSH_MIN_INTERVAL_MS = 3000L;

    /** 幂等键默认有效期（秒） */
    public static final long IDEMPOTENT_TTL_SECONDS = 600L;

    /** 配送平均速度（米/秒），约 18km/h，用于 ETA 粗算 */
    public static final double AVG_DELIVERY_SPEED_MPS = 5.0;

    /** 取货固定耗时（秒），用于 ETA 粗算 */
    public static final int PICKUP_FIXED_SECONDS = 300;
}
