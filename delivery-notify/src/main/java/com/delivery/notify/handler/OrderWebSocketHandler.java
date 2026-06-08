package com.delivery.notify.handler;

import com.delivery.notify.dto.PushMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;

/**
 * 用户端 WebSocket：订阅某个订单的状态与骑手位置变化。
 *
 * <p>连接地址：{@code ws://localhost:8080/ws/user/{orderNo}}
 *
 * <p><b>断线重连约定</b>：客户端重连后应立刻调
 * {@code GET /api/user/orders/{orderNo}/track} 拉一次全量状态，
 * 再依赖推送增量更新。这样即使断线期间漏了推送，状态也不会错
 * —— 对应验收场景「WebSocket 断开后，用户重新进入仍能看到正确状态」。
 */
@Slf4j
@Component
public class OrderWebSocketHandler extends AbstractSessionHandler {

    public static final String PATH = "/ws/user/{orderNo}";

    public OrderWebSocketHandler(ObjectMapper objectMapper) {
        super(objectMapper);
    }

    @Override
    protected String resolveKey(WebSocketSession session) {
        // 取 /ws/user/{orderNo} 的最后一段
        return lastPathSegment(session);
    }

    @Override
    protected PushMessage onConnected(String orderNo) {
        return PushMessage.of("CONNECTED", Map.of(
                "orderNo", orderNo,
                "tip", "连接成功。重连后请先调用 /track 接口拉取全量状态"
        ));
    }
}
