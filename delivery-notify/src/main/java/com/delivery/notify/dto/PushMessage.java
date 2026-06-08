package com.delivery.notify.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * WebSocket 推送消息体。
 */
@Data
public class PushMessage implements Serializable {

    /** 消息类型：ORDER_STATUS / RIDER_LOCATION / DISPATCH_TASK */
    private String type;

    private long timestamp;

    private Object data;

    public static PushMessage of(String type, Object data) {
        PushMessage m = new PushMessage();
        m.type = type;
        m.timestamp = System.currentTimeMillis();
        m.data = data;
        return m;
    }
}
