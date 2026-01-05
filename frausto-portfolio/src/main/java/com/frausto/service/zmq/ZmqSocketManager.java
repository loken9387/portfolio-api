package com.frausto.service.zmq;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.zeromq.SocketType;
import org.zeromq.ZMQ;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Responsible for creating and tracking ZMQ sockets backed by a shared context.
 */
@Service
public class ZmqSocketManager implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(ZmqSocketManager.class);

    private final ZMQ.Context context;
    private final Map<String, ZMQ.Socket> sockets = new ConcurrentHashMap<>();

    public ZmqSocketManager(@Value("${zmq.ioThreads:1}") int ioThreads) {
        this.context = ZMQ.context(ioThreads);
    }

    public ZMQ.Socket registerSocket(String name, SocketType type, String endpoint, boolean bind) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Socket name is required");
        }
        return sockets.computeIfAbsent(name, key -> {
            ZMQ.Socket socket = context.socket(type);
            if (bind) {
                socket.bind(endpoint);
                log.info("Bound ZMQ {} socket '{}' to {}", type, name, endpoint);
            } else {
                socket.connect(endpoint);
                log.info("Connected ZMQ {} socket '{}' to {}", type, name, endpoint);
            }
            return socket;
        });
    }

    public Optional<ZMQ.Socket> getSocket(String name) {
        return Optional.ofNullable(sockets.get(name));
    }

    public void closeSocket(String name) {
        Optional.ofNullable(sockets.remove(name)).ifPresent(socket -> {
            try {
                socket.close();
            } catch (Exception ex) {
                log.warn("Failed closing socket {}", name, ex);
            }
        });
    }

    @PreDestroy
    @Override
    public void close() {
        sockets.values().forEach(socket -> {
            try {
                socket.close();
            } catch (Exception ex) {
                log.warn("Failed closing ZMQ socket", ex);
            }
        });
        sockets.clear();
        context.close();
    }
}
