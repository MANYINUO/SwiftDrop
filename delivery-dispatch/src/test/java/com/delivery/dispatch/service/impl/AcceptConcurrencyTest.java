package com.delivery.dispatch.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.delivery.common.config.DeliveryProperties;
import com.delivery.common.enums.OrderStatus;
import com.delivery.common.exception.BizException;
import com.delivery.common.exception.ErrorCode;
import com.delivery.dispatch.dto.AcceptResult;
import com.delivery.dispatch.entity.DispatchTask;
import com.delivery.dispatch.mapper.DispatchTaskMapper;
import com.delivery.dispatch.service.CandidateSelector;
import com.delivery.notify.service.PushService;
import com.delivery.order.entity.DeliveryOrder;
import com.delivery.order.mapper.DeliveryOrderMapper;
import com.delivery.order.service.OrderService;
import com.delivery.order.statemachine.OrderStateMachine;
import com.delivery.rider.entity.Rider;
import com.delivery.rider.mapper.RiderMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 接单并发测试 —— 覆盖「四步原子事务」的安全边界。
 *
 * <p>接单是整个项目并发最关键的一步。这里不去 mock 掉最核心的那一步（CAS），
 * 而是分别验证四个失败点各自会带来什么后果：
 * <ol>
 *   <li>订单已被别人抢走 → 抛 {@code ORDER_ALREADY_TAKEN}，<b>不能</b>动骑手容量</li>
 *   <li>派单任务已被处理 → 抛 {@code DISPATCH_TASK_NOT_PENDING}，<b>不能</b>动容量</li>
 *   <li>骑手容量已满 → 抛 {@code RIDER_CAPACITY_FULL}（第四步失败，前一步已执行）</li>
 *   <li>重复接单 → 幂等返回，<b>不能</b>重复加容量</li>
 * </ol>
 *
 * <p>另外用真实多线程跑一次「N 个骑手抢同一单」，断言只有一个成功。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("接单 · 四步原子事务")
class AcceptConcurrencyTest {

    private static final Long ORDER_ID = 2001L;
    private static final String ORDER_NO = "SD20261019000002";
    private static final Long TASK_ID = 3001L;
    private static final Long RIDER_A = 11L;
    private static final Long RIDER_B = 12L;

    @Mock private DeliveryOrderMapper orderMapper;
    @Mock private DispatchTaskMapper taskMapper;
    @Mock private RiderMapper riderMapper;
    @Mock private OrderStateMachine stateMachine;
    @Mock private CandidateSelector candidateSelector;
    @Mock private PushService pushService;
    @Mock private OrderService orderService;
    @Mock private DeliveryProperties properties;

    @InjectMocks
    private DispatchServiceImpl dispatchService;

