package com.delivery.common.enums;

import lombok.Getter;

/**
 * 派单任务状态。
 *
 * <p>一次「派给某个骑手」= 一条 dispatch_task 记录。
 * 一个订单最终只会有一条 {@link #ACCEPTED}。
 */
@Getter
public enum DispatchTaskStatus {

    /** 已推送，等待骑手响应 */
    PENDING("待响应"),

    /** 骑手接单 */
    ACCEPTED("已接单"),

    /** 骑手拒单 */
    REJECTED("已拒单"),

    /** 超时未响应 */
    TIMEOUT("已超时"),

    /** 订单取消 / 改派导致任务作废 */
    CANCELLED("已作废");

    private final String desc;

    DispatchTaskStatus(String desc) {
        this.desc = desc;
    }
}
