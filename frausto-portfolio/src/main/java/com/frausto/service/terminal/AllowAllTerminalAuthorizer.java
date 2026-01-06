package com.frausto.service.terminal;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

/**
 * Default no-op authorizer. Replace with a JWT/session-aware implementation when wiring authentication.
 */
@Component
public class AllowAllTerminalAuthorizer implements TerminalSessionAuthorizer {
    @Override
    public boolean isAuthorized(WebSocketSession session, String containerId) {
        return true;
    }
}