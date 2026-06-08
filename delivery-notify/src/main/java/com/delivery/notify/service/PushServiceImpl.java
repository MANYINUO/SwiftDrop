package com.delivery.notify.service;

import com.delivery.notify.dto.PushMessage;
import com.delivery.notify.handler.OrderWebSocketHandler;
import com.delivery.notify.handler.RiderWebSocketHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 推送服务实现 —— 业务代码与 WebSocket 连接之间的「门面 / 转发层」。
 *
 * <p>业务方（派单引擎、事件监听器）只面向 {@link PushService} 接口发消息，
 * 不需要知道连接具体存在哪个 Handler 的内存 Map 里；这里按订阅 key 把消息
 * 转发给对应的 Handler，由它广播给该 key 下的所有连接。
 *
 * <p><b>单机限制</b>：连接表是各 Handler 进程内的 {@code ConcurrentHashMap}，
 * 只在当前后端实例里有效。多实例部署时（用户连在 A 节点、事件却发生在 B 节点就推不到），
 * 需把这一层换成 Redis Pub/Sub 或 MQ 广播：先把消息发到频道，由真正持有连接的实例
 * 收到后再在本地广播。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PushServiceImpl implements PushService {

    private final OrderWebSocketHandler orderHandler;
    private final RiderWebSocketHandler riderHandler;

    @Override
    public void pushToOrder(String orderNo, PushMessage message) {
        orderHandler.broadcast(orderNo, message);
    }

    @Override
    public void pushToRider(Long riderId, PushMessage message) {
        riderHandler.broadcast(String.valueOf(riderId), message);
    }

    @Override
    public int userSessionCount() {
        return orderHandler.totalSessions();
    }

    @Override
    public int riderSessionCount() {
        return riderHandler.totalSessions();
    }
}
