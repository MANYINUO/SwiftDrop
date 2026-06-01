package com.delivery.common.constant;

/**
 * 全局常量类
 * 把所有魔法值集中管理，避免散落在代码各处不好维护
 * 用 final class + 私有构造，防止被实例化
 */
public final class DeliveryConstants {

    private DeliveryConstants() {
    }

    // ==================== Redis Key（B4 阶段用，先定义好）====================

    /** 骑手实时位置 GEO 集合的 key */
    public static final String REDIS_KEY_RIDER_GEO = "delivery:rider:geo";

    /** 骑手在线标记 Hash 的 key：riderId -> 1 */
    public static final String REDIS_KEY_RIDER_ONLINE = "delivery:rider:online";

    /** 骑手在手单数 key 前缀：delivery:rider:load:{riderId} */
    public static final String REDIS_KEY_RIDER_LOAD_PREFIX = "delivery:rider:load:";

    /** 派单抢占锁 key 前缀：delivery:order:lock:{orderId} */
    public static final String REDIS_KEY_ORDER_LOCK_PREFIX = "delivery:order:lock:";

    // ==================== 派单参数 ====================

    /** 派单响应超时（秒）：超过这个时间骑手没响应就算超时 */
    public static final int DISPATCH_RESPONSE_TIMEOUT_SECONDS = 15;

    /** 每轮最多推送给多少个候选骑手 */
    public static final int DISPATCH_CANDIDATE_LIMIT = 10;

    /** 搜索半径阶梯（米）：第1轮1km，第2轮3km，第3轮5km */
    public static final int[] DISPATCH_RADIUS_STEPS_METERS = {1000, 3000, 5000};

    /** 最大派单轮次，超过就转人工调度 */
    public static final int DISPATCH_MAX_ROUND = 3;

    /** 派单超时扫描间隔（毫秒）：定时任务多久扫一次超时任务 */
    public static final long DISPATCH_TIMEOUT_SCAN_INTERVAL_MS = 5000L;

    /** 单次扫描最多处理多少条超时任务 */
    public static final int DISPATCH_TIMEOUT_SCAN_BATCH = 200;

    // ==================== 派单打分权重（5个因子加起来=1.0）====================

    public static final double WEIGHT_DISTANCE = 0.35;     // 距离：越近越好
    public static final double WEIGHT_LOAD = 0.20;         // 负载：在手单越少越好
    public static final double WEIGHT_ROUTE_MATCH = 0.25;  // 顺路度：方向越一致越好
    public static final double WEIGHT_SCORE = 0.10;        // 评分：历史评分越高越好
    public static final double WEIGHT_TIME_RISK = 0.10;    // 时效风险：越不容易超时越好

    /**
     * 超时惩罚系数：超时过的骑手打分乘以 0.6（降权但不排除）
     * 拒单的骑手直接排除，超时的骑手只降权——因为超时可能只是当时在忙或没看到推送
     */
    public static final double TIMEOUT_PENALTY = 0.6;

    // ==================== 业务默认值 ====================

    /** 骑手默认最大同时接单数 */
    public static final int DEFAULT_MAX_CAPACITY = 1;

    /** 位置上报最小间隔（毫秒）：2秒内重复上报直接丢弃（防抖） */
    public static final long LOCATION_REPORT_MIN_INTERVAL_MS = 2000L;

    /** 向用户推送位置的最小间隔（毫秒）：避免高频推送打爆前端 */
    public static final long LOCATION_PUSH_MIN_INTERVAL_MS = 3000L;

    /** 幂等键默认有效期（秒） */
    public static final long IDEMPOTENT_TTL_SECONDS = 600L;

    /** 配送平均速度（米/秒），约 18km/h，用于粗算预计送达时间 */
    public static final double AVG_DELIVERY_SPEED_MPS = 5.0;

    /** 取货固定耗时（秒），用于 ETA 粗算 */
    public static final int PICKUP_FIXED_SECONDS = 300;
}
