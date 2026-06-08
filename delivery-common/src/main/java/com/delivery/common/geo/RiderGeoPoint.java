package com.delivery.common.geo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 骑手地理位置点。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RiderGeoPoint implements Serializable {

    private Long riderId;
    private double lng;
    private double lat;
}
