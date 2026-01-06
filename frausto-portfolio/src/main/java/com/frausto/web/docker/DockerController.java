package com.frausto.web.docker;
import com.frausto.proto.service.DockerContainerStatus;
import com.frausto.proto.service.DockerStatusEvent;
import com.frausto.service.docker.DockerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/docker")
public class DockerController {

    private final DockerService dockerService;

    public DockerController(DockerService dockerService) {
        this.dockerService = dockerService;
    }

    @PostMapping("/configs/{configId}/start")
    public ResponseEntity<Map<String, String>> startContainer(@PathVariable Long configId) {
        String containerId = dockerService.startContainer(configId);
        return ResponseEntity.ok(Map.of("containerId", containerId));
    }

    @DeleteMapping("/configs/{configId}/containers")
    public ResponseEntity<Void> removeContainersForConfig(@PathVariable Long configId,
                                                          @RequestParam(defaultValue = "false") boolean force) {
        dockerService.removeContainersForConfig(configId, force);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/status")
    public List<DockerContainerStatus> getContainerStatuses() {
        return dockerService.getContainerStatuses();
    }

    @PostMapping("/status/broadcast")
    public DockerStatusEvent broadcastContainerStatuses() {
        return dockerService.broadcastContainerStatuses();
    }
}
