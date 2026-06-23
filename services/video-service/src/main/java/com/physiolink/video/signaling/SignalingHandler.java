package com.physiolink.video.signaling;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebRTC signaling handler — routes offer, answer, and ICE candidate messages
 * between peers in the same room.
 *
 * Message format:
 * {
 *   "type": "offer" | "answer" | "candidate" | "join",
 *   "roomId": "<appointment-video-room-id>",
 *   "payload": { ... SDP or candidate ... }
 * }
 */
@Component
public class SignalingHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(SignalingHandler.class);

    // roomId → map of sessionId → WebSocketSession
    private final Map<String, Map<String, WebSocketSession>> rooms = new ConcurrentHashMap<>();
    // sessionId → roomId (for cleanup on disconnect)
    private final Map<String, String> sessionRoom = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("WebSocket connected: {}", session.getId());
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        Map<String, Object> msg = objectMapper.readValue(message.getPayload(), Map.class);
        String type   = (String) msg.get("type");
        String roomId = (String) msg.get("roomId");

        if (type == null || roomId == null) return;

        switch (type) {
            case "join" -> {
                rooms.computeIfAbsent(roomId, k -> new ConcurrentHashMap<>())
                     .put(session.getId(), session);
                sessionRoom.put(session.getId(), roomId);
                log.info("Session {} joined room {}", session.getId(), roomId);
            }
            case "offer", "answer", "candidate" -> broadcast(session, roomId, message.getPayload());
            default -> log.warn("Unknown signaling type: {}", type);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String roomId = sessionRoom.remove(session.getId());
        if (roomId != null) {
            Map<String, WebSocketSession> room = rooms.get(roomId);
            if (room != null) {
                room.remove(session.getId());
                if (room.isEmpty()) rooms.remove(roomId);
            }
        }
        log.info("WebSocket disconnected: {} status={}", session.getId(), status);
    }

    /** Send the message to all other participants in the room */
    private void broadcast(WebSocketSession sender, String roomId, String payload) {
        Map<String, WebSocketSession> room = rooms.getOrDefault(roomId, Map.of());
        for (Map.Entry<String, WebSocketSession> entry : room.entrySet()) {
            if (!entry.getKey().equals(sender.getId()) && entry.getValue().isOpen()) {
                try {
                    entry.getValue().sendMessage(new TextMessage(payload));
                } catch (IOException e) {
                    log.error("Failed to relay signaling message: {}", e.getMessage());
                }
            }
        }
    }
}
