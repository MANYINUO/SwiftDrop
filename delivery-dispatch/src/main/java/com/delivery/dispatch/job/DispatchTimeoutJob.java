package com.delivery.dispatch.job;

import com.delivery.dispatch.entity.DispatchTask;
import com.delivery.dispatch.mapper.DispatchTaskMapper;
import com.delivery.dispatch.service.DispatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 派单超时扫描任务。
 *
 * <p><b>第一版用定时任务</b>：实现简单、行为可预测、出问题好排查，
 * 而且能在项目里展示「方案演进」——从扫表到 MQ 延迟消息。
 *
 * <p><b>演进方向</b>：派单时同时投递一条 RabbitMQ 延迟消息
 * （需 {@code rabbitmq_delayed_message_exchange} 插件），
 * 到点消费即触发重派。好处是精度从「秒级」提到「毫秒级」，且不用扫表。
 * 代价是多一个中间件依赖、以及消息重复消费要自己做幂等
 * （本项目已用 {@code dispatch_task.status} 的 CAS 天然幂等，迁移成本很低）。
 *
 * <p>可通过 {@code delivery.dispatch.timeout-scan-interval-ms} 调整扫描间隔，
 * 设为 -1 可关闭（迁移到 MQ 后关掉）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "delivery.dispatch.timeout-scan-interval-ms", matchIfMissing = true)
public class DispatchTimeoutJob {

    private final DispatchTaskMapper taskMapper;
    private final DispatchService dispatchService;

    /** 单次扫描最多处理的任务数，避免一次扫太多把线程占满 */
    private static final int BATCH = 200;

    @Scheduled(fixedDelayString = "${delivery.dispatch.timeout-scan-interval-ms:5000}")
    public void scanTimeoutTasks() {
        List<DispatchTask> expired;
        try {
            expired = taskMapper.selectExpiredPending(BATCH);
        } catch (Exception e) {
            log.error("扫描超时派单任务失败", e);
            return;
        }

        if (expired.isEmpty()) {
            return;
        }

        log.info("发现 {} 条超时未响应的派单任务，开始重派", expired.size());
        for (DispatchTask task : expired) {
            try {
                // 单个任务失败不影响其他任务
                dispatchService.handleTimeout(task);
            } catch (Exception e) {
                log.error("处理超时派单任务失败 taskId={} orderNo={}",
                        task.getId(), task.getOrderNo(), e);
            }
        }
    }
}
