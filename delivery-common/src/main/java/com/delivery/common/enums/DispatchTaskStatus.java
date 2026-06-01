package com.delivery.common.enums;

import lombok.Getter;

/**
 * 派单任务状态枚举
 * 一次「派给某个骑手」= 一条 dispatch_task 记录
 * 一个订单最终只会有一条 ACCEPTED
 */
@Getter
public enum DispatchTaskStatus {

    PENDING("待响应"),     // 已推送给骑手，等他点接单或拒单
    ACCEPTED("已接单"),    // 骑手接单了
    REJECTED("已拒单"),    // 骑手拒单了
    TIMEOUT("已超时"),     // 骑手超时没响应
    CANCELLED("已作废");   // 订单取消或改派，这条任务作废

    private final String desc;

    DispatchTaskStatus(String desc) {
        this.desc = desc;
    }
}
