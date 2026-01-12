package com.frausto.config;

import com.frausto.model.docker.dto.DockerServiceConfigRequest;
import com.frausto.model.docker.entity.DockerServiceConfig;
import com.frausto.repository.DockerRepository;
import com.frausto.service.docker.DockerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.context.event.EventListener;

@Configuration
public class DockerConfigInitializer {
    private static final Logger log = LoggerFactory.getLogger(DockerConfigInitializer.class);
    private final DockerService dockerService;

    @Autowired
    public DockerConfigInitializer(DockerService dockerService) {
        this.dockerService = dockerService;
    }

    @Bean
    @Order(1)
    CommandLineRunner seedDefaultDockerConfig(DockerRepository dockerRepository) {
        return args -> {
            boolean hasConfigs = dockerRepository.count() > 0;
            if (hasConfigs) {
                return;
            }

            DockerServiceConfigRequest defaultConfig = new DockerServiceConfigRequest();
            defaultConfig.setName("default-nginx");
            defaultConfig.setContainerName("portfolio-nginx");
            defaultConfig.setDescription("Default container for serving static content");
            defaultConfig.setImage("nginx:latest");
            defaultConfig.setRestartPolicy("always");
            defaultConfig.setNetworkMode("bridge");

            DockerServiceConfig created = dockerService.createConfig(defaultConfig);
            log.info("Seeded default Docker config with id {}", created.getId());
        };
    }

    @EventListener(ApplicationReadyEvent.class)
    public void startManagedContainers(ApplicationReadyEvent event) {
        log.info("Reconciling managed containers after application readiness");
        dockerService.reconcileStartupContainers();
    }
}
