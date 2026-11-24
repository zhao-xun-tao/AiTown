package com.aitown.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 管理前端 WebSocket 连接，并向所有订阅者广播世界快照 JSON。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorldWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;
    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
        log.info("WebSocket 已连接: {}，当前连接数={}", session.getId(), sessions.size());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
        log.info("WebSocket 已断开: {}，当前连接数={}", session.getId(), sessions.size());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // Phase 1：前端只收不发；预留心跳/订阅协议
        log.debug("收到客户端消息: {}", message.getPayload());
    }

    /**
     * 将任意对象序列化为 JSON 并广播给所有在线会话。
     */
    public void broadcast(Object payload) {
        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (IOException e) {
            log.error("世界状态序列化失败", e);
            return;
        }

        TextMessage message = new TextMessage(json);
        for (WebSocketSession session : sessions) {
            if (!session.isOpen()) {
                sessions.remove(session);
                continue;
            }
            try {
                synchronized (session) {
                    session.sendMessage(message);
                }
            } catch (IOException e) {
                log.warn("向会话 {} 推送失败，移除连接", session.getId(), e);
                sessions.remove(session);
            }
        }
    }
}
