package com.delivery.common.geo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 附近骑手搜索结果：骑手 ID + 距离 + 坐标。
 *
 * <p>坐标由 Redis 的 WITHCOORD 一并返回，避免应用层再逐个回查（省 N 次往返）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NearbyRider implements Serializable {

    private Long riderId;

    /** 距离（米） */
    private double distanceMeters;

    private double lng;
    private double lat;

    public NearbyRider(Long riderId, double distanceMeters) {
        this.riderId = riderId;
        this.distanceMeters = distanceMeters;
    }
}
