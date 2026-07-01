package com.delivery.order.statemachine;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.delivery.common.enums.OperatorType;
import com.delivery.common.enums.OrderEventType;
import com.delivery.common.enums.OrderStatus;
import com.delivery.common.exception.BizException;
import com.delivery.common.exception.ErrorCode;
import com.delivery.order.entity.DeliveryOrder;
import com.delivery.order.entity.OrderEvent;
import com.delivery.order.event.OrderStatusChangedEvent;
import com.delivery.order.mapper.DeliveryOrderMapper;
import com.delivery.order.mapper.OrderEventMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

import java.util.EnumSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 订单状态机测试 —— 覆盖「两层防护」。
 *
 * <p>测试重点不是枚举表本身，而是两条容易被误以为等价、实际职责完全不同的防线：
 * <ul>
 *   <li><b>第一层 · 合法性</b>：内存转移表拦截非法流转，此时<b>不应发起任何 SQL</b>。</li>
 *   <li><b>第二层 · 并发</b>：CAS 更新影响行数为 0 时必须抛 {@code CONCURRENT_MODIFIED}，
 *       且<b>不能</b>写事件流水、<b>不能</b>发状态变更事件——否则会出现
 *       「状态没改成、但事件流水记了一笔」的脏数据。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("订单状态机")
class OrderStateMachineTest {

    private static final Long ORDER_ID = 1001L;
    private static final String ORDER_NO = "SD20261019000001";
    private static final Long RIDER_ID = 7L;

    @Mock
    private DeliveryOrderMapper orderMapper;

    @Mock
    private OrderEventMapper eventMapper;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private OrderStateMachine stateMachine;

