package com.delivery.admin.service;

import java.util.Map;

/**
 * 指标统计。
 *
 * <p>所有数字都从数据库实时查出，不做预设、不读缓存。
 */
public interface MetricsService {

    /** 汇总指标 */
    Map<String, Object> overview();

    /** 订单状态分布 */
    Map<String, Long> orderStatusDistribution();
}
