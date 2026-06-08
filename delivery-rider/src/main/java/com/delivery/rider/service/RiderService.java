package com.delivery.rider.service;

import com.delivery.rider.dto.RiderOptionVO;
import com.delivery.rider.dto.RiderVO;
import com.delivery.rider.entity.Rider;

import java.math.BigDecimal;
import java.util.List;

public interface RiderService {

    /**
     * 骑手上线：置在线状态并写入 Redis GEO。
     *
     * @param lng 可选。为空时使用该骑手最后上报的位置
     */
    RiderVO online(Long riderId, BigDecimal lng, BigDecimal lat);

    /** 骑手下线：置离线状态并从 Redis GEO 移除 */
    RiderVO offline(Long riderId);

    RiderVO getById(Long riderId);

    Rider getEntity(Long riderId);

    List<RiderVO> listAll();

    List<RiderVO> listOnline();

    /** 登录页精简骑手名单（不含手机号 / 坐标等隐私字段） */
    List<RiderOptionVO> listOptions();
}
