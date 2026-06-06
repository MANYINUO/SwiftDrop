package com.delivery.rider.service.impl;

import com.delivery.common.exception.BizException;
import com.delivery.common.exception.ErrorCode;
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
import java.util.stream.Collectors;

/**
 * 骑手服务实现。
 * F2 简化版：没有 Redis GEO，上线/下线只更新 MySQL，
 * 位置由 RiderLocationService 单独处理。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RiderServiceImpl implements RiderService {

    private final RiderMapper riderMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RiderVO online(Long riderId, BigDecimal lng, BigDecimal lat) {
        Rider rider = requireRider(riderId);

        // 没传坐标就用最后已知位置；都没有则不允许上线
        BigDecimal useLng = lng != null ? lng : rider.getLastLng();
        BigDecimal useLat = lat != null ? lat : rider.getLastLat();
        if (useLng == null || useLat == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "首次上线必须携带经纬度");
        }

        riderMapper.updateOnlineStatus(riderId, 1);
        riderMapper.updateLastPosition(riderId, useLng, useLat);

        log.info("骑手上线 riderId={} riderNo={}", riderId, rider.getRiderNo());
        return RiderVO.of(riderMapper.selectById(riderId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RiderVO offline(Long riderId) {
        Rider rider = requireRider(riderId);

        riderMapper.updateOnlineStatus(riderId, 0);

        if (rider.getDeliveringCount() != null && rider.getDeliveringCount() > 0) {
            log.warn("骑手带单下线 riderId={} 在手单数={}", riderId, rider.getDeliveringCount());
        }

        log.info("骑手下线 riderId={} riderNo={}", riderId, rider.getRiderNo());
        return RiderVO.of(riderMapper.selectById(riderId));
    }

    @Override
    public RiderVO getById(Long riderId) {
        return RiderVO.of(requireRider(riderId));
    }

    @Override
    public Rider getEntity(Long riderId) {
        return requireRider(riderId);
    }

    @Override
    public List<RiderVO> listOnline() {
        return riderMapper.selectOnlineRiders().stream()
                .map(RiderVO::of)
                .collect(Collectors.toList());
    }

    private Rider requireRider(Long riderId) {
        Rider rider = riderMapper.selectById(riderId);
        if (rider == null) {
            throw new BizException(ErrorCode.RIDER_NOT_FOUND, "riderId=" + riderId);
        }
        return rider;
    }
}
