package com.frausto.service.terminal;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import jakarta.annotation.PreDestroy;

@Service
public class TerminalSessionService {

    private static final Logger log = LoggerFactory.getLogger(TerminalSessionService.class);
    private static final List<String> DEFAULT_SHELL = List.of("/bin/bash");

    private final DockerClient dockerClient;
    private final ExecutorService ioExecutor = Executors.newCachedThreadPool();

    public TerminalSessionService(DockerClient dockerClient) {
        this.dockerClient = dockerClient;
    }

    public TerminalSessionHandle openSession(String containerId, List<String> command, WebSocketSession session) throws IOException {
        List<String> effectiveCmd = command == null || command.isEmpty() ? DEFAULT_SHELL : command;

        ExecCreateCmdResponse exec = dockerClient.execCreateCmd(containerId)
                .withAttachStdout(true)
                .withAttachStderr(true)
                .withAttachStdin(true)
                .withTty(true)
                .withCmd(effectiveCmd.toArray(new String[0]))
                .exec();

        PipedOutputStream stdinSink = new PipedOutputStream();
        PipedInputStream stdinSource = new PipedInputStream(stdinSink);
        TerminalExecCallback callback = new TerminalExecCallback(session);

        ioExecutor.submit(() -> {
            try {
                dockerClient.execStartCmd(exec.getId())
                        .withDetach(false)
                        .withTty(true)
                        .withStdIn(stdinSource)
                        .exec(callback)
                        .awaitCompletion();
            } catch (Exception e) {
                log.error("Terminal session for container {} ended with error", containerId, e);
                closeQuietly(session, CloseStatus.SERVER_ERROR);
            } finally {
                try {
                    stdinSink.close();
                } catch (IOException ignored) {
                }

                if (session.isOpen()) {
                    closeQuietly(session, CloseStatus.NORMAL);
                }
            }
        });

        return new TerminalSessionHandle(exec.getId(), stdinSink, callback);
    }

    public void forwardInput(TerminalSessionHandle handle, byte[] payload) throws IOException {
        Objects.requireNonNull(handle, "Terminal session handle is required");
        handle.forwardInput(payload);
    }

    public void resize(TerminalSessionHandle handle, int cols, int rows) {
        Objects.requireNonNull(handle, "Terminal session handle is required");
        dockerClient.execResizeCmd(handle.getExecId())
                .withWidth(cols)
                .withHeight(rows)
                .exec();
    }

    public void close(TerminalSessionHandle handle) {
        if (handle == null) {
            return;
        }

        try {
            handle.close();
        } catch (IOException e) {
            log.debug("Error while closing terminal session handle", e);
        }
    }

    private void closeQuietly(WebSocketSession session, CloseStatus status) {
        try {
            session.close(status);
        } catch (IOException e) {
            log.debug("Error closing websocket session", e);
        }
    }

    @PreDestroy
    public void shutdown() {
        ioExecutor.shutdownNow();
    }
}
