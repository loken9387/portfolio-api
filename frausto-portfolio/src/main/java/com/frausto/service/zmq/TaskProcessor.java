package com.frausto.service.zmq;

import com.frausto.proto.service.DockerContainerStatus;
import com.google.protobuf.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.ZMQ;
import org.zeromq.ZMQException;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public class TaskProcessor<T extends Message> implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(TaskProcessor.class);

    private final ZMQ.Socket socket;
    private final BlockingQueue<Task<T>> queue;
    private final AtomicReference<Consumer<T>> callback;
    private volatile boolean isListenerMode = false;

    // topic -> Protobuf class
    private static final Map<String, Class<? extends Message>> topicClassMap = new HashMap<>();
    static {
        topicClassMap.put("docker.status", DockerContainerStatus.class);
    }

    public TaskProcessor(ZMQ.Socket socket,
                         BlockingQueue<Task<T>> queue,
                         Consumer<T> callback) {
        this.socket = socket;
        this.queue = queue;
        this.callback = new AtomicReference<>(callback);
    }

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                if (isListenerMode) {
                    handleMessageListener(); // timeout-safe
                } else {
                    Task<T> task = queue.take();

                    switch (task.type) {
                        case SEND: {
                            // multipart: [topic][payload]
                            socket.send(task.topic, ZMQ.SNDMORE);
                            socket.send(task.payload, 0);
                            break;
                        }
                        case RECEIVE: {
                            boolean done = handleMessageReceiveOnce(task);
                            if (!done) {
                                // Re-queue so we try again on the next loop
                                queue.offer(task);
                            }
                            break;
                        }
                        case SEND_AND_WAIT: {
                            // Only send on the first pass; subsequent retries just recv
                            if (task.phase == Task.Phase.SEND) {
                                boolean sendSuccessful = false;
                                try {
                                    // REQ/DEALER usually send single-part payloads (no topic frame)
                                    sendSuccessful = socket.send(task.payload, 0);
                                } catch (ZMQException e) {
                                }

                                if (!sendSuccessful) {
                                    queue.offer(task); // retry the SEND leg before we attempt to receive
                                    break;
                                }

                                task.phase = Task.Phase.RECV; // now we're awaiting the reply
                            }

                            boolean done = handleMessageReceiveOnce(task);
                            if (!done) {
                                queue.offer(task); // keep trying until it completes
                            } else {
                                // reset in case the same Task instance is ever reused (defensive)
                                task.phase = Task.Phase.SEND;
                            }
                            break;
                        }
                    }
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
            }
        }
    }

    /** Listener mode: keep polling until data arrives; treat timeouts as "no data yet". */
    private void handleMessageListener() {
        // Try to read the first frame as topic (PUB/SUB)
        String topic = socket.recvStr(0); // returns null on timeout when RCVTIMEO set
        if (topic == null) {
            // no data yet (peer down or just idle) → keep looping
            return;
        }

        byte[] payload = socket.recv(0);  // second frame
        if (payload == null) {
            // If a publisher sent single-part unexpectedly (or transient timeout), just return and try again.
            return;
        }

        try {
            T msg = deserializeProtobufMessage(payload, topic);
            Consumer<T> cb = callback.get();
            if (cb != null) cb.accept(msg);
        } catch (Exception e) {
        }
    }

    /**
     * Receive one message for RECEIVE / SEND_AND_WAIT paths.
     * Timeout returns without completing the future. Caller can re-issue or you can adapt to loop.
     */
    private boolean handleMessageReceiveOnce(Task<T> task) {
        // Try to read first frame; could be topic or payload depending on your responder.
        byte[] first = socket.recv(0); // null on timeout
        if (first == null) {
            // timeout: do nothing; keep the future pending
            return false;
        }

        byte[] payload;
        String firstAsString = null;
        try {
            firstAsString = new String(first, StandardCharsets.UTF_8);
        } catch (Exception ignore) {}

        // Heuristic:
        // - If peer sent multipart as [topic][payload] and the topic matches our expected topic, read next as payload
        // - Otherwise, treat the first frame as the payload (single-part reply)
        if (firstAsString != null && firstAsString.equals(task.topic)) {
            payload = socket.recv(0);
            if (payload == null) {
                // rare: responder split frames but payload timed out; try later
                return false;
            }
        } else {
            payload = first; // single-part reply assumed
        }

        try {
            T deserialized = deserializeProtobufMessage(payload, task.topic);
            if (task.result != null) task.result.complete(deserialized);
        } catch (Exception e) {
            if (task.result != null) task.result.completeExceptionally(e);
        }
        return true;
    }

    /** Topic-aware Protobuf deserialization. */
    @SuppressWarnings("unchecked")
    private T deserializeProtobufMessage(byte[] bytes, String topic) throws Exception {
        Class<? extends Message> messageClass = topicClassMap.get(topic);
        if (messageClass == null) throw new IllegalArgumentException("No class found for topic: " + topic);
        return (T) messageClass.getDeclaredMethod("parseFrom", byte[].class).invoke(null, bytes);
    }

    public void enableListenerMode() { isListenerMode = true; }
    public void disableListenerMode() { isListenerMode = false; }

    public void updateCallback(Consumer<T> newCallback) { callback.set(newCallback); }

    public void addTask(Task<T> task) {
        try {
            queue.put(task);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }
}
