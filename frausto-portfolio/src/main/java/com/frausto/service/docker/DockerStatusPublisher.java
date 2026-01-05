package com.frausto.service.docker;

import com.frausto.proto.service.DockerContainerStatus;
import com.frausto.proto.service.DockerStatusEvent;
import com.frausto.service.zmq.ZMQWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class DockerStatusPublisher {
    private static final Logger log = LoggerFactory.getLogger(DockerStatusPublisher.class);

    private final ZMQWrapper zmqWrapper;
    private final String socketName;
    private final String topic;

    public DockerStatusPublisher(
            ZMQWrapper zmqWrapper,
            @Value("${docker.status.pubEndpoint:tcp://*:5556}") String pubEndpoint) {
        this.zmqWrapper = zmqWrapper;
        this.socketName = "docker-status-pub";
        this.topic = "docker.status";
        this.zmqWrapper.addSocket(socketName, pubEndpoint, "PUB", true);
        log.info("Docker status publisher bound to {} on topic {} (socket {})", pubEndpoint, topic, socketName);
    }

    public DockerStatusEvent publishStatuses(List<DockerContainerStatus> statuses) {
        DockerStatusEvent event = DockerStatusEvent.newBuilder()
                .addAllStatuses(statuses)
                .setGeneratedAtEpochMs(Instant.now().toEpochMilli())
                .build();

        zmqWrapper.send(socketName, topic, event);
        return event;
    }
}
