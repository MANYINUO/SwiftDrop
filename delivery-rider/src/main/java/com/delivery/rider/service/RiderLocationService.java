package com.delivery.rider.service;

import com.delivery.common.exception.BizException;
import com.delivery.common.exception.ErrorCode;
import com.delivery.rider.dto.LocationReportRequest;
import com.delivery.rider.entity.Rider;
import com.delivery.rider.entity.RiderLocationLog;
import com.delivery.rider.mapper.RiderLocationLogMapper;
import com.delivery.rider.mapper.RiderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 骑手位置服务。
 * F2 简化版：同步写 rider 表（最后位置）+ rider_location_log 表（轨迹）。
 * 生产环境应做 Redis 防抖 + 批量缓冲落库，F4 推送阶段再加回。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RiderLocationService {

    private final RiderMapper riderMapper;
    private final RiderLocationLogMapper logMapper;

    /**
     * 上报位置。
     * @return true 表示接受；F2 简化版总是接受（不做防抖）
     */
    public boolean report(Long riderId, LocationReportRequest req) {
        Rider rider = riderMapper.selectById(riderId);
        if (rider == null) {
            throw new BizException(ErrorCode.RIDER_NOT_FOUND, "riderId=" + riderId);
        }
        if (rider.getOnlineStatus() == null || rider.getOnlineStatus() != 1) {
            throw new BizException(ErrorCode.RIDER_OFFLINE, "riderId=" + riderId);
        }

        // 1. 更新 rider 表的最后位置（调度和用户端查实时位置都读这个）
        riderMapper.updateLastPosition(riderId, req.getLng(), req.getLat());

        // 2. 写轨迹流水（用户端订单追踪页的历史轨迹读这个）
        RiderLocationLog logRow = new RiderLocationLog();
        logRow.setRiderId(riderId);
        logRow.setOrderNo(req.getOrderNo());
        logRow.setLng(req.getLng());
        logRow.setLat(req.getLat());
        logRow.setReportAt(LocalDateTime.now());
        logMapper.insert(logRow);

        log.debug("骑手位置上报 riderId={} lng={} lat={} orderNo={}",
                riderId, req.getLng(), req.getLat(), req.getOrderNo());
        return true;
    }
}
