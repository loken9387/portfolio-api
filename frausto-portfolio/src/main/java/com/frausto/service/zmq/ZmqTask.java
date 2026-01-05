package com.frausto.service.zmq;

/**
 * Represents a unit of ZeroMQ work to be executed by the task processor.
 */
public record ZmqTask(String description, Runnable action) implements Runnable {
    public ZmqTask {
        if (action == null) {
            throw new IllegalArgumentException("Action must not be null");
        }
    }

    @Override
    public void run() {
        action.run();
    }
}
