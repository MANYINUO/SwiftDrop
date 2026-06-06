package com.delivery.rider.service;

import com.delivery.rider.dto.RiderVO;
import com.delivery.rider.entity.Rider;

import java.math.BigDecimal;
import java.util.List;

/**
 * 骑手服务：上下线、信息查询。
 * 接单/取货/送达在 dispatch 模块的 DispatchService 里（避免循环依赖）。
 */
public interface RiderService {

    /** 骑手上线：置在线状态，更新最后位置 */
    RiderVO online(Long riderId, BigDecimal lng, BigDecimal lat);

    /** 骑手下线：置离线状态 */
    RiderVO offline(Long riderId);

    RiderVO getById(Long riderId);

    Rider getEntity(Long riderId);

    /** 在线骑手列表 */
    List<RiderVO> listOnline();
}
