package com.frausto.service.terminal;

import org.springframework.web.socket.WebSocketSession;

public interface TerminalSessionAuthorizer {
    boolean isAuthorized(WebSocketSession session, String containerId);

    default boolean isAuthorized(String containerId) {
        return true;
    }
}
