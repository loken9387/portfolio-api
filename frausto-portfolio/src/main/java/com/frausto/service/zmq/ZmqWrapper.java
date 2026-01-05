package com.frausto.service.zmq;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.zeromq.SocketType;
import org.zeromq.ZMQ;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.BiConsumer;

/**
 * Provides a central wrapper for ZeroMQ interactions, coordinating sockets, publishing and listeners.
 */
@Service
public class ZmqWrapper implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(ZmqWrapper.class);

    private final ZmqSocketManager socketManager;
    private final ZmqTaskProcessor taskProcessor;
    private final ExecutorService listenerExecutor;

    public ZmqWrapper(ZmqSocketManager socketManager, ZmqTaskProcessor taskProcessor) {
        this.socketManager = socketManager;
        this.taskProcessor = taskProcessor;
        this.listenerExecutor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "zmq-listener");
            t.setDaemon(true);
            return t;
        });
    }

    public void registerPublisher(String name, String endpoint) {
        socketManager.registerSocket(name, SocketType.PUB, endpoint, true);
    }

    public void registerPull(String name, String endpoint) {
        socketManager.registerSocket(name, SocketType.PULL, endpoint, true);
    }

    public void registerPush(String name, String endpoint) {
        socketManager.registerSocket(name, SocketType.PUSH, endpoint, false);
    }

    public ListenerHandle registerListener(String name, String endpoint, Collection<String> topics,
                                           BiConsumer<String, byte[]> handler) {
        ZMQ.Socket socket = socketManager.registerSocket(name, SocketType.SUB, endpoint, false);
        Objects.requireNonNull(handler, "handler");
        if (topics == null || topics.isEmpty()) {
            socket.subscribe(ZMQ.SUBSCRIPTION_ALL);
        } else {
            topics.forEach(topic -> socket.subscribe(topic.getBytes(StandardCharsets.UTF_8)));
        }

        Future<?> future = listenerExecutor.submit(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    String topic = socket.recvStr();
                    byte[] payload = socket.recv();
                    handler.accept(topic, payload);
                } catch (Exception ex) {
                    if (Thread.currentThread().isInterrupted()) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                    log.warn("Listener '{}' encountered an error", name, ex);
                }
            }
        });

        return new ListenerHandle(name, future);
    }

    public void send(String socketName, String topic, byte[] payload) {
        taskProcessor.submit(new ZmqTask("send-" + socketName, () -> {
            ZMQ.Socket socket = socketManager.getSocket(socketName)
                    .orElseThrow(() -> new IllegalStateException("Socket not registered: " + socketName));
            socket.sendMore(topic);
            socket.send(payload);
        }));
    }

    @PreDestroy
    @Override
    public void close() {
        listenerExecutor.shutdownNow();
    }

    public record ListenerHandle(String socketName, Future<?> future) {
        public void stop() {
            future.cancel(true);
        }
    }
}
