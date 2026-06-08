package com.delivery.dispatch.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 接单结果。
 */
@Data
public class AcceptResult implements Serializable {

    private boolean success;
    /** 是否命中幂等（重复提交，返回首次结果） */
    private boolean idempotent;
    private String orderNo;
    private Long orderId;
    private String message;

    public static AcceptResult success(String orderNo, Long orderId) {
        AcceptResult r = new AcceptResult();
        r.success = true;
        r.orderNo = orderNo;
        r.orderId = orderId;
        r.message = "接单成功";
        return r;
    }

    public static AcceptResult alreadyAccepted(String orderNo) {
        AcceptResult r = new AcceptResult();
        r.success = true;
        r.idempotent = true;
        r.orderNo = orderNo;
        r.message = "您已接过该订单（重复请求，返回首次结果）";
        return r;
    }
}
