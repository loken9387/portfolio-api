package com.frausto.service.zmq;

import com.google.protobuf.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

import java.util.concurrent.*;
import java.util.function.Consumer;

public class SocketManager {
    private static final Logger logger = LoggerFactory.getLogger(SocketManager.class);
    private final ZContext context;  // ZeroMQ context used to create and manage sockets
    private final ConcurrentHashMap<String, ZMQ.Socket> socketMap;  // Map to store sockets by their names
    private final ConcurrentHashMap<String, TaskProcessor<?>> taskProcessorMap;  // Map to store TaskProcessor instances
    private final ExecutorService socketWorkers;  // Executor to manage socket worker threads


    /**
     * Place defaults for the sockets
     */
    private static final int DEFAULT_RCV_TIMEOUT_MS = 10000;   // Return null every 1s to allow liveness checks
    private static final int DEFAULT_SND_TIMEOUT_MS = 1000;
    private static final int DEFAULT_RECONNECT_IVL_MS = 250;  // initial backoff
    private static final int DEFAULT_RECONNECT_MAX_MS = 250; // max backoff
    private static final int DEFAULT_RCV_HWM = 1000;
    private static final int DEFAULT_SND_HWM = 1000;

    /**
     * Constructor for SocketManager.
     * Initializes the ZeroMQ context, socket map, task queues, and worker executors.
     */
    public SocketManager() {
        this.context = new ZContext();  // Create a new ZContext for managing ZeroMQ sockets
        this.socketMap = new ConcurrentHashMap<>();  // Map to hold sockets by name
        this.taskProcessorMap = new ConcurrentHashMap<>();  // Initialize the map for TaskProcessors
        this.socketWorkers = Executors.newCachedThreadPool();  // Executor for managing socket tasks
    }

    /**
     * Apply defaults to sockets
     */
    private void applyReliableDefaults(ZMQ.Socket socket) {
        // Fast, non-blocking close
        socket.setLinger(0);

        // Bounded queues
        socket.setRcvHWM(DEFAULT_RCV_HWM);
        socket.setSndHWM(DEFAULT_SND_HWM);

        // Timeouts: ensure recv returns null periodically so listener can keep looping
        socket.setReceiveTimeOut(DEFAULT_RCV_TIMEOUT_MS);
        socket.setSendTimeOut(DEFAULT_SND_TIMEOUT_MS);

        // Reconnect backoff
        socket.setReconnectIVL(DEFAULT_RECONNECT_IVL_MS);
        socket.setReconnectIVLMax(DEFAULT_RECONNECT_MAX_MS);
    }

    /**
     * Adjust receive timeout for a specific socket at runtime (ms).
     */
    public void setReceiveTimeout(String socketName, int millis) {
        ZMQ.Socket s = socketMap.get(socketName);
        if (s == null) throw new IllegalArgumentException("Socket [" + socketName + "] not found!");
        s.setReceiveTimeOut(millis);
    }

    /**
     * Adjust send timeout for a specific socket at runtime (ms).
     */
    public void setSendTimeout(String socketName, int millis) {
        ZMQ.Socket s = socketMap.get(socketName);
        if (s == null) throw new IllegalArgumentException("Socket [" + socketName + "] not found!");
        s.setSendTimeOut(millis);
    }



    /**
     * Initializes a new socket or connects it if already not initialized.
     *
     * @param socketName The name of the socket.
     * @param address    The address to bind/connect the socket.
     * @param socketType The type of socket (e.g., PUB, REP, etc.).
     * @param bind       Whether to bind or connect the socket.
     */
    public <T extends Message> void initSocket(String socketName, String address, int socketType, boolean bind) {
        socketMap.computeIfAbsent(socketName, key -> {
            ZMQ.Socket socket = context.createSocket(socketType);  // Create the specified socket type

            applyReliableDefaults(socket);

            if (bind) {
                System.out.println("Bound Socket " + socketName + " to address " + address);
                socket.bind(address);  // Bind the socket to the address
            } else {
                System.out.println("Connect Socket " + socketName + " to address " + address);
                socket.setImmediate(false);
                socket.connect(address);  // Connect the socket to the address
            }

            BlockingQueue<Task<T>> taskQueue = new LinkedBlockingQueue<>();  // Create a queue for task management

            TaskProcessor<T> processor = new TaskProcessor<>(socket, taskQueue, null);  // Create a task processor for this socket
            taskProcessorMap.put(socketName, processor);  // Add the TaskProcessor to the map
            socketWorkers.submit(processor);  // Start the task processor in a worker thread

            return socket;  // Return the initialized socket
        });
    }

    /**
     * Initializes a new socket or connects it if already not initialized.
     *
     * @param socketName The name of the socket.
     * @param address The address to bind/connect the socket.
     * @param bind Whether to bind or connect the socket.
     * @param subTopic The subscription topic
     */
    public <T extends Message> void initSocket(String socketName, String address, boolean bind, String subTopic) {
        // Validate subTopic is not null or empty
        if (subTopic == null) {
            throw new IllegalArgumentException("subTopic cannot be null or empty");
        }

        socketMap.computeIfAbsent(socketName, key -> {
            ZMQ.Socket socket = context.createSocket(ZMQ.SUB);  // Create the specified socket type

            applyReliableDefaults(socket);

            if (bind) {
                System.out.println("Bound Socket " + socketName + " to address " + address);
                socket.bind(address);  // Bind the socket to the address
            } else {
                System.out.println("Connect Socket " + socketName + " to address " + address);
                socket.connect(address);  // Connect the socket to the address
            }

            socket.subscribe(subTopic.getBytes());

            BlockingQueue<Task<T>> taskQueue = new LinkedBlockingQueue<>();  // Create a queue for task management

            TaskProcessor<T> processor = new TaskProcessor<>(socket, taskQueue, null);  // Create a task processor for this socket
            processor.enableListenerMode();
            taskProcessorMap.put(socketName, processor);  // Add the TaskProcessor to the map
            socketWorkers.submit(processor);  // Start the task processor in a worker thread

            return socket;  // Return the initialized socket
        });
    }

