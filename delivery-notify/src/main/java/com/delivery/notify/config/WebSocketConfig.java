package com.delivery.notify.config;

import com.delivery.notify.handler.OrderWebSocketHandler;
import com.delivery.notify.handler.RiderWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket 配置。
 *
 * <p>两条通道：
 * <ul>
 *   <li>{@code /ws/user/{orderNo}} —— 用户订阅订单状态与骑手位置</li>
 *   <li>{@code /ws/rider/{riderId}} —— 骑手接收派单推送</li>
 * </ul>
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final OrderWebSocketHandler orderHandler;
    private final RiderWebSocketHandler riderHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(orderHandler, OrderWebSocketHandler.PATH)
                .setAllowedOriginPatterns("*");

        registry.addHandler(riderHandler, RiderWebSocketHandler.PATH)
                .setAllowedOriginPatterns("*");
    }
}
