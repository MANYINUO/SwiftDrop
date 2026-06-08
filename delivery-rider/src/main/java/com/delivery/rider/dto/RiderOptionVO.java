package com.delivery.rider.dto;

import com.delivery.rider.entity.Rider;
import lombok.Data;

/**
 * 骑手登录页用的精简选项。
 *
 * <p>只暴露"选哪个骑手登录"和在线状态所必需的字段，<b>不含手机号、坐标、评分、容量</b>等，
 * 避免未登录就能拉到全量骑手隐私。完整信息走需登录的 {@code /api/rider/list}。
 */
@Data
public class RiderOptionVO {

    private Long riderId;
    private String riderNo;
    private String name;
    /** 是否在线：1 在线 0 离线 */
    private Integer online;

    public static RiderOptionVO of(Rider rider) {
        RiderOptionVO vo = new RiderOptionVO();
        vo.riderId = rider.getId();
        vo.riderNo = rider.getRiderNo();
        vo.name = rider.getName();
        vo.online = rider.getOnlineStatus();
        return vo;
    }
}