    /**
     * Registers a listener (callback) for receiving messages from a specified socket.
     *
     * @param socketName The name of the socket to listen on.
     * @param callback The callback function to handle the received messages.
     */
    public <T extends Message> void registerListener(String socketName, Consumer<T> callback) {
        ZMQ.Socket socket = socketMap.get(socketName);  // Retrieve the socket by name
        if (socket == null) {
            throw new IllegalArgumentException("Socket [" + socketName + "] not found!");
        }

        @SuppressWarnings("unchecked")
        TaskProcessor<T> processor = (TaskProcessor<T>) taskProcessorMap.get(socketName);
        if (processor != null) {
            processor.updateCallback(callback);  // Update the callback dynamically
            processor.enableListenerMode();  // Enable listener mode
        }
    }

    /**
     * Sends a Protobuf message to a specified socket after serializing it into a byte array.
     *
     * @param socketName The name of the socket to send the message to.
     * @param protobufMessage The Protobuf message to be serialized and sent.
     */
    public <T extends Message> void send(String socketName, String topic, T protobufMessage) {
        byte[] serializedMessage = serializeProtobufMessage(protobufMessage);  // Serialize the Protobuf message

        submitTask(socketName, new Task<>(Task.TaskType.SEND, topic, serializedMessage));  // Submit the send task to the socket
    }

    /**
     * Sends a Protobuf message and waits for a response.
     * The Protobuf message is serialized before being sent.
     *
     * @param socketName The name of the socket to send the message to.
     * @param protobufMessage The Protobuf message to be serialized and sent.
     * @return A CompletableFuture containing the response as a String.
     */
    public <T extends Message, R extends Message> CompletableFuture<R> sendAndWait(String socketName, String topic,
                                                                                   T protobufMessage) {
        CompletableFuture<R> result = new CompletableFuture<>();  // Create a CompletableFuture for the result
        byte[] serializedMessage = protobufMessage.toByteArray();  // Serialize the Protobuf message
        submitTask(socketName, new Task<>(Task.TaskType.SEND_AND_WAIT, topic, serializedMessage, result));  // Submit the send and wait task
        return result;  // Return the CompletableFuture for the response
    }

    /**
     * Serializes a Protobuf message to a byte array.
     *
     * @param message The Protobuf message to serialize.
     * @return The serialized byte array of the message.
     */
    private byte[] serializeProtobufMessage(Message message) {
        return message.toByteArray();  // Use the Protobuf Message's method to serialize it to a byte array
    }

    /**
     * Receives a Protobuf message from the specified socket and deserializes it into the given message class type.
     *
     * @param socketName The name of the socket to receive the message from.
     * @param topic The message topic
     * @return A CompletableFuture that completes with the deserialized Protobuf message.
     */
    public <T extends Message> CompletableFuture<T> receive(String socketName, String topic) {
        CompletableFuture<T> result = new CompletableFuture<>();

        Task<T> task = new Task<>(Task.TaskType.RECEIVE, topic, null, result);  // Create a receive task

        submitTask(socketName, task);  // Submit the task to the socket's queue

        return task.result;
    }

    /**
     * Submits a task to the task queue associated with the specified socket.
     *
     * @param socketName The name of the socket to submit the task to.
     * @param task The task to submit (e.g., send, receive).
     */
    private <T extends Message> void submitTask(String socketName, Task<T> task) {
        TaskProcessor<?> taskProcessor = taskProcessorMap.get(socketName);  // Retrieve the task queue for the socket

        if (taskProcessor == null) {
            throw new IllegalArgumentException("Socket [" + socketName + "] not found!");  // Throw an error if the socket is not found
        }

        if (taskProcessor instanceof TaskProcessor<?>) {
            @SuppressWarnings("unchecked")
            TaskProcessor<T> typedTaskProcessor = (TaskProcessor<T>) taskProcessor;
            typedTaskProcessor.addTask(task);  // Add the task to the queue for processing
        } else {
            throw new ClassCastException("TaskProcessor type mismatch for socket: " + socketName);
        }
    }

    /**
     * Closes all the sockets and the ZeroMQ context, ensuring proper resource cleanup.
     */
    public void close() {
        // Ensure all tasks are completed before closing the context and sockets
        socketWorkers.shutdown();

        try {
            if (!socketWorkers.awaitTermination(60, TimeUnit.SECONDS)) {
                socketWorkers.shutdownNow();  // Force shutdown if tasks don't complete in time
            }
        } catch (InterruptedException e) {
            socketWorkers.shutdownNow();
        }

        // Close all sockets and the context
        socketMap.values().forEach(ZMQ.Socket::close);  // Close each socket
        context.close();  // Close the ZeroMQ context
    }
}
