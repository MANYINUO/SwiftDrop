package com.delivery.notify.handler;

import com.delivery.notify.dto.PushMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 会话注册中心：管理「谁连在哪里」。
 *
 * <p>用户端按订单号订阅（一个订单可能被多端同时查看，所以是 Set）；
 * 骑手端按骑手 ID 订阅（一个骑手可能多设备登录）。
 *
 * <p>单机实现。多实例部署时需要把「推送」从本地 Map 换成
 * Redis 发布订阅或 MQ 广播，否则用户连在 A 节点、事件发生在 B 节点就推不到。
 */
@Slf4j
public abstract class AbstractSessionHandler extends TextWebSocketHandler {

    protected final ObjectMapper objectMapper;

    /** key: 订阅标识（订单号 / 骑手ID）→ 该标识下的所有连接 */
    private final Map<String, Set<WebSocketSession>> sessions = new ConcurrentHashMap<>();

    protected AbstractSessionHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** 从连接路径中解析订阅标识，例如 /ws/user/{orderNo} -> orderNo */
    protected abstract String resolveKey(WebSocketSession session);

    /**
     * 取路径的最后一段作为订阅标识。
     *
     * <p><b>注意</b>：不要用 {@code HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE} ——
     * WebSocket 的 handler 不经过 Spring MVC 的参数解析，那个属性是空的，
     * 会导致所有连接都因为「解析不出标识」被立即关闭（踩过这个坑）。
     * 直接解析 URI 路径最可靠。
     */
    protected String lastPathSegment(WebSocketSession session) {
        java.net.URI uri = session.getUri();
        if (uri == null) {
            return null;
        }
        String path = uri.getPath();
        if (path == null || path.isEmpty()) {
            return null;
        }
        int idx = path.lastIndexOf('/');
        String key = idx >= 0 ? path.substring(idx + 1) : path;
        return key.isEmpty() ? null : key;
    }

    /** 连接建立后的欢迎消息 */
    protected abstract PushMessage onConnected(String key);

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String key = resolveKey(session);
        if (key == null) {
            log.warn("无法解析订阅标识，关闭连接 uri={}", session.getUri());
            closeQuietly(session);
            return;
        }
        sessions.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet()).add(session);
        log.info("WebSocket 已连接 key={} sessionId={} 当前该 key 连接数={}",
                key, session.getId(), sessions.get(key).size());
        send(session, onConnected(key));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String key = resolveKey(session);
        if (key == null) {
            return;
        }
        Set<WebSocketSession> set = sessions.get(key);
        if (set != null) {
            set.remove(session);
            if (set.isEmpty()) {
                sessions.remove(key);
            }
        }
        log.info("WebSocket 已断开 key={} sessionId={} status={}", key, session.getId(), status);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String payload = message.getPayload();
        // 支持客户端主动发 ping 保活
        if ("ping".equalsIgnoreCase(payload)) {
            send(session, PushMessage.of("PONG", null));
            return;
        }
        // 其余消息暂不处理；真实项目可在这里做「订阅切换」等指令
        log.debug("收到客户端消息 key={} payload={}", resolveKey(session), payload);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.warn("WebSocket 传输异常 sessionId={}", session.getId(), exception);
        closeQuietly(session);
    }

    /** 向某个 key 下的所有连接广播 */
    public void broadcast(String key, PushMessage message) {
        Set<WebSocketSession> set = sessions.get(key);
        if (set == null || set.isEmpty()) {
            return;
        }
        set.forEach(s -> send(s, message));
    }

    public Set<WebSocketSession> sessionsOf(String key) {
        return sessions.getOrDefault(key, Collections.emptySet());
    }

    public int totalSessions() {
        return sessions.values().stream().mapToInt(Set::size).sum();
    }

    protected void send(WebSocketSession session, PushMessage message) {
        if (session == null || !session.isOpen()) {
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(message);
            synchronized (session) {
                session.sendMessage(new TextMessage(json));
            }
        } catch (IOException e) {
            log.warn("推送失败，关闭连接 sessionId={}", session.getId(), e);
            closeQuietly(session);
        }
    }

    private void closeQuietly(WebSocketSession session) {
        try {
            session.close();
        } catch (IOException ignored) {
            // 关闭失败无所谓，连接最终会被容器回收
        }
    }
}
