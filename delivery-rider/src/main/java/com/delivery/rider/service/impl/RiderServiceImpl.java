package com.delivery.rider.service.impl;

import com.delivery.common.exception.BizException;
import com.delivery.common.exception.ErrorCode;
import com.delivery.common.geo.RiderGeoPoint;
import com.delivery.common.geo.RiderGeoRepository;
import com.delivery.rider.dto.RiderOptionVO;
import com.delivery.rider.dto.RiderVO;
import com.delivery.rider.entity.Rider;
import com.delivery.rider.mapper.RiderMapper;
import com.delivery.rider.service.RiderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 骑手服务：上线 / 下线 与骑手信息查询。
 *
 * <p><b>关键：上线、下线都必须同时改两处存储</b>
 * <ul>
 *   <li>MySQL 的 rider 表：持久状态（在线标志、最后位置、在手单数），重启不丢，后台列表读它；</li>
 *   <li>Redis GEO 集合：派单用的<b>实时空间索引</b>，{@code GEOSEARCH} 找附近骑手只认它。</li>
 * </ul>
 * 上线 = 两处都写入；下线 = 两处都移除。只改一处就会出现「数据库显示在线但派单搜不到」
 * 或反过来的不一致。后端重启后 GEO 不会自动恢复、骑手必须重新点上线，根源也在这里。
 *
 * <p>列表接口用一条 {@code GEOPOS} 批量取回所有骑手坐标，避免 N 个骑手 N 次 Redis 往返（N+1）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RiderServiceImpl implements RiderService {

    private final RiderMapper riderMapper;
    private final RiderGeoRepository geoRepository;

    /**
     * 骑手上线：① 置在线状态 ② 冷备最后位置到 MySQL ③ GEOADD 进 Redis 实时索引。
     * 首次上线必须带经纬度（数据库也没有历史位置时直接拒绝），否则 GEO 里没坐标、无法参与派单。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public RiderVO online(Long riderId, BigDecimal lng, BigDecimal lat) {
        Rider rider = requireRider(riderId);

        // 没传坐标就用最后已知位置；都没有则不允许上线（否则无法参与派单）
        BigDecimal useLng = lng != null ? lng : rider.getLastLng();
        BigDecimal useLat = lat != null ? lat : rider.getLastLat();
        if (useLng == null || useLat == null) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "首次上线必须携带经纬度，否则无法参与派单");
        }

        riderMapper.updateOnlineStatus(riderId, 1);
        riderMapper.updateLastPosition(riderId, useLng, useLat);
        geoRepository.addOrUpdate(riderId, useLng.doubleValue(), useLat.doubleValue());

        log.info("骑手上线 riderId={} riderNo={} 位置=({}, {})",
                riderId, rider.getRiderNo(), useLng, useLat);
        return buildVO(riderId);
    }

    /**
     * 骑手下线：清在线状态 + 从 GEO 集合移除（ZREM），与上线正好相反。
     * 允许骑手带着在手单下线，但只告警留痕、交后台介入改派，不强行拦截。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public RiderVO offline(Long riderId) {
        Rider rider = requireRider(riderId);

        riderMapper.updateOnlineStatus(riderId, 0);
        geoRepository.remove(riderId);

        if (rider.getDeliveringCount() != null && rider.getDeliveringCount() > 0) {
            // 允许下线，但要留痕：在手订单需要后台介入改派
            log.warn("骑手带单下线，需后台关注 riderId={} riderNo={} 在手单数={}",
                    riderId, rider.getRiderNo(), rider.getDeliveringCount());
        }

        log.info("骑手下线 riderId={} riderNo={}", riderId, rider.getRiderNo());
        return buildVO(riderId);
    }

    @Override
    public RiderVO getById(Long riderId) {
        Rider rider = requireRider(riderId);
        return buildVO(rider);
    }

    @Override
    public Rider getEntity(Long riderId) {
        return requireRider(riderId);
    }

    @Override
    public List<RiderVO> listAll() {
        List<Rider> riders = riderMapper.selectList(null);
        Map<Long, RiderGeoPoint> positions = geoRepository.getPositions(
                riders.stream().map(Rider::getId).collect(Collectors.toList()));
        return riders.stream()
                .map(r -> buildVO(r, positions.get(r.getId())))
                .collect(Collectors.toList());
    }

    @Override
    public List<RiderVO> listOnline() {
        List<Rider> riders = riderMapper.selectOnlineRiders();
        Map<Long, RiderGeoPoint> positions = geoRepository.getPositions(
                riders.stream().map(Rider::getId).collect(Collectors.toList()));
        return riders.stream()
                .map(r -> buildVO(r, positions.get(r.getId())))
                .collect(Collectors.toList());
    }

    @Override
    public List<RiderOptionVO> listOptions() {
        return riderMapper.selectList(null).stream()
                .map(RiderOptionVO::of)
                .collect(Collectors.toList());
    }

    // ---------------- 私有方法 ----------------

    private Rider requireRider(Long riderId) {
        Rider rider = riderMapper.selectById(riderId);
        if (rider == null) {
            throw new BizException(ErrorCode.RIDER_NOT_FOUND, "riderId=" + riderId);
        }
        return rider;
    }

    private RiderVO buildVO(Long riderId) {
        return buildVO(requireRider(riderId));
    }

    /** 组装 VO（单条场景）：实时位置自行查一次 GEO */
    private RiderVO buildVO(Rider rider) {
        return buildVO(rider, geoRepository.getPosition(rider.getId()));
    }

    /**
     * 组装 VO（列表场景）：实时位置由调用方一次性 GEOPOS 批量取回后传入，
     * 避免 N 个骑手 N 次 Redis 往返。point 为 null 表示当前不在线，坐标字段留空。
     */
    private RiderVO buildVO(Rider rider, RiderGeoPoint point) {
        RiderVO vo = RiderVO.of(rider);
        if (point != null) {
            vo.setLng(BigDecimal.valueOf(point.getLng()));
            vo.setLat(BigDecimal.valueOf(point.getLat()));
        }
        return vo;
    }
}