    @BeforeAll
    static void initTableInfoCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, DispatchTask.class);
        TableInfoHelper.initTableInfo(assistant, DeliveryOrder.class);
    }

    /** 构造一个处于「派单中」的订单 */
    private DeliveryOrder dispatchingOrder() {
        DeliveryOrder o = new DeliveryOrder();
        o.setId(ORDER_ID);
        o.setOrderNo(ORDER_NO);
        o.setStatus(OrderStatus.DISPATCHING.name());
        o.setUserId(5001L);
        o.setVersion(0);
        o.setDispatchRound(1);
        o.setNeedManual(0);
        return o;
    }

    /** 构造一个 PENDING 且未过期的派单任务 */
    private DispatchTask pendingTask(Long riderId) {
        DispatchTask t = new DispatchTask();
        t.setId(TASK_ID);
        t.setOrderId(ORDER_ID);
        t.setOrderNo(ORDER_NO);
        t.setRound(1);
        t.setRiderId(riderId);
        t.setCandidateRank(1);
        t.setStatus("PENDING");
        t.setExpireAt(LocalDateTime.now().plusSeconds(15));
        t.setCreatedAt(LocalDateTime.now());
        return t;
    }

    private Rider onlineRider(Long id, int load, int capacity) {
        Rider r = new Rider();
        r.setId(id);
        r.setRiderNo("R" + id);
        r.setName("骑手" + id);
        r.setOnlineStatus(1);
        r.setDeliveringCount(load);
        r.setMaxCapacity(capacity);
        return r;
    }

    // ==================================================================
    //  四步中每一步失败的后果
    // ==================================================================

    @Nested
    @DisplayName("四步失败点")
    class FailurePoints {

        @Test
        @DisplayName("第一步 CAS 失败（订单被抢走）：抛 ORDER_ALREADY_TAKEN，不动容量")
        void casFailureOnOrderThrowsAlreadyTaken() {
            when(taskMapper.selectById(TASK_ID)).thenReturn(pendingTask(RIDER_A));
            when(orderMapper.selectById(ORDER_ID)).thenReturn(dispatchingOrder());

            // 状态机 CAS 抛并发冲突，模拟「读状态」和「写状态」之间被抢走
            doThrow(new BizException(ErrorCode.CONCURRENT_MODIFIED))
                    .when(stateMachine).transit(
                            anyLong(), anyString(), any(), any(), any(), any(), anyString(),
                            any(), any());

            assertThatThrownBy(() -> dispatchService.accept(RIDER_A, TASK_ID))
                    .isInstanceOf(BizException.class)
                    .extracting(e -> ((BizException) e).getCode())
                    .isEqualTo(ErrorCode.ORDER_ALREADY_TAKEN.getCode());

            // 关键：订单都没抢到，绝不能去加骑手容量（否则就是超卖）
            verify(riderMapper, never()).increaseLoad(anyLong());
            verify(taskMapper, never()).markAccepted(anyLong());
        }

        @Test
        @DisplayName("第二步任务已被处理：抛 DISPATCH_TASK_NOT_PENDING，不动容量")
        void markAcceptedFailureRollsBack() {
            when(taskMapper.selectById(TASK_ID)).thenReturn(pendingTask(RIDER_A));
            when(orderMapper.selectById(ORDER_ID)).thenReturn(dispatchingOrder());
            // 第二步影响 0 行 = 任务已被别人处理
            when(taskMapper.markAccepted(TASK_ID)).thenReturn(0);

            assertThatThrownBy(() -> dispatchService.accept(RIDER_A, TASK_ID))
                    .isInstanceOf(BizException.class)
                    .extracting(e -> ((BizException) e).getCode())
                    .isEqualTo(ErrorCode.DISPATCH_TASK_NOT_PENDING.getCode());

            verify(riderMapper, never()).increaseLoad(anyLong());
        }

        @Test
        @DisplayName("第四步容量已满：抛 RIDER_CAPACITY_FULL，事务回滚")
        void capacityFullThrowsAndRollsBack() {
            when(taskMapper.selectById(TASK_ID)).thenReturn(pendingTask(RIDER_A));
            when(orderMapper.selectById(ORDER_ID)).thenReturn(dispatchingOrder());
            when(taskMapper.markAccepted(TASK_ID)).thenReturn(1);
            // 第四步：increaseLoad 影响 0 行 = 已满载
            when(riderMapper.increaseLoad(RIDER_A)).thenReturn(0);

            assertThatThrownBy(() -> dispatchService.accept(RIDER_A, TASK_ID))
                    .isInstanceOf(BizException.class)
                    .extracting(e -> ((BizException) e).getCode())
                    .isEqualTo(ErrorCode.RIDER_CAPACITY_FULL.getCode());

            // 前三步已执行，但第四步失败 —— 真实环境下 @Transactional 会整体回滚。
            // 单测里验证的是「异常确实抛出了」，回滚由 Spring 事务保证。
            verify(taskMapper).markAccepted(TASK_ID);
            verify(riderMapper).increaseLoad(RIDER_A);
        }

        @Test
        @DisplayName("订单已被其他骑手接走：快速失败，不进 CAS")
        void orderAlreadyTakenFastFail() {
            DispatchTask task = pendingTask(RIDER_A);
            when(taskMapper.selectById(TASK_ID)).thenReturn(task);

            DeliveryOrder taken = dispatchingOrder();
            taken.setRiderId(RIDER_B);
            taken.setStatus(OrderStatus.PENDING_PICKUP.name());
            when(orderMapper.selectById(ORDER_ID)).thenReturn(taken);

            assertThatThrownBy(() -> dispatchService.accept(RIDER_A, TASK_ID))
                    .isInstanceOf(BizException.class)
                    .extracting(e -> ((BizException) e).getCode())
                    .isEqualTo(ErrorCode.ORDER_ALREADY_TAKEN.getCode());

            // 快速失败：连 CAS 都不该调用
            verify(stateMachine, never()).transit(
                    anyLong(), anyString(), any(), any(), any(), any(), anyString(), any(), any());
            verify(riderMapper, never()).increaseLoad(anyLong());
        }
    }

    // ==================================================================
    //  幂等
    // ==================================================================

    @Nested
    @DisplayName("幂等")
    class Idempotency {

        @Test
        @DisplayName("重复接单（任务已 ACCEPTED）：返回幂等结果，不重复加容量")
        void repeatedAcceptReturnsIdempotentResult() {
            DispatchTask accepted = pendingTask(RIDER_A);
            accepted.setStatus("ACCEPTED");
            when(taskMapper.selectById(TASK_ID)).thenReturn(accepted);

            AcceptResult result = dispatchService.accept(RIDER_A, TASK_ID);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.isIdempotent()).isTrue();
            assertThat(result.getOrderNo()).isEqualTo(ORDER_NO);

            // 核心：幂等命中时绝不能再次 +1，否则骑手会凭空多出负载
            verify(riderMapper, never()).increaseLoad(anyLong());
            verify(stateMachine, never()).transit(
                    anyLong(), anyString(), any(), any(), any(), any(), anyString(), any(), any());
        }

        @Test
        @DisplayName("任务已过期：抛 DISPATCH_TASK_EXPIRED，不动容量")
        void expiredTaskIsRejected() {
            DispatchTask expired = pendingTask(RIDER_A);
            expired.setExpireAt(LocalDateTime.now().minusSeconds(1));
            when(taskMapper.selectById(TASK_ID)).thenReturn(expired);
            when(orderMapper.selectById(ORDER_ID)).thenReturn(dispatchingOrder());

            assertThatThrownBy(() -> dispatchService.accept(RIDER_A, TASK_ID))
                    .isInstanceOf(BizException.class)
                    .extracting(e -> ((BizException) e).getCode())
                    .isEqualTo(ErrorCode.DISPATCH_TASK_EXPIRED.getCode());

            verify(riderMapper, never()).increaseLoad(anyLong());
        }

        @Test
        @DisplayName("骑手操作他人任务：抛 RIDER_NOT_OWNER")
        void riderCannotAcceptOthersTask() {
            when(taskMapper.selectById(TASK_ID)).thenReturn(pendingTask(RIDER_A));

            assertThatThrownBy(() -> dispatchService.accept(RIDER_B, TASK_ID))
                    .isInstanceOf(BizException.class)
                    .extracting(e -> ((BizException) e).getCode())
                    .isEqualTo(ErrorCode.RIDER_NOT_OWNER.getCode());

            verify(riderMapper, never()).increaseLoad(anyLong());
        }

        @Test
        @DisplayName("任务状态非 PENDING（如已 TIMEOUT）：拒绝接单")
        void nonPendingTaskIsRejected() {
            DispatchTask timeout = pendingTask(RIDER_A);
            timeout.setStatus("TIMEOUT");
            when(taskMapper.selectById(TASK_ID)).thenReturn(timeout);

            assertThatThrownBy(() -> dispatchService.accept(RIDER_A, TASK_ID))
                    .isInstanceOf(BizException.class)
                    .extracting(e -> ((BizException) e).getCode())
                    .isEqualTo(ErrorCode.DISPATCH_TASK_NOT_PENDING.getCode());

            verify(riderMapper, never()).increaseLoad(anyLong());
        }
    }

    // ==================================================================
    //  并发抢单：真实多线程
    // ==================================================================

    @Nested
    @DisplayName("并发抢单")
    class ConcurrentGrab {

        /**
         * 模拟 N 个骑手同时抢同一单。
         *
         * <p>用「CAS 只成功一次」来模拟数据库的条件更新语义：多个线程中只有一个
         * 能拿到 {@code accept} 的成功结果，其余必须失败。这验证的是
         * 「订单状态只在第一次成功时流转」这条核心不变量。
         */
        @Test
        @DisplayName("N 个骑手抢同一单：恰好 1 人成功，其余全部失败")
        void onlyOneRiderWins() throws Exception {
            int riders = 20;
            ExecutorService pool = Executors.newFixedThreadPool(riders);
            CountDownLatch startGate = new CountDownLatch(1);
            CountDownLatch doneGate = new CountDownLatch(riders);

            AtomicInteger successCount = new AtomicInteger();
            AtomicInteger failureCount = new AtomicInteger();
            AtomicInteger capacityIncreased = new AtomicInteger();

            // 用原子变量模拟数据库的「条件更新只成功一次」
            java.util.concurrent.atomic.AtomicBoolean orderClaimed =
                    new java.util.concurrent.atomic.AtomicBoolean(false);

            for (int i = 0; i < riders; i++) {
                long riderId = 100L + i;
                long taskId = 5000L + i;

                // 每个骑手都有一条自己的 PENDING 任务
                DispatchTask task = new DispatchTask();
                task.setId(taskId);
                task.setOrderId(ORDER_ID);
                task.setOrderNo(ORDER_NO);
                task.setRound(1);
                task.setRiderId(riderId);
                task.setStatus("PENDING");
                task.setExpireAt(LocalDateTime.now().plusSeconds(15));
                when(taskMapper.selectById(taskId)).thenReturn(task);

                pool.submit(() -> {
                    try {
                        startGate.await();
                        dispatchService.accept(riderId, taskId);
                        successCount.incrementAndGet();
                    } catch (BizException e) {
                        failureCount.incrementAndGet();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        doneGate.countDown();
                    }
                });
            }

            // 订单读取：第一次成功抢占后，后续读取都看到「已被接走」
            when(orderMapper.selectById(ORDER_ID)).thenAnswer(inv -> {
                DeliveryOrder o = dispatchingOrder();
                if (orderClaimed.get()) {
                    o.setRiderId(RIDER_A);
                    o.setStatus(OrderStatus.PENDING_PICKUP.name());
                }
                return o;
            });

            // 状态机 CAS：只有第一次调用成功，其余抛并发冲突
            doAnswer(inv -> {
                if (!orderClaimed.compareAndSet(false, true)) {
                    throw new BizException(ErrorCode.CONCURRENT_MODIFIED);
                }
                return null;
            }).when(stateMachine).transit(
                    anyLong(), anyString(), any(), any(), any(), any(), anyString(), any(), any());

            when(taskMapper.markAccepted(anyLong())).thenReturn(1);
            when(taskMapper.cancelOtherPending(anyLong(), anyLong())).thenReturn(0);
            when(riderMapper.increaseLoad(anyLong())).thenAnswer(inv -> {
                capacityIncreased.incrementAndGet();
                return 1;
            });

            startGate.countDown();
            assertThat(doneGate.await(10, TimeUnit.SECONDS)).isTrue();
            pool.shutdown();

            // 核心不变量：恰好一人成功
            assertThat(successCount.get())
                    .as("并发抢单必须恰好一人成功")
                    .isEqualTo(1);
            assertThat(failureCount.get()).isEqualTo(riders - 1);

            // 容量只能被加一次，否则就是超卖
            assertThat(capacityIncreased.get())
                    .as("骑手容量只能被 +1 一次")
                    .isEqualTo(1);
        }
    }
}
