package com.frausto.web.terminal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.frausto.service.terminal.TerminalSessionAuthorizer;
import com.frausto.service.terminal.TerminalSessionHandle;
import com.frausto.service.terminal.TerminalSessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Component
public class TerminalWebSocketHandler extends AbstractWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(TerminalWebSocketHandler.class);
    private static final String SESSION_HANDLE_ATTR = "terminalSessionHandle";

    private final TerminalSessionService terminalSessionService;
    private final TerminalSessionAuthorizer authorizer;
    private final ObjectMapper objectMapper;

    public TerminalWebSocketHandler(TerminalSessionService terminalSessionService,
                                    TerminalSessionAuthorizer authorizer,
                                    ObjectMapper objectMapper) {
        this.terminalSessionService = terminalSessionService;
        this.authorizer = authorizer;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        MultiValueMap<String, String> params = getQueryParams(session.getUri());
        String containerId = params.getFirst("containerId");

        if (!StringUtils.hasText(containerId)) {
            session.close(CloseStatus.BAD_DATA.withReason("containerId is required"));
            return;
        }

        if (!authorizer.isAuthorized(session, containerId)) {
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("Unauthorized"));
            return;
        }

        List<String> command = parseCommand(params.getFirst("cmd"));

        try {
            TerminalSessionHandle handle = terminalSessionService.openSession(containerId, command, session);
            session.getAttributes().put(SESSION_HANDLE_ATTR, handle);
        } catch (Exception e) {
            log.error("Unable to start terminal session for container {}", containerId, e);
            session.close(CloseStatus.SERVER_ERROR);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        TerminalSessionHandle handle = (TerminalSessionHandle) session.getAttributes().get(SESSION_HANDLE_ATTR);
        if (handle == null) {
            session.close(CloseStatus.SERVER_ERROR.withReason("No terminal session"));
            return;
        }

        if (tryHandleResize(handle, message.getPayload())) {
            return;
        }

        terminalSessionService.forwardInput(handle, message.getPayload().getBytes(StandardCharsets.UTF_8));
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) throws Exception {
        TerminalSessionHandle handle = (TerminalSessionHandle) session.getAttributes().get(SESSION_HANDLE_ATTR);
        if (handle == null) {
            session.close(CloseStatus.SERVER_ERROR.withReason("No terminal session"));
            return;
        }

        byte[] payload = new byte[message.getPayload().remaining()];
        message.getPayload().get(payload);
        terminalSessionService.forwardInput(handle, payload);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        TerminalSessionHandle handle = (TerminalSessionHandle) session.getAttributes().get(SESSION_HANDLE_ATTR);
        terminalSessionService.close(handle);
    }

    private MultiValueMap<String, String> getQueryParams(URI uri) {
        if (uri == null) {
            return UriComponentsBuilder.newInstance().build().getQueryParams();
        }
        return UriComponentsBuilder.fromUri(uri).build().getQueryParams();
    }

    private List<String> parseCommand(String cmd) {
        if (!StringUtils.hasText(cmd)) {
            return new ArrayList<>();
        }
        return List.of(cmd.split(" +"));
    }

    private boolean tryHandleResize(TerminalSessionHandle handle, String payload) {
        try {
            JsonNode node = objectMapper.readTree(payload);
            JsonNode typeNode = node.get("type");

            if (typeNode == null || !"resize".equalsIgnoreCase(typeNode.asText())) {
                return false;
            }

            int cols = node.path("cols").asInt(0);
            int rows = node.path("rows").asInt(0);

            if (cols > 0 && rows > 0) {
                terminalSessionService.resize(handle, cols, rows);
            }
            return true;
        } catch (IOException ex) {
            // not a JSON payload, treat as terminal data instead
            return false;
        }
    }
}
