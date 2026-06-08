package com.delivery.notify.handler;

import com.delivery.notify.dto.PushMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;

/**
 * 骑手端 WebSocket：接收派单推送。
 *
 * <p>连接地址：{@code ws://localhost:8080/ws/rider/{riderId}}
 *
 * <p>有了长连接，骑手不用轮询拉派单任务；断线期间漏掉的派单，
 * 重连后可以调 {@code GET /api/rider/{riderId}/dispatch-tasks} 补偿。
 */
@Slf4j
@Component
public class RiderWebSocketHandler extends AbstractSessionHandler {

    public static final String PATH = "/ws/rider/{riderId}";

    public RiderWebSocketHandler(ObjectMapper objectMapper) {
        super(objectMapper);
    }

    @Override
    protected String resolveKey(WebSocketSession session) {
        // 取 /ws/rider/{riderId} 的最后一段
        return lastPathSegment(session);
    }

    @Override
    protected PushMessage onConnected(String riderId) {
        return PushMessage.of("CONNECTED", Map.of(
                "riderId", riderId,
                "tip", "连接成功。重连后请调用 /dispatch-tasks 拉取未响应的派单"
        ));
    }
}
