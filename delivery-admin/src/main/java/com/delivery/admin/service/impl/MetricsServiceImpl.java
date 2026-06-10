package com.delivery.admin.service.impl;

import com.delivery.admin.service.MetricsService;
import com.delivery.common.geo.RiderGeoRepository;
import com.delivery.dispatch.mapper.DispatchTaskMapper;
import com.delivery.notify.service.PushService;
import com.delivery.order.mapper.DeliveryOrderMapper;
import com.delivery.rider.mapper.RiderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 调度后台指标看板：聚合订单、派单、骑手、WebSocket 连接数等概览数据。
 *
 * <p><b>演示版取舍</b>：{@link #overview()} 里十几次统计是串行查库的，数据量小、逻辑直观；
 * 数据量上来后应把可合并的聚合写成一条 SQL（例如 {@code SUM(CASE WHEN ...)}），
 * 再加 5～10 秒短缓存（看板不需要秒级实时），避免每次刷新都全表 COUNT 一遍。
 *
 * <p>派单成功率特意采用「订单口径」而非「任务口径」，原因见
 * {@link com.delivery.order.mapper.DeliveryOrderMapper#countDispatchedOrders}。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MetricsServiceImpl implements MetricsService {

    private final DeliveryOrderMapper orderMapper;
    private final DispatchTaskMapper taskMapper;
    private final RiderMapper riderMapper;
    private final RiderGeoRepository geoRepository;
    private final PushService pushService;

    @Override
    public Map<String, Object> overview() {
        Map<String, Object> m = new LinkedHashMap<>();

        // ---------- 订单 ----------
        long orderTotal = orderMapper.selectCount(null);
        m.put("orderTotal", orderTotal);
        m.put("orderStatusDistribution", orderStatusDistribution());

        Double avgDeliver = orderMapper.avgDeliverSeconds();
        m.put("avgDeliverSeconds", round(avgDeliver));
        m.put("avgDeliverMinutes", avgDeliver == null ? null
                : round(avgDeliver / 60.0));
        m.put("needManualOrders", orderMapper.countNeedManual());

        // ---------- 派单 ----------
        long taskTotal = taskMapper.countAll();
        long taskAccepted = taskMapper.countAccepted();
        m.put("dispatchTaskTotal", taskTotal);
        m.put("dispatchTaskAccepted", taskAccepted);

        // 【订单口径】派单成功率 —— 对外汇报用这个口径
        //   = 已派出（有骑手接单）的订单数 / 订单总数
        long orderTotalCnt = orderMapper.countOrders();
        long dispatched = orderMapper.countDispatchedOrders();
        m.put("dispatchedOrderCount", dispatched);
        m.put("dispatchSuccessRate", orderTotalCnt == 0 ? null
                : round(dispatched * 100.0 / orderTotalCnt) + "%");

        // 【任务口径】推送响应率 —— 运营视角：推出去的派单有多少被响应了
        //   注意上限不是 100%，因为一个订单会推给多个候选骑手
        m.put("taskResponseRate", taskTotal == 0 ? null
                : round(taskAccepted * 100.0 / taskTotal) + "%");

        m.put("avgDispatchResponseSeconds", round(taskMapper.avgAcceptSeconds()));
        m.put("avgDispatchRound", round(taskMapper.avgRound()));

        // ---------- 骑手 ----------
        m.put("riderTotal", riderMapper.selectCount(null));
        m.put("riderOnline", riderMapper.countOnline());
        m.put("riderGeoOnline", geoRepository.onlineCount());

        // ---------- 推送 ----------
        m.put("userWsSessions", pushService.userSessionCount());
        m.put("riderWsSessions", pushService.riderSessionCount());

        return m;
    }

    @Override
    public Map<String, Long> orderStatusDistribution() {
        List<Map<String, Object>> rows = orderMapper.countGroupByStatus();
        Map<String, Long> result = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            Object status = row.get("status");
            Object cnt = row.get("cnt");
            if (status != null && cnt != null) {
                result.put(status.toString(), ((Number) cnt).longValue());
            }
        }
        return result;
    }

    /** 保留两位小数，方便直接展示 */
    private Double round(Double v) {
        if (v == null) {
            return null;
        }
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
