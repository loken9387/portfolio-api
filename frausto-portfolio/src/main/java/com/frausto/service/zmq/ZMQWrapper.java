package com.frausto.service.zmq;

import com.google.protobuf.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * ZMQWrapper that manages multiple sockets.
 * Operations on the same socket are processed sequentially.
 * Operations on different sockets run concurrently.
 */
@Component
public class ZMQWrapper {
    private static final Logger logger = LoggerFactory.getLogger(ZMQWrapper.class);
    private final SocketManager socketManager;  // SocketManager instance for managing ZeroMQ sockets
    private final ZContext proxyContext;

    /**
     * Private constructor to initialize the ZMQWrapper.
     * It creates a new instance of the SocketManager to manage the sockets.
     */
    private ZMQWrapper() {
        this.socketManager = new SocketManager();  // Initialize SocketManager
        this.proxyContext = new ZContext();
        startProxy();
    }

    private void startProxy() {
        new Thread(() -> {
            try {
                ZMQ.Socket xsub = proxyContext.createSocket(ZMQ.XSUB);
                ZMQ.Socket xpub = proxyContext.createSocket(ZMQ.XPUB);

                xsub.bind("ipc:///zmq/xsub.sock");
                xpub.bind("ipc:///zmq/xpub.sock");

                ZMQ.proxy(xsub, xpub, null);
            } catch (Exception e) {
            }
        }, "ZMQProxyThread").start();
    }

    /**
     * Sends a Protobuf message to the specified socket.
     * The Protobuf message is serialized before being sent.
     *
     * @param socketName The name of the socket to send the message to.
     * @param payload The Protobuf message that will be serialized and sent.
     */
    public <T extends Message> void send(String socketName, String topic, T payload) {
        socketManager.send(socketName, topic, payload);  // Call the send method in SocketManager
    }

    /**
     * Sends a Protobuf message to the specified socket and waits for a response.
     * The Protobuf message is serialized before being sent.
     *
     * @param socketName The name of the socket to send the message to.
     * @param payload The Protobuf message that will be serialized and sent.
     * @return A CompletableFuture containing the response as a String.
     */
    public <T extends Message, R extends Message> CompletableFuture<R> sendAndWait(String socketName, String topic,
                                                                                   T payload) {
        return socketManager.sendAndWait(socketName, topic, payload);  // Delegate to SocketManager's sendAndWait method
    }

    /**
     * Receives a Protobuf message from the specified socket and deserializes it into the provided message class type.
     *
     * @param socketName The name of the socket to receive the message from.
     * @param topic The pub/sub topic
     * @return A CompletableFuture that completes with the deserialized Protobuf message.
     */
    public <T extends Message> CompletableFuture<T> receive(String socketName, String topic) {
        return socketManager.receive(socketName, topic);  // Delegate to SocketManager's receive method
    }

    /**
     * Registers a listener (callback) to handle incoming messages on the specified socket.
     *
     * @param socketName The name of the socket to listen on.
     * @param callback The callback function to handle the received message. It accepts a String message.
     */
    public <T extends Message> void registerListener(String socketName, Consumer<T> callback) {
        socketManager.registerListener(socketName, callback);  // Delegate to SocketManager's registerListener method
    }

    /**
     * Closes all the sockets and the context in the SocketManager, cleaning up resources.
     */
    public void close() {
        socketManager.close();  // Call the close method in SocketManager
        proxyContext.close();
    }

    /**
     * Adds a new socket dynamically after the initial setup. This method allows adding new sockets at runtime.
     *
     * @param socketName The name of the new socket to be added.
     * @param address The address to bind or connect the socket to.
     * @param socketType The type of the socket (e.g., PUB, REP, REQ, etc.).
     * @param bind Whether to bind (`true`) or connect (`false`) the socket to the specified address.
     */
    public void addSocket(String socketName, String address, String socketType, boolean bind) {
        socketManager.initSocket(socketName, address, getSocketType(socketType), bind);  // Call SocketManager's initSocket method
    }

    /**
     * Adds a new socket dynamically after the initial setup. This method allows adding new sockets at runtime.
     *
     * @param socketName The name of the new socket to be added.
     * @param address The address to bind or connect the socket to.
     * @param bind Whether to bind (`true`) or connect (`false`) the socket to the specified address.
     * @param topic The pub/sub topic
     */
    public void addSocket(String socketName, String address, boolean bind, String topic) {
        socketManager.initSocket(socketName, address, bind, topic);  // Call SocketManager's initSocket method
    }

    /**
     * Helper method to convert a string representing a socket type (e.g., "PUB", "REQ") to the corresponding ZMQ socket
     * type constant.
     *
     * @param socketType The string representing the socket type.
     * @return The corresponding ZMQ socket type constant.
     * @throws IllegalArgumentException if the socket type is invalid or unknown.
     */
    private int getSocketType(String socketType) {
        return switch (socketType.toUpperCase()) {  // Convert string to uppercase and check against known socket types
            case "PUB" -> ZMQ.PUB;  // Return PUB socket type
            case "SUB" -> ZMQ.SUB;
            case "REQ" -> ZMQ.REQ;  // Return REQ socket type
            case "REP" -> ZMQ.REP;  // Return REP socket type
            case "PUSH" -> ZMQ.PUSH;  // Return PUSH socket type
            case "PULL" -> ZMQ.PULL;  // Return PULL socket type
            case "DEALER" -> ZMQ.DEALER;  // Return DEALER socket type
            default -> throw new IllegalArgumentException(
                "Invalid socket type: " + socketType);  // Throw error if the type is invalid
        };
    }

//    private void enableMonitor(ZMQ.Socket s, String monAddr) {
//        s.monitor(monAddr, ZMQ.EVENT_ALL);
//    }
}
