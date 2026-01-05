package com.frausto.service.zmq;

import com.frausto.proto.DockerContainerStatus;
import com.frausto.proto.DockerStatusEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class DockerStatusPublisher {
    private static final Logger log = LoggerFactory.getLogger(DockerStatusPublisher.class);

    private final ZmqWrapper zmqWrapper;
    private final String socketName;
    private final String topic;

    public DockerStatusPublisher(
            ZmqWrapper zmqWrapper,
            @Value("${docker.status.pubEndpoint:tcp://*:5556}") String pubEndpoint,
            @Value("${docker.status.topic:docker.status}") String topic,
            @Value("${docker.status.pubSocketName:docker-status-pub}") String socketName) {
        this.zmqWrapper = zmqWrapper;
        this.socketName = socketName;
        this.topic = topic;
        this.zmqWrapper.registerPublisher(socketName, pubEndpoint);
        log.info("Docker status publisher bound to {} on topic {} (socket {})", pubEndpoint, topic, socketName);
    }

    public DockerStatusEvent publishStatuses(List<DockerContainerStatus> statuses) {
        DockerStatusEvent event = DockerStatusEvent.newBuilder()
                .addAllStatuses(statuses)
                .setGeneratedAtEpochMs(Instant.now().toEpochMilli())
                .build();

        zmqWrapper.send(socketName, topic, event.toByteArray());
        return event;
    }
}
