package com.delivery.common.enums;

import lombok.Getter;

import java.util.Arrays;

/**
 * 订单状态枚举
 * 用字符串存数据库（不用数字），排查问题时直接能看懂
 * terminal=true 表示终态（不会再变了），false 表示还能继续流转
 */
@Getter
public enum OrderStatus {

    PENDING_DISPATCH("待派单", false),   // 订单刚创建，等调度器派单
    DISPATCHING("派单中", false),        // 已生成派单任务，等骑手响应
    PENDING_PICKUP("待取货", false),     // 骑手已接单，正在去取件的路上
    DELIVERING("配送中", false),         // 骑手已取货，正在配送
    DELIVERED("已送达", true),           // 终态
    CANCELLED("已取消", true),           // 终态
    EXCEPTION("异常", false);            // 非终态！人工处理后可改派或取消

    private final String desc;       // 中文描述，给前端展示用
    private final boolean terminal;  // 是否终态

    OrderStatus(String desc, boolean terminal) {
        this.desc = desc;
        this.terminal = terminal;
    }

    /**
     * 把字符串转成枚举，找不到就抛异常（防止数据库里出现脏数据）
     */
    public static OrderStatus of(String name) {
        return Arrays.stream(values())
                .filter(s -> s.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未知订单状态: " + name));
    }
}
