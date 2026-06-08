package com.delivery.common.enums;

import lombok.Getter;

/**
 * 订单事件类型，写入 order_event 表。
 *
 * <p>排查问题、复盘调度效果、做数据看板都依赖这张流水表。
 */
@Getter
public enum OrderEventType {

    ORDER_CREATED("创建订单"),
    ORDER_CANCELLED("取消订单"),
    STATUS_CHANGED("状态变更"),

    DISPATCH_CREATED("生成派单任务"),
    DISPATCH_ACCEPTED("骑手接单"),
    DISPATCH_REJECTED("骑手拒单"),
    DISPATCH_TIMEOUT("派单超时"),
    /** 候选骑手耗尽，转人工调度 */
    DISPATCH_EXHAUSTED("候选骑手耗尽"),
    DISPATCH_ROUND_ADVANCE("进入下一轮派单"),

    RIDER_PICKED_UP("骑手取货"),
    RIDER_DELIVERED("骑手送达"),
    RIDER_RELEASED("骑手解除关联"),

    ADMIN_RESOLVED("后台人工处理"),
    ADMIN_EXCEPTION("后台标记异常"),
    ADMIN_MANUAL_DISPATCH("后台手动派单");

    private final String desc;

    OrderEventType(String desc) {
        this.desc = desc;
    }
}