    /**
     * MyBatis-Plus 的 LambdaUpdateWrapper 依赖 TableInfo 缓存来解析
     * {@code DeliveryOrder::getStatus} 这类方法引用。纯 Mockito 单测没有 MyBatis 上下文，
     * 必须先手动注册实体元信息，否则会抛「can not find lambda cache for this entity」。
     */
    @BeforeAll
    static void initTableInfoCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, DeliveryOrder.class);
    }

    @BeforeEach
    void setUp() {
        // 默认：CAS 更新成功（影响 1 行）
        when(orderMapper.update(isNull(), any())).thenReturn(1);
    }

    // ==================================================================
    //  第一层：合法性校验（纯内存，不打库）
    // ==================================================================

    @Nested
    @DisplayName("第一层 · 合法性校验")
    class Legality {

        @Test
        @DisplayName("主流程正向流转全部合法")
        void happyPathTransitionsAreAllowed() {
            assertThat(stateMachine.canTransit(OrderStatus.PENDING_DISPATCH, OrderStatus.DISPATCHING)).isTrue();
            assertThat(stateMachine.canTransit(OrderStatus.DISPATCHING, OrderStatus.PENDING_PICKUP)).isTrue();
            assertThat(stateMachine.canTransit(OrderStatus.PENDING_PICKUP, OrderStatus.DELIVERING)).isTrue();
            assertThat(stateMachine.canTransit(OrderStatus.DELIVERING, OrderStatus.DELIVERED)).isTrue();
        }

        @Test
        @DisplayName("终态不允许任何流转")
        void terminalStatusHasNoExit() {
            assertThat(stateMachine.allowedTargets(OrderStatus.DELIVERED)).isEmpty();
            assertThat(stateMachine.allowedTargets(OrderStatus.CANCELLED)).isEmpty();

            for (OrderStatus to : OrderStatus.values()) {
                assertThat(stateMachine.canTransit(OrderStatus.DELIVERED, to)).isFalse();
                assertThat(stateMachine.canTransit(OrderStatus.CANCELLED, to)).isFalse();
            }
        }

        @Test
        @DisplayName("已送达不能回退到配送中")
        void deliveredCannotGoBackToDelivering() {
            assertThat(stateMachine.canTransit(OrderStatus.DELIVERED, OrderStatus.DELIVERING)).isFalse();
        }

        @Test
        @DisplayName("配送中不允许直接取消，只能走异常或送达")
        void deliveringCannotBeCancelledDirectly() {
            assertThat(stateMachine.canTransit(OrderStatus.DELIVERING, OrderStatus.CANCELLED)).isFalse();
            // 但可以标记异常，再由异常走取消
            assertThat(stateMachine.canTransit(OrderStatus.DELIVERING, OrderStatus.EXCEPTION)).isTrue();
            assertThat(stateMachine.canTransit(OrderStatus.EXCEPTION, OrderStatus.CANCELLED)).isTrue();
        }

        @Test
        @DisplayName("异常是非终态，必须能改派或取消")
        void exceptionIsNotTerminal() {
            assertThat(OrderStatus.EXCEPTION.isTerminal()).isFalse();
            assertThat(stateMachine.canTransit(OrderStatus.EXCEPTION, OrderStatus.PENDING_DISPATCH)).isTrue();
            assertThat(stateMachine.canTransit(OrderStatus.EXCEPTION, OrderStatus.CANCELLED)).isTrue();
        }

        @Test
        @DisplayName("拒单/超时回退：派单中可回到待派单")
        void dispatchingCanFallBackToPendingDispatch() {
            assertThat(stateMachine.canTransit(OrderStatus.DISPATCHING, OrderStatus.PENDING_DISPATCH)).isTrue();
            assertThat(stateMachine.canTransit(OrderStatus.PENDING_PICKUP, OrderStatus.PENDING_DISPATCH)).isTrue();
        }

        @Test
        @DisplayName("空状态一律不合法")
        void nullStatusIsRejected() {
            assertThat(stateMachine.canTransit(null, OrderStatus.DISPATCHING)).isFalse();
            assertThat(stateMachine.canTransit(OrderStatus.DISPATCHING, null)).isFalse();
            assertThat(stateMachine.canTransit(null, null)).isFalse();
        }

        @Test
        @DisplayName("非法流转在内存层就被拦截：不发 SQL、不写事件")
        void illegalTransitionNeverTouchesDatabase() {
            assertThatThrownBy(() -> stateMachine.transit(
                    ORDER_ID, ORDER_NO,
                    OrderStatus.DELIVERED, OrderStatus.DELIVERING,
                    OperatorType.RIDER, RIDER_ID, "非法回退"))
                    .isInstanceOf(BizException.class)
                    .extracting(e -> ((BizException) e).getCode())
                    .isEqualTo(ErrorCode.ILLEGAL_TRANSITION.getCode());

            // 关键：第一层拦截后，不应该有任何落库动作
            verify(orderMapper, never()).update(any(), any());
            verify(eventMapper, never()).insert(any(OrderEvent.class));
            verify(eventPublisher, never()).publishEvent(any(OrderStatusChangedEvent.class));
        }
    }

    // ==================================================================
    //  第二层：CAS 落库（并发安全的关键）
    // ==================================================================

    @Nested
    @DisplayName("第二层 · CAS 落库")
    class Concurrency {

        @Test
        @DisplayName("CAS 成功：写事件流水并发布状态变更事件")
        void successfulTransitRecordsEventAndPublishes() {
            stateMachine.transit(ORDER_ID, ORDER_NO,
                    OrderStatus.DISPATCHING, OrderStatus.PENDING_PICKUP,
                    OperatorType.RIDER, RIDER_ID, "骑手接单");

            // ① CAS 更新被调用
            verify(orderMapper).update(isNull(), any());

            // ② 事件流水被写入，且字段正确
            ArgumentCaptor<OrderEvent> eventCaptor = ArgumentCaptor.forClass(OrderEvent.class);
            verify(eventMapper).insert(eventCaptor.capture());
            OrderEvent event = eventCaptor.getValue();
            assertThat(event.getOrderId()).isEqualTo(ORDER_ID);
            assertThat(event.getOrderNo()).isEqualTo(ORDER_NO);
            assertThat(event.getFromStatus()).isEqualTo(OrderStatus.DISPATCHING.name());
            assertThat(event.getToStatus()).isEqualTo(OrderStatus.PENDING_PICKUP.name());
            assertThat(event.getOperatorType()).isEqualTo(OperatorType.RIDER.name());
            assertThat(event.getOperatorId()).isEqualTo(RIDER_ID);
            assertThat(event.getEventType()).isEqualTo(OrderEventType.STATUS_CHANGED.name());

            // ③ 状态变更事件被发布（notify 模块据此推送）
            verify(eventPublisher).publishEvent(any(OrderStatusChangedEvent.class));
        }

        @Test
        @DisplayName("CAS 失败（影响 0 行）：抛并发冲突，且不写事件、不发布")
        void casFailureThrowsAndSkipsSideEffects() {
            when(orderMapper.update(isNull(), any())).thenReturn(0);

            assertThatThrownBy(() -> stateMachine.transit(
                    ORDER_ID, ORDER_NO,
                    OrderStatus.DISPATCHING, OrderStatus.PENDING_PICKUP,
                    OperatorType.RIDER, RIDER_ID, "骑手接单"))
                    .isInstanceOf(BizException.class)
                    .extracting(e -> ((BizException) e).getCode())
                    .isEqualTo(ErrorCode.CONCURRENT_MODIFIED.getCode());

            // 核心断言：CAS 失败后绝不能留下「状态没改、事件却记了」的脏数据
            verify(eventMapper, never()).insert(any(OrderEvent.class));
            verify(eventPublisher, never()).publishEvent(any(OrderStatusChangedEvent.class));
        }

        @Test
        @DisplayName("带附加字段的流转：customizer 被应用且仍走 CAS")
        void transitWithCustomizer() {
            stateMachine.transit(ORDER_ID, ORDER_NO,
                    OrderStatus.DISPATCHING, OrderStatus.PENDING_PICKUP,
                    OperatorType.RIDER, RIDER_ID, "骑手接单",
                    w -> w.set(DeliveryOrder::getRiderId, RIDER_ID),
                    OrderEventType.DISPATCH_ACCEPTED);

            verify(orderMapper).update(isNull(), any());
            ArgumentCaptor<OrderEvent> captor = ArgumentCaptor.forClass(OrderEvent.class);
            verify(eventMapper).insert(captor.capture());
            assertThat(captor.getValue().getEventType())
                    .isEqualTo(OrderEventType.DISPATCH_ACCEPTED.name());
        }
    }

    // ==================================================================
    //  事件流水（不改变状态）
    // ==================================================================

    @Nested
    @DisplayName("仅记录事件")
    class RecordOnly {

        @Test
        @DisplayName("recordEvent 只写流水，不改状态、不发布事件")
        void recordEventDoesNotChangeStatus() {
            stateMachine.recordEvent(ORDER_ID, ORDER_NO,
                    OrderEventType.DISPATCH_EXHAUSTED,
                    OrderStatus.PENDING_DISPATCH, OrderStatus.PENDING_DISPATCH,
                    OperatorType.SYSTEM, null,
                    "第 3 轮无可用候选骑手");

            verify(orderMapper, never()).update(any(), any());
            verify(eventPublisher, never()).publishEvent(any(OrderStatusChangedEvent.class));

            ArgumentCaptor<OrderEvent> captor = ArgumentCaptor.forClass(OrderEvent.class);
            verify(eventMapper).insert(captor.capture());
            OrderEvent e = captor.getValue();
            assertThat(e.getEventType()).isEqualTo(OrderEventType.DISPATCH_EXHAUSTED.name());
            assertThat(e.getOperatorType()).isEqualTo(OperatorType.SYSTEM.name());
            assertThat(e.getOperatorId()).isNull();
        }

        @Test
        @DisplayName("记录事件时 from 为空也能正常落库（如创建订单）")
        void recordEventAllowsNullFromStatus() {
            stateMachine.recordEvent(ORDER_ID, ORDER_NO,
                    OrderEventType.ORDER_CREATED, null, OrderStatus.PENDING_DISPATCH,
                    OperatorType.USER, 5001L, "下单成功");

            ArgumentCaptor<OrderEvent> captor = ArgumentCaptor.forClass(OrderEvent.class);
            verify(eventMapper).insert(captor.capture());
            assertThat(captor.getValue().getFromStatus()).isNull();
            assertThat(captor.getValue().getToStatus()).isEqualTo(OrderStatus.PENDING_DISPATCH.name());
        }
    }

    // ==================================================================
    //  转移表完备性
    // ==================================================================

    @ParameterizedTest(name = "{0} 的合法目标不为 null")
    @EnumSource(OrderStatus.class)
    @DisplayName("每个状态都有明确的转移表条目（缺失会静默变成不允许）")
    void everyStatusHasEntry(OrderStatus status) {
        Set<OrderStatus> targets = stateMachine.allowedTargets(status);
        assertThat(targets).isNotNull();
        // 除终态外，其余状态至少有一条出路，避免订单「卡死」
        if (!status.isTerminal()) {
            assertThat(targets).isNotEmpty();
        }
    }

    @Test
    @DisplayName("转移表内不允许自环（同状态到同状态）")
    void noSelfLoop() {
        for (OrderStatus from : OrderStatus.values()) {
            Set<OrderStatus> targets = stateMachine.allowedTargets(from);
            assertThat(targets)
                    .as("状态 %s 不应允许流转到自身", from)
                    .doesNotContain(from);
        }
    }

    @Test
    @DisplayName("除异常与回退路径外，状态只能前进")
    void statusOnlyMovesForward() {
        // 已送达 / 已取消是终态
        assertThat(EnumSet.of(OrderStatus.DELIVERED, OrderStatus.CANCELLED))
                .allMatch(OrderStatus::isTerminal);

        // 待取货可以取消（尚未取货），但配送中不行——这条铁律必须被守住
        assertThat(stateMachine.canTransit(OrderStatus.PENDING_PICKUP, OrderStatus.CANCELLED)).isTrue();
        assertThat(stateMachine.canTransit(OrderStatus.DELIVERING, OrderStatus.CANCELLED)).isFalse();
    }
}
