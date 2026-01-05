package com.frausto.service.zmq;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Processes ZeroMQ tasks sequentially to avoid contention on socket usage.
 */
@Service
public class ZmqTaskProcessor implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(ZmqTaskProcessor.class);

    private final BlockingQueue<ZmqTask> queue = new LinkedBlockingQueue<>();
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final ExecutorService executor;

    public ZmqTaskProcessor() {
        executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "zmq-task-processor");
            t.setDaemon(true);
            return t;
        });
        executor.submit(this::drainLoop);
    }

    public void submit(ZmqTask task) {
        if (!running.get()) {
            throw new IllegalStateException("ZMQ task processor is shut down");
        }
        queue.offer(task);
    }

    private void drainLoop() {
        while (running.get()) {
            try {
                ZmqTask task = queue.poll(500, TimeUnit.MILLISECONDS);
                if (task != null) {
                    try {
                        task.run();
                    } catch (Exception ex) {
                        String desc = (task.description() == null || task.description().isBlank())
                                ? "zmq-task"
                                : task.description();
                        log.warn("Failed to execute ZMQ task: {}", desc, ex);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    @PreDestroy
    @Override
    public void close() {
        running.set(false);
        executor.shutdownNow();
    }
}
