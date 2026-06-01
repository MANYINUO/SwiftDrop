package com.delivery.common.util;

/**
 * 地理计算工具类
 * 两个核心方法：
 *   1. distanceMeters —— Haversine 公式算两点间球面距离（米）
 *   2. cosineSimilarity —— 算两个方向向量的余弦相似度（顺路度）
 */
public final class GeoUtil {

    /** 地球平均半径（米） */
    private static final double EARTH_RADIUS_M = 6_371_000.0;

    private GeoUtil() {
    }

    /**
     * Haversine 公式计算两点间球面距离（米）
     *
     * 原理：把经纬度转成弧度，用半正矢公式算球面上两点的圆心角，再乘地球半径
     * 同城范围内和平面距离差异不大，但跨纬度时更准确
     *
     * @param lng1 点1经度
     * @param lat1 点1纬度
     * @param lng2 点2经度
     * @param lat2 点2纬度
     * @return 距离（米）
     */
    public static double distanceMeters(double lng1, double lat1, double lng2, double lat2) {
        double radLat1 = Math.toRadians(lat1);
        double radLat2 = Math.toRadians(lat2);
        double deltaLat = radLat1 - radLat2;
        double deltaLng = Math.toRadians(lng1) - Math.toRadians(lng2);

        // Haversine 公式核心
        double a = Math.pow(Math.sin(deltaLat / 2), 2)
                + Math.cos(radLat1) * Math.cos(radLat2) * Math.pow(Math.sin(deltaLng / 2), 2);
        // min(1, sqrt(a)) 防止浮点误差让 sqrt(a) 略大于1，导致 asin 返回 NaN
        double c = 2 * Math.asin(Math.min(1, Math.sqrt(a)));
        return EARTH_RADIUS_M * c;
    }

    /**
     * 两个方向向量的余弦相似度，取值 [-1, 1]
     *
     * 用来算「顺路度」：
     *   骑手行进方向 = 骑手当前位置 - 骑手上一个位置
     *   取件方向     = 取件点位置 - 骑手当前位置
     * 接近 1 表示骑手正朝取件点走（接单几乎不绕路）
     * 接近 -1 表示骑手正朝反方向走（接了要绕回来）
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
        // 浮点误差可能让结果略微超出 [-1, 1]，夹一下
        return Math.max(-1.0, Math.min(1.0, cos));
    }

    /**
     * 把余弦相似度从 [-1, 1] 线性映射到 [0, 1]，方便参与加权打分
     */
    public static double normalizeCosine(double cosine) {
        return (cosine + 1.0) / 2.0;
    }

    /**
     * 按平均速度粗算预计送达时间（秒）
     * 公式：距离 / 平均速度 + 固定取货耗时
     */
    public static int estimateSeconds(double distanceMeters, double speedMps, int pickupSeconds) {
        if (speedMps <= 0) {
            return pickupSeconds;
        }
        return (int) Math.ceil(distanceMeters / speedMps) + pickupSeconds;
    }
}
