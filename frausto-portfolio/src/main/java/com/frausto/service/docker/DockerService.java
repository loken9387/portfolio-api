package com.frausto.service.docker;

import com.frausto.model.docker.entity.DockerEnvVar;
import com.frausto.model.docker.entity.DockerPortMapping;
import com.frausto.model.docker.entity.DockerServiceConfig;
import com.frausto.model.docker.entity.DockerVolumeMapping;
import com.frausto.repository.DockerRepository;
import com.frausto.service.util.InstanceTracker;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.exception.DockerException;
import com.github.dockerjava.api.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * DockerService defines the contract for managing Docker-based service configurations.
 */
@Service
public class DockerService {
    private static final Logger log = LoggerFactory.getLogger(DockerService.class);

    /* Docker client for interacting with the docker daemon */
    private final DockerClient dockerClient;

    /* Instance Tracker to better manage containers with multiple instances */
    private final InstanceTracker instanceTracker;

    /* Docker service config repo */
    private final DockerRepository dockerRepo;

    public DockerService(DockerClient dockerClient, InstanceTracker instanceTracker, DockerRepository dockerRepository) {
        this.dockerClient = dockerClient;
        this.instanceTracker = instanceTracker;
        this.dockerRepo = dockerRepository;
    }

    public String startContainer(Long configId) {

        DockerServiceConfig cfg = dockerRepo.findById(configId).orElseThrow(() -> new IllegalArgumentException(
                "No DockerServiceConfig with id " + configId
        ));

        // Base create command with image
        CreateContainerCmd cmd = dockerClient.createContainerCmd(cfg.getImage());

        // Optional Container Name
        if (cfg.getContainerName() != null && !cfg.getContainerName().isBlank()) {
            cmd.withName(instanceTracker.generateReusedName(cfg.getContainerName()));
        }

        List<String> envList = buildEnvList(cfg);
        if (!envList.isEmpty()) {
            cmd.withEnv(envList);
        }

        // Ports, volumes, restart, network live in HostConfig
        HostConfig hostConfig = HostConfig.newHostConfig();

        // Port mappings
        Ports portBindings = buildPortBindings(cfg);
        if (portBindings != null) {
            hostConfig.withPortBindings(portBindings);

            // Exposed ports also need to be set on the container itself
            List<ExposedPort> exposedPorts = buildExposedPorts(cfg);
            if (!exposedPorts.isEmpty()) {
                cmd.withExposedPorts(exposedPorts);
            }
        }

        // Volume bindings
        List<Bind> binds = buildBinds(cfg);
        if (!binds.isEmpty()) {
            hostConfig.withBinds(binds);
        }

        // Restart policy
        RestartPolicy restartPolicy = buildRestartPolicy(cfg.getRestartPolicy());
        if (restartPolicy != null) {
            hostConfig.withRestartPolicy(restartPolicy);
        }

        // Network mode (optional)
        if (cfg.getNetworkMode() != null && !cfg.getNetworkMode().isBlank()) {
            hostConfig.withNetworkMode(cfg.getNetworkMode());
        }

        cmd.withHostConfig(hostConfig);

        try {
            // Create
            CreateContainerResponse resp = cmd.exec();

            // Start
            dockerClient.startContainerCmd(resp.getId()).exec();

            // You might record this in a DockerContainerInstance table here
            return resp.getId();
        } catch (DockerException e) {
            // wrap or rethrow as your domain exception
            throw new RuntimeException("Failed to start container from config " + configId, e);
        }
    }

    private List<String> buildEnvList(DockerServiceConfig cfg) {
        Map<String, String> envMap = new LinkedHashMap<>();

        if (cfg.getEnvVars() != null) {
            for (DockerEnvVar ev : cfg.getEnvVars()) {
                if (ev.getName() == null || ev.getName().isBlank()) continue;
                if (ev.getValue() == null) continue; // or allow empty value
                envMap.put(ev.getName(), ev.getValue());
            }
        }

        return envMap.entrySet()
                .stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.toList());
    }

    private Ports buildPortBindings(DockerServiceConfig cfg) {
        if (cfg.getPorts() == null || cfg.getPorts().isEmpty()) {
            return null;
        }

        Ports ports = new Ports();

        for (DockerPortMapping pm : cfg.getPorts()) {
            if (pm.getContainerPort() == null) continue;

            String proto = (pm.getProtocol() == null || pm.getProtocol().isBlank())
                    ? "tcp"
                    : pm.getProtocol().toLowerCase(Locale.ROOT);

            ExposedPort exposedPort = new ExposedPort(proto, pm.getContainerPort());

            if (pm.getHostPort() != null) {
                // Bind to specific host port
                Ports.Binding binding = Ports.Binding.bindPort(pm.getHostPort());
                ports.bind(exposedPort, binding);
            } else {
                // Let Docker pick host port
                ports.bind(exposedPort, Ports.Binding.empty());
            }
        }

        return ports;
    }

    private List<ExposedPort> buildExposedPorts(DockerServiceConfig cfg) {
        if (cfg.getPorts() == null) return List.of();

        List<ExposedPort> exposedPorts = new ArrayList<>();
        for (DockerPortMapping pm : cfg.getPorts()) {
            if (pm.getContainerPort() == null) continue;

            String proto = (pm.getProtocol() == null || pm.getProtocol().isBlank())
                    ? "tcp"
                    : pm.getProtocol().toLowerCase(Locale.ROOT);

            exposedPorts.add(new ExposedPort(proto, pm.getContainerPort()));
        }
        return exposedPorts;
    }

    private List<Bind> buildBinds(DockerServiceConfig cfg) {
        if (cfg.getVolumes() == null || cfg.getVolumes().isEmpty()) {
            return List.of();
        }

        List<Bind> binds = new ArrayList<>();
        for (DockerVolumeMapping vm : cfg.getVolumes()) {
            if (vm.getHostPathOrVolume() == null ||
                    vm.getContainerPath() == null ||
                    vm.getContainerPath().isBlank()) {
                continue;
            }

            Volume volume = new Volume(vm.getContainerPath());

            String mode = (vm.getMode() == null || vm.getMode().isBlank())
                    ? "rw"
                    : vm.getMode().toLowerCase(Locale.ROOT);

            Bind bind = new Bind(vm.getHostPathOrVolume(), volume, Boolean.valueOf(mode));
            binds.add(bind);
        }
        return binds;
    }

    private RestartPolicy buildRestartPolicy(String policyStr) {
        if (policyStr == null || policyStr.isBlank()) {
            return null;
        }

        // simple formats:
        // "no"
        // "always"
        // "on-failure"
        // "on-failure:3"
        String p = policyStr.trim().toLowerCase(Locale.ROOT);

        if (p.equals("no")) {
            return RestartPolicy.noRestart();
        }
        if (p.equals("always")) {
            return RestartPolicy.alwaysRestart();
        }
        if (p.startsWith("on-failure")) {
            Integer maxRetries = null;
            int idx = p.indexOf(':');
            if (idx > 0 && idx < p.length() - 1) {
                try {
                    maxRetries = Integer.parseInt(p.substring(idx + 1));
                } catch (NumberFormatException ignored) {
                }
            }
            if (maxRetries != null) {
                return RestartPolicy.onFailureRestart(maxRetries);
            } else {
                return RestartPolicy.onFailureRestart(0);
            }
        }

        // Fallback; or return null / throw
        return RestartPolicy.parse(p);
    }
}
