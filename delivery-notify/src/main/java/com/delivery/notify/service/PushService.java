package com.delivery.notify.service;

import com.delivery.notify.dto.PushMessage;

/**
 * 推送服务。
 *
 * <p>对上层屏蔽「消息怎么发出去」的细节：调用方只管说「推给这个订单 / 这个骑手」。
 * 当前实现是单机 WebSocket；多实例部署时换成「Redis 发布订阅 + 各节点广播」即可，
 * 调用方代码不用改。
 */
public interface PushService {

    /** 推给正在查看该订单的所有用户连接 */
    void pushToOrder(String orderNo, PushMessage message);

    /** 推给某个骑手的所有连接 */
    void pushToRider(Long riderId, PushMessage message);

    /** 当前用户连接数 */
    int userSessionCount();

    /** 当前骑手连接数 */
    int riderSessionCount();
}
