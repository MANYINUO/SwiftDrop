package com.delivery.common.geo;

import com.delivery.common.constant.DeliveryConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.geo.Circle;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 骑手实时位置仓库（Redis GEO）。
 *
 * <p><b>为什么用 Redis GEO 而不是 MySQL 空间索引</b>：
 * GEO 底层是 ZSet，把经纬度编码成 52 位 geohash 整数作为 score，
 * 查找附近骑手是 O(log N) 且全内存，天然适合「每秒都在变」的骑手位置。
 * MySQL 空间索引虽然也能做，但写多读多的场景下压力大得多。
 *
 * <p>放在 common 是因为 order（用户查位置）、rider（上报位置）、dispatch（找候选）
 * 三个模块都要用它 —— 这是基础设施，不是某个模块的业务逻辑。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RiderGeoRepository {

    private final StringRedisTemplate redis;

    private static final String KEY = DeliveryConstants.REDIS_KEY_RIDER_GEO;

    /** 骑手上线 / 更新位置：GEOADD */
    public void addOrUpdate(Long riderId, double lng, double lat) {
        redis.opsForGeo().add(KEY, new Point(lng, lat), String.valueOf(riderId));
    }

    /** 骑手下线：ZREM */
    public void remove(Long riderId) {
        redis.opsForGeo().remove(KEY, String.valueOf(riderId));
    }

    /**
     * 查找指定坐标附近的骑手，按距离升序。
     *
     * <p>对应 Redis 命令：{@code GEOSEARCH key FROMLONLAT lng lat BYRADIUS r m ASC COUNT n}
     *
     * @param radiusMeters 搜索半径（米）
     * @param limit        最多返回多少个
     */
    public List<NearbyRider> searchNearby(double lng, double lat, double radiusMeters, int limit) {
        Circle circle = new Circle(new Point(lng, lat), new Distance(radiusMeters, Metrics.NEUTRAL));
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = redis.opsForGeo().radius(
                KEY,
                circle,
                RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs()
                        .includeDistance()
                        .includeCoordinates()
                        .sortAscending()
                        .limit(limit)
        );

        if (results == null) {
            return Collections.emptyList();
        }

        return results.getContent().stream()
                .map(this::toNearbyRider)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private NearbyRider toNearbyRider(GeoResult<RedisGeoCommands.GeoLocation<String>> r) {
        try {
            Long riderId = Long.valueOf(r.getContent().getName());
            // Metrics.NEUTRAL 下 getValue() 就是米
            double meters = r.getDistance() == null ? 0d : r.getDistance().getValue();
            Point point = r.getContent().getPoint();
            double lng = point == null ? 0d : point.getX();
            double lat = point == null ? 0d : point.getY();
            return new NearbyRider(riderId, meters, lng, lat);
        } catch (NumberFormatException e) {
            log.warn("GEO 中存在非法的骑手 member: {}", r.getContent().getName());
            return null;
        }
    }

    /**
     * 查某个骑手的当前位置。返回 null 表示该骑手不在线（不在 GEO 集合里）。
     */
    public RiderGeoPoint getPosition(Long riderId) {
        List<Point> points = redis.opsForGeo().position(KEY, String.valueOf(riderId));
        if (points == null || points.isEmpty() || points.get(0) == null) {
            return null;
        }
        Point p = points.get(0);
        return new RiderGeoPoint(riderId, p.getX(), p.getY());
    }

    /**
     * 批量查多个骑手的当前位置（底层一条 GEOPOS 命令），用于骑手列表，
     * 避免对每个骑手各发一次 Redis 往返。只返回当前在线（GEO 集合中存在）的骑手，
     * 按 riderId 建索引。
     */
    public Map<Long, RiderGeoPoint> getPositions(Collection<Long> riderIds) {
        if (riderIds == null || riderIds.isEmpty()) {
            return Collections.emptyMap();
        }
        String[] members = riderIds.stream()
                .map(String::valueOf)
                .toArray(String[]::new);
        List<Point> points = redis.opsForGeo().position(KEY, members);

        Map<Long, RiderGeoPoint> result = new HashMap<>(members.length * 2);
        int i = 0;
        for (Long riderId : riderIds) {
            Point p = (points == null || i >= points.size()) ? null : points.get(i);
            if (p != null) {
                result.put(riderId, new RiderGeoPoint(riderId, p.getX(), p.getY()));
            }
            i++;
        }
        return result;
    }

    /** 在线骑手总数（GEO 集合基数） */
    public long onlineCount() {
        Long size = redis.opsForZSet().zCard(KEY);
        return size == null ? 0L : size;
    }

    /** 清空（仅测试用） */
    public void clear() {
        redis.delete(KEY);
    }

    // ================================================================
    //  上一位置缓存 —— 用来算「顺路度」
    //
    //  顺路度 = 骑手行进方向 与 骑手→取件点方向 的余弦相似度。
    //  要算行进方向就必须知道「上一个位置」，所以每次上报时把旧位置存下来。
    //  成本只是每上报多一次 Redis 读写。
    // ================================================================

    private static final String PREV_POS_PREFIX = "delivery:rider:prevpos:";

    /** 上报新位置前，先把旧位置存起来作为「行进方向」的起点 */
    public void savePreviousPosition(Long riderId, double lng, double lat) {
        redis.opsForValue().set(PREV_POS_PREFIX + riderId, lng + "," + lat, Duration.ofMinutes(30));
    }

    /** 读取上一个位置；返回 null 表示还没有历史位置（首次上报） */
    public double[] getPreviousPosition(Long riderId) {
        return parsePreviousPosition(redis.opsForValue().get(PREV_POS_PREFIX + riderId));
    }

    /**
     * 批量读取多个骑手的上一位置（底层一条 MGET），用于派单候选打分，
     * 避免对每个候选各发一次 GET。只返回有历史位置的骑手，按 riderId 建索引。
     */
    public Map<Long, double[]> getPreviousPositions(Collection<Long> riderIds) {
        if (riderIds == null || riderIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<String> keys = riderIds.stream()
                .map(id -> PREV_POS_PREFIX + id)
                .collect(Collectors.toList());
        List<String> values = redis.opsForValue().multiGet(keys);

        Map<Long, double[]> result = new HashMap<>();
        int i = 0;
        for (Long riderId : riderIds) {
            String raw = (values == null || i >= values.size()) ? null : values.get(i);
            double[] prev = parsePreviousPosition(raw);
            if (prev != null) {
                result.put(riderId, prev);
            }
            i++;
        }
        return result;
    }

    private static double[] parsePreviousPosition(String v) {
        if (v == null || v.isBlank()) {
            return null;
        }
        String[] parts = v.split(",");
        if (parts.length != 2) {
            return null;
        }
        try {
            return new double[]{Double.parseDouble(parts[0]), Double.parseDouble(parts[1])};
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
