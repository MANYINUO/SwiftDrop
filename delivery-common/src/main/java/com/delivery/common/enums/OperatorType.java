package com.delivery.common.enums;

import lombok.Getter;

/**
 * 操作方类型，用于 order_event 记录「谁在什么时候做了什么」。
 */
@Getter
public enum OperatorType {

    /** 下单用户 */
    USER("用户"),

    /** 骑手 */
    RIDER("骑手"),

    /** 系统自动（调度器、定时任务） */
    SYSTEM("系统"),

    /** 调度后台人工 */
    ADMIN("后台管理员");

    private final String desc;

    OperatorType(String desc) {
        this.desc = desc;
    }
}
