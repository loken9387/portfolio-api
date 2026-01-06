package com.frausto.service.terminal;

import com.github.dockerjava.api.async.ResultCallbackTemplate;
import com.github.dockerjava.api.model.Frame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;

public class TerminalExecCallback extends ResultCallbackTemplate<TerminalExecCallback, Frame> {
    private static final Logger log = LoggerFactory.getLogger(TerminalExecCallback.class);

    private final WebSocketSession session;

    public TerminalExecCallback(WebSocketSession session) {
        this.session = session;
    }

    @Override
    public void onNext(Frame frame) {
        if (!session.isOpen()) {
            try {
                close();
            } catch (IOException e) {
                log.debug("Error closing callback after socket closed", e);
            }
            return;
        }

        try {
            session.sendMessage(new BinaryMessage(frame.getPayload()));
        } catch (IOException e) {
            log.warn("Unable to forward terminal data to websocket", e);
            try {
                session.close(CloseStatus.SERVER_ERROR);
            } catch (IOException ignored) {
                // ignore secondary close errors
            }
        }
    }
}