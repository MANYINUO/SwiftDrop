package com.delivery.common.enums;

import lombok.Getter;

/**
 * 操作方类型，写入 order_event 表，记录「谁在什么时候做了什么」
 */
@Getter
public enum OperatorType {

    USER("用户"),           // 下单用户
    RIDER("骑手"),          // 骑手
    SYSTEM("系统"),         // 调度器、定时任务等自动操作
    ADMIN("后台管理员");    // 调度后台人工操作

    private final String desc;

    OperatorType(String desc) {
        this.desc = desc;
    }
}
