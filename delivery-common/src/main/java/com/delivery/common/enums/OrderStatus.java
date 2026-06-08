package com.delivery.common.enums;

import lombok.Getter;

import java.util.Arrays;

/**
 * 订单状态。
 *
 * <p>用字符串而非数字存储，排查问题时可读性更好。
 * <p><b>注意</b>：{@link #EXCEPTION} 是<b>非终态</b>——它必须能回到待派单（改派）或流转到已取消，
 * 否则人工处理完订单就"死"在那里了。
 */
@Getter
public enum OrderStatus {

    /** 待派单：订单已创建，等待调度器处理（need_manual=1 时表示需人工调度） */
    PENDING_DISPATCH("待派单", false),

    /** 派单中：已生成派单任务，等待骑手响应 */
    DISPATCHING("派单中", false),

    /** 待取货：骑手已接单，正在前往取件点 */
    PENDING_PICKUP("待取货", false),

    /** 配送中：骑手已取货，配送途中 */
    DELIVERING("配送中", false),

    /** 已送达：终态 */
    DELIVERED("已送达", true),

    /** 已取消：终态 */
    CANCELLED("已取消", true),

    /** 异常：需人工介入，可改派或取消。非终态！ */
    EXCEPTION("异常", false);

    private final String desc;
    private final boolean terminal;

    OrderStatus(String desc, boolean terminal) {
        this.desc = desc;
        this.terminal = terminal;
    }

    public static OrderStatus of(String name) {
        return Arrays.stream(values())
                .filter(s -> s.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未知订单状态: " + name));
    }
}
