package com.delivery.dispatch.service;

import com.delivery.common.constant.DeliveryConstants;
import com.delivery.common.geo.NearbyRider;
import com.delivery.common.geo.RiderGeoRepository;
import com.delivery.common.util.GeoUtil;
import com.delivery.dispatch.dto.RiderCandidate;
import com.delivery.rider.entity.Rider;
import com.delivery.rider.mapper.RiderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 候选骑手选择器 —— <b>v2 多目标打分模型</b>。
 *
 * <p><b>为什么不能只按距离排</b>：
 * 最近的骑手可能已经在送 3 单、可能正朝反方向骑、可能评分很低、
 * 可能送到后必然超时。只按距离排序，会把这些情况全部忽略。
 *
 * <p><b>五个因子</b>（权重见 {@link DeliveryConstants}）：
 * <pre>
 *   score = w1 × 距离分 + w2 × 负载分 + w3 × 顺路分 + w4 × 评分分 + w5 × 时效风险分
 *
 *   距离分     = 1 - min(骑手到取件点距离 / 搜索半径, 1)
 *   负载分     = 1 - 在手单数 / 最大承载
 *   顺路分     = (余弦相似度 + 1) / 2，其中余弦 = 骑手行进方向 · 骑手→取件点方向
 *   评分分     = 历史评分 / 5
 *   时效风险分 = 1 - min(max(0, (预计送达 - 承诺时效) / 承诺时效), 1)
 * </pre>
 *
 * <p><b>顺路分</b>是这套模型里最能体现「懂业务」的部分：骑手正朝取件点方向骑，
 * 接单几乎不增加成本；正朝反方向骑，接了要绕回来。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CandidateSelector {

    private final RiderGeoRepository geoRepository;
    private final RiderMapper riderMapper;

    /**
     * 筛选并排序候选骑手。
     *
     * @param promisedSeconds 订单承诺的配送时长（秒），用于算时效风险
     * @param radiusMeters    本轮搜索半径（米）
     * @param limit           最多返回多少个候选
     */
    public List<RiderCandidate> select(double pickupLng, double pickupLat,
                                       double deliverLng, double deliverLat,
                                       long promisedSeconds,
                                       int radiusMeters, int limit) {

        // ---------- 1. GEO 搜索：多取一些，后面会过滤掉满载的 ----------
        int fetch = Math.max(limit * 4, 20);
        List<NearbyRider> nearby = geoRepository.searchNearby(pickupLng, pickupLat, radiusMeters, fetch);
        if (nearby.isEmpty()) {
            log.info("半径 {} 米内没有在线骑手", radiusMeters);
            return Collections.emptyList();
        }

        // ---------- 2. 批量查骑手信息（一次 SQL，避免 N+1）----------
        List<Long> ids = nearby.stream().map(NearbyRider::getRiderId).collect(Collectors.toList());
        Map<Long, Rider> riderMap = riderMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Rider::getId, r -> r));

        // 上一位置也一次性 MGET 取回，避免 score() 里每个候选各发一次 Redis GET
        Map<Long, double[]> prevPositionMap = geoRepository.getPreviousPositions(ids);

        // ---------- 3. 逐个过滤 + 打分 ----------
        List<RiderCandidate> candidates = new ArrayList<>(nearby.size());
        for (NearbyRider n : nearby) {
            Rider rider = riderMap.get(n.getRiderId());
            if (rider == null || !rider.isOnline()) {
                continue;
            }
            int load = rider.getDeliveringCount() == null ? 0 : rider.getDeliveringCount();
            int capacity = rider.getMaxCapacity() == null
                    ? DeliveryConstants.DEFAULT_MAX_CAPACITY : rider.getMaxCapacity();

            // 满载骑手直接排除 —— 这是「空闲骑手」的判定
            if (load >= capacity) {
                continue;
            }

            candidates.add(score(rider, n, pickupLng, pickupLat, deliverLng, deliverLat,
                    promisedSeconds, radiusMeters, prevPositionMap.get(n.getRiderId())));
        }

        // ---------- 4. 按综合得分降序 ----------
        candidates.sort(Comparator.comparingDouble(RiderCandidate::getScore).reversed());

        if (candidates.size() > limit) {
            return new ArrayList<>(candidates.subList(0, limit));
        }
        return candidates;
    }

    private RiderCandidate score(Rider rider, NearbyRider nearby,
                                 double pickupLng, double pickupLat,
                                 double deliverLng, double deliverLat,
                                 long promisedSeconds, int radiusMeters,
                                 double[] prev) {

        RiderCandidate c = new RiderCandidate();
        c.setRiderId(rider.getId());
        c.setRiderNo(rider.getRiderNo());
        c.setRiderName(rider.getName());
        c.setDistanceToPickup(nearby.getDistanceMeters());
        c.setDeliveringCount(rider.getDeliveringCount() == null ? 0 : rider.getDeliveringCount());
        c.setMaxCapacity(rider.getMaxCapacity() == null
                ? DeliveryConstants.DEFAULT_MAX_CAPACITY : rider.getMaxCapacity());
        c.setRating(rider.getScore() == null ? 0d : rider.getScore().doubleValue());

        // ---------- 因子 1：距离分 ----------
        double distanceScore = 1 - Math.min(nearby.getDistanceMeters() / radiusMeters, 1.0);

        // ---------- 因子 2：负载分 ----------
        double loadScore = 1 - Math.min(
                (double) c.getDeliveringCount() / c.getMaxCapacity(), 1.0);

        // ---------- 因子 3：顺路分 ----------
        double cosine = 0d;
        double routeScore = 0.5;   // 拿不到历史位置时给中性值，不偏袒也不惩罚
        if (prev != null) {
            // 行进方向 = 当前位置 - 上一个位置
            double moveDx = nearby.getLng() - prev[0];
            double moveDy = nearby.getLat() - prev[1];
            // 指向取件点方向 = 取件点 - 当前位置
            double toPickupDx = pickupLng - nearby.getLng();
            double toPickupDy = pickupLat - nearby.getLat();

            cosine = GeoUtil.cosineSimilarity(moveDx, moveDy, toPickupDx, toPickupDy);
            routeScore = GeoUtil.normalizeCosine(cosine);
        }

        // ---------- 因子 4：评分分 ----------
        double ratingScore = c.getRating() / 5.0;

        // ---------- 因子 5：时效风险分 ----------
        double toDeliver = GeoUtil.distanceMeters(
                pickupLng, pickupLat, deliverLng, deliverLat);
        double totalMeters = nearby.getDistanceMeters() + toDeliver;
        int eta = GeoUtil.estimateSeconds(totalMeters,
                DeliveryConstants.AVG_DELIVERY_SPEED_MPS,
                DeliveryConstants.PICKUP_FIXED_SECONDS);
        double risk = promisedSeconds <= 0 ? 0d
                : Math.max(0d, (eta - promisedSeconds) / (double) promisedSeconds);
        double timeRiskScore = 1 - Math.min(risk, 1.0);

        // ---------- 加权求和 ----------
        double total = DeliveryConstants.WEIGHT_DISTANCE * distanceScore
                + DeliveryConstants.WEIGHT_LOAD * loadScore
                + DeliveryConstants.WEIGHT_ROUTE_MATCH * routeScore
                + DeliveryConstants.WEIGHT_SCORE * ratingScore
                + DeliveryConstants.WEIGHT_TIME_RISK * timeRiskScore;

        c.setDistanceScore(distanceScore);
        c.setLoadScore(loadScore);
        c.setRouteScore(routeScore);
        c.setRatingScore(ratingScore);
        c.setTimeRiskScore(timeRiskScore);
        c.setCosine(cosine);
        c.setEtaSeconds(eta);
        c.setScore(total);
        return c;
    }
}
