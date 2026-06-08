package com.delivery.rider.service;

import com.delivery.common.config.DeliveryProperties;
import com.delivery.common.constant.DeliveryConstants;
import com.delivery.common.exception.BizException;
import com.delivery.common.exception.ErrorCode;
import com.delivery.common.geo.RiderGeoPoint;
import com.delivery.common.geo.RiderGeoRepository;
import com.delivery.rider.dto.LocationReportRequest;
import com.delivery.rider.entity.Rider;
import com.delivery.rider.entity.RiderLocationLog;
import com.delivery.rider.event.RiderLocationUpdatedEvent;
import com.delivery.rider.mapper.RiderLocationLogMapper;
import com.delivery.rider.mapper.RiderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 骑手位置服务 —— 全系统写入压力最大的地方。
 *
 * <p><b>核心设计：读写分离的存储策略</b>
 * <pre>
 *   骑手上报 → ① 防抖（Redis SETNX，2 秒内重复上报直接丢弃）
 *            → ② 写 Redis GEO（同步，调度需要实时位置）
 *            → ③ 入内存缓冲队列（不阻塞接口）
 *            → ④ 发布事件（notify 模块负责推给用户）
 *   定时任务  → ⑤ 缓冲队列批量落库（攒够或到点刷一次）
 * </pre>
 *
 * <p>为什么这么设计：<b>调度需要「实时位置」，但历史轨迹不需要实时</b>。
 * 把这两条链路分开，是位置模块最重要的决策 —— 否则 3000+ QPS 会把数据库打爆。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RiderLocationService {

    private final RiderMapper riderMapper;
    private final RiderLocationLogMapper logMapper;
    private final RiderGeoRepository geoRepository;
    private final StringRedisTemplate redis;
    private final ApplicationEventPublisher eventPublisher;
    private final DeliveryProperties properties;

    /** 位置流水缓冲队列；满了直接丢弃（宁可丢轨迹，不能阻塞上报） */
    private final BlockingQueue<RiderLocationLog> buffer = new LinkedBlockingQueue<>(20_000);

    private final AtomicLong acceptedCount = new AtomicLong();
    private final AtomicLong throttledCount = new AtomicLong();
    private final AtomicLong droppedCount = new AtomicLong();

    /**
     * 上报位置。
     *
     * @return true 表示本次上报被接受；false 表示被防抖丢弃
     */
    public boolean report(Long riderId, LocationReportRequest req) {
        // ---------- ① 防抖前置：最廉价的拦截放在最前，2 秒内重复上报在查库之前就丢弃，
        //             避免高频重复位置每次都打一次 selectById ----------
        long minInterval = properties.getLocation().getReportMinIntervalMs();
        String debounceKey = "delivery:rider:report:" + riderId;
        Boolean first = redis.opsForValue()
                .setIfAbsent(debounceKey, "1", Duration.ofMillis(minInterval));
        if (!Boolean.TRUE.equals(first)) {
            throttledCount.incrementAndGet();
            return false;
        }

        // 防抖通过后再查库校验存在性 / 在线状态；校验失败要回滚防抖占位，
        // 否则骑手（重新）上线后的第一次上报可能被残留防抖键白白挡掉一个窗口。
        Rider rider = riderMapper.selectById(riderId);
        if (rider == null) {
            redis.delete(debounceKey);
            throw new BizException(ErrorCode.RIDER_NOT_FOUND, "riderId=" + riderId);
        }
        if (!rider.isOnline()) {
            redis.delete(debounceKey);
            throw new BizException(ErrorCode.RIDER_OFFLINE, "riderId=" + riderId);
        }

        double lng = req.getLng().doubleValue();
        double lat = req.getLat().doubleValue();

        // ---------- ② 同步写 Redis GEO（调度实时性靠它）----------
        // 先把「当前位置」挪到「上一位置」，供派单时计算顺路度使用
        RiderGeoPoint current = geoRepository.getPosition(riderId);
        if (current != null) {
            geoRepository.savePreviousPosition(riderId, current.getLng(), current.getLat());
        }
        geoRepository.addOrUpdate(riderId, lng, lat);

        // ---------- ③ 入缓冲队列，异步批量落库 ----------
        RiderLocationLog logRow = new RiderLocationLog();
        logRow.setRiderId(riderId);
        logRow.setOrderNo(req.getOrderNo());
        logRow.setLng(req.getLng());
        logRow.setLat(req.getLat());
        logRow.setReportAt(LocalDateTime.now());
        if (!buffer.offer(logRow)) {
            droppedCount.incrementAndGet();
            log.warn("位置缓冲队列已满，丢弃一条轨迹 riderId={}", riderId);
        }

        // ---------- ④ 发布事件，由 notify 模块推送给用户 ----------
        // 注意传的是 orderNo：用户端按 /ws/user/{orderNo} 订阅，key 必须一致
        eventPublisher.publishEvent(
                new RiderLocationUpdatedEvent(riderId, req.getLng(), req.getLat(), req.getOrderNo()));

        acceptedCount.incrementAndGet();
        return true;
    }

    /**
     * 定时批量落库。
     *
     * <p>演进方向：生产环境应换成「发 MQ + 消费者批量落库」，
     * 这样应用重启不会丢缓冲、多实例也能各自处理。
     * 第一版用内存队列是为了少一个中间件依赖，链路更容易调试。
     */
    @Scheduled(fixedDelayString = "${delivery.location.flush-interval-ms:1000}")
    public void flushBuffer() {
        int batchSize = properties.getLocation().getFlushBatchSize();
        List<RiderLocationLog> batch = new ArrayList<>(batchSize);
        buffer.drainTo(batch, batchSize);
        if (batch.isEmpty()) {
            return;
        }
        try {
            int inserted = logMapper.batchInsert(batch);
            // 影响行数少于投递条数 = 有记录没写进去（约束冲突、字段超长等）。
            // 之前这里直接忽略返回值、只打 batch.size()，等于把「悄悄少写」这件事藏起来了。
            if (inserted < batch.size()) {
                log.warn("位置流水批量落库条数少于预期：投递 {} 实际写入 {}", batch.size(), inserted);
            }
            log.debug("批量落库位置流水 {} 条", inserted);
        } catch (Exception e) {
            // 落库失败不影响上报主链路，记日志即可（轨迹可以丢，位置不能丢）
            log.error("位置流水批量落库失败，条数={}", batch.size(), e);
        }
    }

    /** 供压测/演示查看吞吐 */
    public String stats() {
        return String.format("accepted=%d, throttled=%d, dropped=%d, buffered=%d",
                acceptedCount.get(), throttledCount.get(), droppedCount.get(), buffer.size());
    }

    public long onlineCount() {
        return geoRepository.onlineCount();
    }

    /** 常量兜底，避免 IDE 报未使用告警 */
    public long minIntervalMillis() {
        return DeliveryConstants.LOCATION_REPORT_MIN_INTERVAL_MS;
    }
}
