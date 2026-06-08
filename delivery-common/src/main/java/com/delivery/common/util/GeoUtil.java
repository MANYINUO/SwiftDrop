package com.delivery.common.util;

/**
 * 地理计算工具。
 *
 * <p>两个核心公式：
 * <ul>
 *   <li>{@link #distanceMeters} —— Haversine 公式算球面距离</li>
 *   <li>{@link #cosineSimilarity} —— 用来算「顺路度」：骑手行进方向与取件方向的夹角</li>
 * </ul>
 */
public final class GeoUtil {

    /** 地球平均半径（米） */
    private static final double EARTH_RADIUS_M = 6_371_000.0;

    private GeoUtil() {
    }

    /**
     * Haversine 公式计算两点间球面距离。
     *
     * <p>相比直接算平面欧氏距离，Haversine 在跨纬度时更准确；
     * 同城范围内两者差异不大，但用 Haversine 跨纬度时更严谨。
     *
     * @return 距离（米）
     */
    public static double distanceMeters(double lng1, double lat1, double lng2, double lat2) {
        double radLat1 = Math.toRadians(lat1);
        double radLat2 = Math.toRadians(lat2);
        double deltaLat = radLat1 - radLat2;
        double deltaLng = Math.toRadians(lng1) - Math.toRadians(lng2);

        double a = Math.pow(Math.sin(deltaLat / 2), 2)
                + Math.cos(radLat1) * Math.cos(radLat2) * Math.pow(Math.sin(deltaLng / 2), 2);
        double c = 2 * Math.asin(Math.min(1, Math.sqrt(a)));
        return EARTH_RADIUS_M * c;
    }

    /**
     * 两个方向向量的余弦相似度，取值范围 [-1, 1]。
     *
     * <p>用于计算「顺路度」：
     * <pre>
     *   骑手行进方向向量 = 骑手当前位置 - 骑手上一个位置
     *   取件方向向量     = 取件点 - 骑手当前位置
     * </pre>
     * 相似度接近 1 表示骑手正朝取件点走，接单几乎不增加成本；
     * 接近 -1 表示骑手正朝反方向走，接了要绕回来。
     *
     * @return 余弦相似度；任一向量为零向量时返回 0
     */
    public static double cosineSimilarity(double dx1, double dy1, double dx2, double dy2) {
        double norm1 = Math.sqrt(dx1 * dx1 + dy1 * dy1);
        double norm2 = Math.sqrt(dx2 * dx2 + dy2 * dy2);
        if (norm1 == 0 || norm2 == 0) {
            return 0.0;
        }
        double cos = (dx1 * dx2 + dy1 * dy2) / (norm1 * norm2);
        // 浮点误差可能让它略微超出 [-1, 1]
        return Math.max(-1.0, Math.min(1.0, cos));
    }

    /**
     * 把余弦相似度从 [-1, 1] 线性映射到 [0, 1]，方便参与加权求和。
     */
    public static double normalizeCosine(double cosine) {
        return (cosine + 1.0) / 2.0;
    }

    /**
     * 按平均速度粗算 ETA（秒）。
     *
     * <p>v1 版本：{@code 距离 / 平均速度 + 固定取货耗时}。
     * 后续可加入时段系数（早晚高峰）、天气系数做修正。
     *
     * @param distanceMeters 总距离（骑手→取件点→送达点）
     * @param speedMps       平均速度（米/秒）
     * @param pickupSeconds  固定取货耗时（秒）
     */
    public static int estimateSeconds(double distanceMeters, double speedMps, int pickupSeconds) {
        if (speedMps <= 0) {
            return pickupSeconds;
        }
        return (int) Math.ceil(distanceMeters / speedMps) + pickupSeconds;
    }
}
