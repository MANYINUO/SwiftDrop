package com.delivery.dispatch.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.delivery.common.config.DeliveryProperties;
import com.delivery.common.constant.DeliveryConstants;
import com.delivery.common.enums.DispatchTaskStatus;
import com.delivery.common.enums.OperatorType;
import com.delivery.common.enums.OrderEventType;
import com.delivery.common.enums.OrderStatus;
import com.delivery.common.exception.BizException;
import com.delivery.common.exception.ErrorCode;
import com.delivery.dispatch.dto.AcceptResult;
import com.delivery.dispatch.dto.DispatchRoundResult;
import com.delivery.dispatch.dto.DispatchTaskVO;
import com.delivery.dispatch.dto.RiderCandidate;
import com.delivery.dispatch.entity.DispatchTask;
import com.delivery.dispatch.mapper.DispatchTaskMapper;
import com.delivery.dispatch.service.CandidateSelector;
import com.delivery.dispatch.service.DispatchService;
import com.delivery.notify.dto.PushMessage;
import com.delivery.notify.service.PushService;
import com.delivery.order.entity.DeliveryOrder;
import com.delivery.order.dto.OrderVO;
import com.delivery.order.mapper.DeliveryOrderMapper;
import com.delivery.order.service.OrderService;
import com.delivery.order.statemachine.OrderStateMachine;
import com.delivery.rider.entity.Rider;
import com.delivery.rider.mapper.RiderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 调度引擎实现。
 *
 * <p>核心是三段逻辑：
 * <ol>
 *   <li><b>派单</b>：找候选 → 打分排序 → 生成派单任务 → 推送骑手</li>
 *   <li><b>接单</b>：四步原子事务，保证一单一人且骑手不超载</li>
 *   <li><b>重派</b>：拒单 / 超时后回退到待派单，扩大半径进入下一轮；轮次用尽转人工</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DispatchServiceImpl implements DispatchService {

    private final DeliveryOrderMapper orderMapper;
    private final DispatchTaskMapper taskMapper;
    private final RiderMapper riderMapper;
    private final OrderStateMachine stateMachine;
    private final CandidateSelector candidateSelector;
    private final PushService pushService;
    private final OrderService orderService;
    private final DeliveryProperties properties;

    /**
     * 自身代理。类内需要重新走事务边界去调用 {@link #dispatchOrder} 时必须用它，
     * 直接 this 调用会绕过 Spring 代理，方法上的 {@code @Transactional} 静默失效。
     */
    @Lazy
    @Autowired
    private DispatchService self;

    // ==================================================================
    //  一、派单
    // ==================================================================

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public DispatchRoundResult dispatchOrder(Long orderId, int round) {
        DeliveryOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BizException(ErrorCode.ORDER_NOT_FOUND, "orderId=" + orderId);
        }

        if (order.statusEnum() != OrderStatus.PENDING_DISPATCH) {
            // 已经被别人派出去 / 已取消，直接跳过（并发安全，不报错）
            return DispatchRoundResult.skipped(order.getOrderNo(), order.statusEnum().getDesc());
        }

        int maxRound = properties.getDispatch().getMaxRound();
        int currentRound = Math.max(round, 1);

        // 无候选就扩大半径重试，在本事务内用循环完成（替代旧的 this 自调用递归——
        // 自调用绕过 Spring 代理，递归各轮会被错误地并入外层事务）。无候选阶段只记事件、
        // 不产生任务写入，循环放同一事务即可；只有找到候选的那一轮才流转状态并建任务。
        while (true) {
            int radius = properties.getDispatch().radiusOfRound(currentRound);
            long promisedSeconds = promisedSeconds(order);

            // ---------- 1. 找候选并打分 ----------
            List<RiderCandidate> candidates = candidateSelector.select(
                    order.getPickupLng().doubleValue(), order.getPickupLat().doubleValue(),
                    order.getDeliverLng().doubleValue(), order.getDeliverLat().doubleValue(),
                    promisedSeconds, radius, properties.getDispatch().getCandidateLimit());

            // ---------- 2. 排除已拒单骑手；对超时过的骑手降权而不是排除 ----------
            candidates = excludeRejectedRiders(orderId, candidates);
            candidates = applyTimeoutPenalty(orderId, candidates);

            if (!candidates.isEmpty()) {
                return doDispatch(order, currentRound, radius, candidates);
            }

            recordExhausted(order, currentRound, radius);

            if (currentRound < maxRound) {
                log.info("订单 {} 第 {} 轮无候选，扩大半径进入第 {} 轮",
                        order.getOrderNo(), currentRound, currentRound + 1);
                currentRound++;
                continue;
            }

            markNeedManual(order.getId(), currentRound);
            log.warn("订单 {} 候选骑手耗尽（已尝试 {} 轮），转人工调度",
                    order.getOrderNo(), currentRound);
            return DispatchRoundResult.exhausted(order.getOrderNo(), currentRound);
        }
    }

    /** 找到候选后的单轮派单：状态流转 + 建任务，推送延迟到事务提交后。 */
    private DispatchRoundResult doDispatch(DeliveryOrder order, int useRound, int radius,
                                           List<RiderCandidate> candidates) {
        // ---------- 3. 状态流转：待派单 -> 派单中（CAS）----------
        stateMachine.transit(order.getId(), order.getOrderNo(),
                OrderStatus.PENDING_DISPATCH, OrderStatus.DISPATCHING,
                OperatorType.SYSTEM, null,
                String.format("第 %d 轮派单：搜索半径 %d 米，候选 %d 人", useRound, radius, candidates.size()),
                w -> w.set(DeliveryOrder::getDispatchRound, useRound)
                        .set(DeliveryOrder::getDispatchedAt, LocalDateTime.now())
                        .set(DeliveryOrder::getNeedManual, 0),
                OrderEventType.DISPATCH_CREATED);

        // ---------- 4. 生成派单任务（推送先收集，提交后再发）----------
        LocalDateTime expireAt = LocalDateTime.now()
                .plusSeconds(properties.getDispatch().getResponseTimeoutSeconds());

        int rank = 1;
        List<String> explains = new ArrayList<>(candidates.size());
        List<RiderPush> pushes = new ArrayList<>(candidates.size());
        for (RiderCandidate c : candidates) {
            DispatchTask task = new DispatchTask();
            task.setOrderId(order.getId());
            task.setOrderNo(order.getOrderNo());
            task.setRound(useRound);
            task.setRiderId(c.getRiderId());
            task.setCandidateRank(rank++);
            task.setCandidateScore(BigDecimal.valueOf(c.getScore()));
            task.setScoreDetail(c.detailJson());
            task.setStatus(DispatchTaskStatus.PENDING.name());
            task.setExpireAt(expireAt);
            task.setCreatedAt(LocalDateTime.now());
            taskMapper.insert(task);

            pushes.add(new RiderPush(c.getRiderId(),
                    PushMessage.of("DISPATCH_TASK", buildTaskVO(task, order))));

            explains.add(c.explain());
            log.info("派单 → orderNo={} 第{}轮 骑手={} 排名={} 得分={}",
                    order.getOrderNo(), useRound, c.getRiderId(), task.getCandidateRank(),
                    String.format("%.4f", c.getScore()));
        }

        // 任务确认落库后再推送，杜绝事务回滚导致骑手收到不存在任务的「幽灵派单」
        scheduleRiderPushes(pushes);

        return DispatchRoundResult.dispatched(order.getOrderNo(), useRound, candidates.size(),
                expireAt, explains);
    }

    /** 排除本轮已明确拒单的骑手。 */
    private List<RiderCandidate> excludeRejectedRiders(Long orderId, List<RiderCandidate> candidates) {
        List<Long> rejected = taskMapper.selectRejectedRiderIds(orderId);
        if (rejected.isEmpty()) {
            return candidates;
        }
        return candidates.stream()
                .filter(c -> !rejected.contains(c.getRiderId()))
                .collect(Collectors.toList());
    }

    /** 上一轮超时的骑手不排除、只降权，并重排让未超时者优先。 */
    private List<RiderCandidate> applyTimeoutPenalty(Long orderId, List<RiderCandidate> candidates) {
        if (candidates.isEmpty()) {
            return candidates;
        }
        List<Long> timedOut = taskMapper.selectTimedOutRiderIds(orderId);
        if (timedOut.isEmpty()) {
            return candidates;
        }
        for (RiderCandidate c : candidates) {
            if (timedOut.contains(c.getRiderId())) {
                // 降权而非排除：他可能只是当时在忙，下一轮还愿意接
                c.setScore(c.getScore() * DeliveryConstants.TIMEOUT_PENALTY);
            }
        }
        candidates.sort(Comparator.comparingDouble(RiderCandidate::getScore).reversed());
        log.info("订单 {} 有 {} 名骑手上一轮超时，已降权处理", orderId, timedOut.size());
        return candidates;
    }

    /** 记录一轮「无可用候选」事件。 */
    private void recordExhausted(DeliveryOrder order, int round, int radius) {
        stateMachine.recordEvent(order.getId(), order.getOrderNo(),
                OrderEventType.DISPATCH_EXHAUSTED,
                order.statusEnum(), order.statusEnum(),
                OperatorType.SYSTEM, null,
                String.format("第 %d 轮（半径 %d 米）无可用候选骑手", round, radius));
    }

    @Override
    public DispatchRoundResult dispatchByOrderNo(String orderNo) {
        DeliveryOrder order = orderService.getEntityByOrderNo(orderNo);
        int nextRound = (order.getDispatchRound() == null ? 0 : order.getDispatchRound()) + 1;
        // 必须走自身代理，dispatchOrder 上的 @Transactional 才会生效
        return self.dispatchOrder(order.getId(), nextRound);
    }

    /** 待推送的骑手消息：事务内先收集，提交后统一发送。 */
    private record RiderPush(Long riderId, PushMessage message) {
    }

    /**
     * 把一批骑手推送注册到当前事务提交之后执行；没有活跃事务时立即发送。
     * 逐条吞异常：此时事务已提交，单条推送失败不能影响主流程，也不应误伤其余骑手。
     */
    private void scheduleRiderPushes(List<RiderPush> pushes) {
        if (pushes == null || pushes.isEmpty()) {
            return;
        }
        afterCommit(() -> {
            for (RiderPush p : pushes) {
                try {
                    pushService.pushToRider(p.riderId(), p.message());
                } catch (Exception e) {
                    log.warn("事务提交后推送派单消息失败 riderId={}", p.riderId(), e);
                }
            }
        });
    }

    /** 当前事务成功提交后执行 action；当前无活跃事务则直接执行。 */
    private void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    private void markNeedManual(Long orderId, int round) {
        orderMapper.update(null, Wrappers.<DeliveryOrder>lambdaUpdate()
                .eq(DeliveryOrder::getId, orderId)
                .eq(DeliveryOrder::getStatus, OrderStatus.PENDING_DISPATCH.name())
                .set(DeliveryOrder::getNeedManual, 1)
                .set(DeliveryOrder::getDispatchRound, round)
                .setSql("version = version + 1"));
    }

    // ==================================================================
    //  二、接单 —— 四步原子事务
    // ==================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AcceptResult accept(Long riderId, Long taskId) {

        DispatchTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BizException(ErrorCode.DISPATCH_TASK_NOT_FOUND, "taskId=" + taskId);
        }
        if (!Objects.equals(task.getRiderId(), riderId)) {
            throw new BizException(ErrorCode.RIDER_NOT_OWNER);
        }

        // ---------- 幂等：本人已经接过这一单，直接返回成功 ----------
        if (DispatchTaskStatus.ACCEPTED.name().equals(task.getStatus())) {
            log.info("重复接单请求，命中幂等 riderId={} taskId={}", riderId, taskId);
            return AcceptResult.alreadyAccepted(task.getOrderNo());
        }
        if (!DispatchTaskStatus.PENDING.name().equals(task.getStatus())) {
            throw new BizException(ErrorCode.DISPATCH_TASK_NOT_PENDING,
                    "任务当前状态=" + task.getStatus());
        }
        if (task.getExpireAt() != null && task.getExpireAt().isBefore(LocalDateTime.now())) {
            throw new BizException(ErrorCode.DISPATCH_TASK_EXPIRED);
        }

        DeliveryOrder order = orderMapper.selectById(task.getOrderId());
        if (order == null) {
            throw new BizException(ErrorCode.ORDER_NOT_FOUND);
        }
        // 快速失败：已经被别人接走了
        if (order.getRiderId() != null) {
            throw new BizException(ErrorCode.ORDER_ALREADY_TAKEN);
        }
        OrderStatus from = order.statusEnum();
        if (from != OrderStatus.DISPATCHING) {
            throw new BizException(ErrorCode.ORDER_ALREADY_TAKEN,
                    "订单当前状态【" + from.getDesc() + "】");
        }

        // ---------- ① CAS 抢占订单：并发下只有一个骑手能成功 ----------
        try {
            stateMachine.transit(order.getId(), order.getOrderNo(),
                    OrderStatus.DISPATCHING, OrderStatus.PENDING_PICKUP,
                    OperatorType.RIDER, riderId, "骑手接单",
                    w -> w.set(DeliveryOrder::getRiderId, riderId)
                            .set(DeliveryOrder::getAcceptedAt, LocalDateTime.now()),
                    OrderEventType.DISPATCH_ACCEPTED);
        } catch (BizException e) {
            // CAS 失败说明在「读状态」和「写状态」之间被别的骑手抢走了
            if (e.getCode() == ErrorCode.CONCURRENT_MODIFIED.getCode()) {
                log.info("并发接单失败，订单已被抢走 orderNo={} riderId={}",
                        order.getOrderNo(), riderId);
                throw new BizException(ErrorCode.ORDER_ALREADY_TAKEN);
            }
            throw e;
        }

        // ---------- ② 更新本次派单任务 ----------
        if (taskMapper.markAccepted(taskId) == 0) {
            throw new BizException(ErrorCode.DISPATCH_TASK_NOT_PENDING);
        }

        // ---------- ③ 作废同一轮的其他派单任务 ----------
        int cancelled = taskMapper.cancelOtherPending(order.getId(), taskId);
        if (cancelled > 0) {
            log.info("接单成功，作废同轮其他 {} 条派单任务 orderNo={}", cancelled, order.getOrderNo());
        }

        // ---------- ④ 骑手容量 +1；满了就抛异常，整个事务回滚 ----------
        // 少了这一步，就会出现「一个骑手同时接 10 单」的超卖问题
        if (riderMapper.increaseLoad(riderId) == 0) {
            log.warn("骑手已达接单上限，回滚接单事务 riderId={}", riderId);
            throw new BizException(ErrorCode.RIDER_CAPACITY_FULL);
        }

        log.info("接单成功 orderNo={} riderId={}", order.getOrderNo(), riderId);
        return AcceptResult.success(task.getOrderNo(), order.getId());
    }

    // ==================================================================
    //  三、拒单
    // ==================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long riderId, Long taskId, String reason) {
        DispatchTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BizException(ErrorCode.DISPATCH_TASK_NOT_FOUND, "taskId=" + taskId);
        }
        if (!Objects.equals(task.getRiderId(), riderId)) {
            throw new BizException(ErrorCode.RIDER_NOT_OWNER);
        }

        // CAS：只有 PENDING -> REJECTED 成功的那次才继续处理，天然幂等
        if (taskMapper.markRejected(taskId) == 0) {
            log.info("拒单请求已被处理过（可能已超时/已接单）taskId={}", taskId);
            return;
        }

        DeliveryOrder order = orderMapper.selectById(task.getOrderId());
        if (order == null) {
            return;
        }

        stateMachine.recordEvent(order.getId(), order.getOrderNo(),
                OrderEventType.DISPATCH_REJECTED,
                order.statusEnum(), order.statusEnum(),
                OperatorType.RIDER, riderId,
                reason == null ? "骑手拒单" : reason);

        // 如果这是最后一个待响应任务，立刻进下一轮，不用干等超时
        if (order.statusEnum() == OrderStatus.DISPATCHING
                && taskMapper.countPendingByOrder(order.getId()) == 0) {
            advanceRound(order, task.getRound() + 1, "候选骑手均已拒单");
        }
    }

    // ==================================================================
    //  四、取货 / 送达
    // ==================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void pickup(Long riderId, String orderNo) {
        DeliveryOrder order = orderService.getEntityByOrderNo(orderNo);
        if (!Objects.equals(order.getRiderId(), riderId)) {
            throw new BizException(ErrorCode.RIDER_NOT_OWNER);
        }

        stateMachine.transit(order.getId(), orderNo,
                OrderStatus.PENDING_PICKUP, OrderStatus.DELIVERING,
                OperatorType.RIDER, riderId, "骑手取货",
                w -> w.set(DeliveryOrder::getPickedUpAt, LocalDateTime.now()),
                OrderEventType.RIDER_PICKED_UP);

        log.info("骑手取货 orderNo={} riderId={}", orderNo, riderId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delivered(Long riderId, String orderNo) {
        DeliveryOrder order = orderService.getEntityByOrderNo(orderNo);
        if (!Objects.equals(order.getRiderId(), riderId)) {
            throw new BizException(ErrorCode.RIDER_NOT_OWNER);
        }

        stateMachine.transit(order.getId(), orderNo,
                OrderStatus.DELIVERING, OrderStatus.DELIVERED,
                OperatorType.RIDER, riderId, "骑手送达",
                w -> w.set(DeliveryOrder::getDeliveredAt, LocalDateTime.now()),
                OrderEventType.RIDER_DELIVERED);

        // 释放骑手容量
        riderMapper.decreaseLoad(riderId);

        log.info("骑手送达 orderNo={} riderId={} 已释放容量", orderNo, riderId);
    }

    // ==================================================================
    //  五、超时处理
    // ==================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleTimeout(DispatchTask task) {
        // CAS：只有把 PENDING 改成 TIMEOUT 成功的线程才继续，避免重复重派
        if (taskMapper.markTimeout(task.getId()) == 0) {
            return;
        }

        DeliveryOrder order = orderMapper.selectById(task.getOrderId());
        if (order == null) {
            return;
        }

        OrderStatus status = order.statusEnum();
        if (status != OrderStatus.DISPATCHING) {
            // 已经有人接单 / 订单已取消，忽略
            return;
        }

        stateMachine.recordEvent(order.getId(), order.getOrderNo(),
                OrderEventType.DISPATCH_TIMEOUT,
                status, status, OperatorType.SYSTEM, null,
                String.format("骑手 %d 超时未响应（第 %d 轮）", task.getRiderId(), task.getRound()));

        // 还有别的骑手待响应就先等他们，不要过早回退
        if (taskMapper.countPendingByOrder(order.getId()) > 0) {
            log.info("订单 {} 仍有其他骑手待响应，暂不重派", order.getOrderNo());
            return;
        }

        advanceRound(order, task.getRound() + 1, "本轮骑手全部超时未响应");
    }

    /**
     * 回退到待派单并进入下一轮；轮次用尽则转人工调度。
     */
    private void advanceRound(DeliveryOrder order, int nextRound, String reason) {
        // ① 当前事务先把状态回退到待派单（拒单 / 超时的落库与回退在同一事务内完成）
        stateMachine.transit(order.getId(), order.getOrderNo(),
                OrderStatus.DISPATCHING, OrderStatus.PENDING_DISPATCH,
                OperatorType.SYSTEM, null,
                reason + "，准备第 " + nextRound + " 轮派单",
                null, OrderEventType.DISPATCH_ROUND_ADVANCE);

        Long orderId = order.getId();
        if (nextRound > properties.getDispatch().getMaxRound()) {
            markNeedManual(orderId, nextRound - 1);
            log.warn("订单 {} 派单轮次用尽，转人工调度", order.getOrderNo());
            return;
        }

        // ② 回退事务提交后，再开独立事务派下一轮。不能在当前事务里 this 调 dispatchOrder：
        // 那会让「回退 + 整轮候选搜索 + 建任务 + 推送」全挤在一个长事务里长期占用连接。
        // 下一轮失败也不回滚本次拒单 / 超时，交由超时扫描任务或人工兜底。
        afterCommit(() -> {
            try {
                self.dispatchOrder(orderId, nextRound);
            } catch (Exception e) {
                log.error("回退提交后触发第 {} 轮派单失败 orderId={}", nextRound, orderId, e);
            }
        });
    }

    // ==================================================================
    //  六、后台手动派单
    // ==================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DispatchRoundResult manualAssign(String orderNo, Long riderId, Long adminId) {
        DeliveryOrder order = orderService.getEntityByOrderNo(orderNo);

        Rider rider = riderMapper.selectById(riderId);
        if (rider == null) {
            throw new BizException(ErrorCode.RIDER_NOT_FOUND, "riderId=" + riderId);
        }
        if (!rider.isOnline()) {
            throw new BizException(ErrorCode.RIDER_OFFLINE, "骑手 " + rider.getRiderNo());
        }
        int load = rider.getDeliveringCount() == null ? 0 : rider.getDeliveringCount();
        int cap = rider.getMaxCapacity() == null ? 1 : rider.getMaxCapacity();
        if (load >= cap) {
            throw new BizException(ErrorCode.RIDER_CAPACITY_FULL, "骑手 " + rider.getRiderNo());
        }

        OrderStatus status = order.statusEnum();
        if (status != OrderStatus.PENDING_DISPATCH && status != OrderStatus.DISPATCHING) {
            throw new BizException(ErrorCode.ILLEGAL_TRANSITION,
                    "当前状态【" + status.getDesc() + "】不允许派单");
        }

        // 作废该订单所有待响应任务，避免旧任务被误接
        taskMapper.cancelAllPending(order.getId());

        // 若正在派单中，先回退到待派单
        if (status == OrderStatus.DISPATCHING) {
            stateMachine.transit(order.getId(), orderNo,
                    OrderStatus.DISPATCHING, OrderStatus.PENDING_DISPATCH,
                    OperatorType.ADMIN, adminId, "后台介入改派",
                    null, OrderEventType.ADMIN_MANUAL_DISPATCH);
            order = orderMapper.selectById(order.getId());
        }

        // 待派单 -> 派单中
        stateMachine.transit(order.getId(), orderNo,
                OrderStatus.PENDING_DISPATCH, OrderStatus.DISPATCHING,
                OperatorType.ADMIN, adminId,
                "后台手动派单给骑手 " + rider.getRiderNo() + "（" + rider.getName() + "）",
                w -> w.set(DeliveryOrder::getNeedManual, 0)
                        .set(DeliveryOrder::getDispatchRound, 1)
                        .set(DeliveryOrder::getDispatchedAt, LocalDateTime.now()),
                OrderEventType.ADMIN_MANUAL_DISPATCH);

        DispatchTask task = new DispatchTask();
        task.setOrderId(order.getId());
        task.setOrderNo(orderNo);
        task.setRound(1);
        task.setRiderId(riderId);
        task.setCandidateRank(1);
        task.setCandidateScore(BigDecimal.ONE);
        task.setScoreDetail("{\"source\":\"MANUAL_ASSIGN\",\"adminId\":" + adminId + "}");
        task.setStatus(DispatchTaskStatus.PENDING.name());
        task.setExpireAt(LocalDateTime.now()
                .plusSeconds(properties.getDispatch().getResponseTimeoutSeconds()));
        task.setCreatedAt(LocalDateTime.now());
        taskMapper.insert(task);

        // 与自动派单一致：任务落库事务提交后再推送
        scheduleRiderPushes(List.of(new RiderPush(riderId,
                PushMessage.of("DISPATCH_TASK", buildTaskVO(task, order)))));

        log.info("后台手动派单 orderNo={} riderId={} adminId={}", orderNo, riderId, adminId);
        return DispatchRoundResult.dispatched(orderNo, 1, 1, task.getExpireAt(),
                List.of("后台手动指派给 " + rider.getName() + "(" + rider.getRiderNo() + ")"));
    }

    // ==================================================================
    //  七、查询
    // ==================================================================

    @Override
    public List<DispatchTaskVO> listByOrderNo(String orderNo) {
        DeliveryOrder order = orderService.getEntityByOrderNo(orderNo);
        return taskMapper.listByOrderId(order.getId()).stream()
                .map(DispatchTaskVO::of)
                .collect(Collectors.toList());
    }

    @Override
    public List<DispatchTaskVO> pendingTasks(Long riderId) {
        return taskMapper.listPendingByRider(riderId).stream()
                .map(DispatchTaskVO::of)
                .collect(Collectors.toList());
    }

    @Override
    public List<OrderVO> currentOrders(Long riderId) {
        return orderMapper.selectList(Wrappers.<DeliveryOrder>lambdaQuery()
                        .eq(DeliveryOrder::getRiderId, riderId)
                        // 已接单但未送达：待取货 + 配送中
                        .in(DeliveryOrder::getStatus,
                                OrderStatus.PENDING_PICKUP.name(),
                                OrderStatus.DELIVERING.name())
                        .orderByDesc(DeliveryOrder::getId))
                .stream()
                .map(OrderVO::of)
                .collect(Collectors.toList());
    }

    // ==================================================================
    //  工具方法
    // ==================================================================

    /** 承诺配送时长（秒）：从当前时刻到 expectDeliverAt */
    private long promisedSeconds(DeliveryOrder order) {
        if (order.getExpectDeliverAt() == null) {
            return 3600L;
        }
        long s = Duration.between(LocalDateTime.now(), order.getExpectDeliverAt()).getSeconds();
        return Math.max(s, 60L);
    }

    /** 给骑手端推送的任务详情，带上订单摘要让骑手能直接决策 */
    private DispatchTaskVO buildTaskVO(DispatchTask task, DeliveryOrder order) {
        DispatchTaskVO vo = DispatchTaskVO.of(task);
        vo.setPickupAddress(order.getPickupAddress());
        vo.setDeliverAddress(order.getDeliverAddress());
        vo.setDistanceM(order.getDistanceM());
        vo.setAmountFen(order.getAmountFen());
        vo.setAmountYuan(order.getAmountFen() == null ? null
                : BigDecimal.valueOf(order.getAmountFen(), 2).toPlainString());
        vo.setGoodsDesc(order.getGoodsDesc());
        return vo;
    }
}
