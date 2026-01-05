package com.frausto.service.zmq;

import com.google.protobuf.Message;

import java.util.concurrent.CompletableFuture;

public class Task <T extends Message> {
    // Enum representing the different types of tasks
    public enum TaskType {
        SEND,  // Task to send a message
        RECEIVE,  // Task to receive a message
        SEND_AND_WAIT  // Task to send a message and wait for a reply
    }

    public enum Phase {
        SEND,
        RECV
    }

    TaskType type;  // The type of the task (SEND, RECEIVE, or SEND_AND_WAIT)
    String topic; // The topic of the message that was sent/received
    byte[] payload;  // The message payload (in byte array format)
    CompletableFuture<T> result;  // Holds the result for RECEIVE or SEND_AND_WAIT tasks

    public volatile Phase phase = Phase.SEND;
    /**
     * Constructor for send tasks (no result needed).
     *
     * @param type The type of task (SEND).
     * @param topic the topic of the message to send
     * @param payload The payload of the message to send.
     */
    public Task(TaskType type, String topic, byte[] payload) {
        this.type = type;  // Set the task type
        this.topic = topic;
        this.payload = payload;  // Set the message payload
        this.result = null;  // No result for send tasks
    }

    /**
     * Constructor for receive tasks (needs result).
     *
     * @param type The type of task (RECEIVE or SEND_AND_WAIT).
     * @param payload The payload of the message to send (for SEND_AND_WAIT).
     * @param result The CompletableFuture that holds the result of the task.
     */
    public Task(TaskType type, String topic, byte[] payload, CompletableFuture<T> result) {
        this.type = type;  // Set the task type (RECEIVE or SEND_AND_WAIT)
        this.topic = topic; // Set the topic of the message
        this.payload = payload;  // Set the message payload
        this.result = result;  // Set the CompletableFuture to hold the result of the task
    }
}

