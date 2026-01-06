package com.frausto.service.docker;

import com.frausto.model.docker.dto.DockerEnvVarRequest;
import com.frausto.model.docker.dto.DockerPortMappingRequest;
import com.frausto.model.docker.dto.DockerServiceConfigRequest;
import com.frausto.model.docker.dto.DockerVolumeMappingRequest;
import com.frausto.model.docker.entity.DockerEnvVar;
import com.frausto.model.docker.entity.DockerPortMapping;
import com.frausto.model.docker.entity.DockerServiceConfig;
import com.frausto.model.docker.entity.DockerVolumeMapping;
import com.frausto.repository.DockerRepository;
import com.frausto.service.util.InstanceTracker;
import com.frausto.proto.service.DockerContainerStatus;
import com.frausto.proto.service.DockerStatusEvent;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.command.ListContainersCmd;
import com.github.dockerjava.api.exception.DockerException;
import com.github.dockerjava.api.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

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

    /* ZeroMQ publisher for broadcasting status updates */
    private final DockerStatusPublisher statusPublisher;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> statusBroadcastTask;

    public DockerService(DockerClient dockerClient, InstanceTracker instanceTracker, DockerRepository dockerRepository,
                         DockerStatusPublisher statusPublisher) {
        this.dockerClient = dockerClient;
        this.instanceTracker = instanceTracker;
        this.dockerRepo = dockerRepository;
        this.statusPublisher = statusPublisher;
    }

    public List<DockerServiceConfig> getConfigs() {
        return dockerRepo.findAll();
    }

    public DockerServiceConfig getConfig(Long id) {
        return dockerRepo.findById(id).orElseThrow(() -> new IllegalArgumentException(
                "No DockerServiceConfig with id " + id
        ));
    }

    @Transactional
    public DockerServiceConfig createConfig(DockerServiceConfigRequest request) {
        DockerServiceConfig config = new DockerServiceConfig();
        config.setName(request.getName());
        config.setContainerName(request.getContainerName());
        config.setDescription(request.getDescription());
        config.setImage(request.getImage());
        config.setCommand(request.getCommand());
        config.setEntrypoint(request.getEntrypoint());
        config.setRestartPolicy(request.getRestartPolicy());
        config.setNetworkMode(request.getNetworkMode());
        config.setNetworkName(request.getNetworkName());

        addPorts(config, request.getPorts());
        addEnvVars(config, request.getEnvVars());
        addVolumeMappings(config, request.getVolumes());

        return dockerRepo.save(config);
    }

    public String startContainer(Long configId) {

        DockerServiceConfig cfg = dockerRepo.findById(configId).orElseThrow(() -> new IllegalArgumentException(
                "No DockerServiceConfig with id " + configId
        ));

        // Base create command with image
        CreateContainerCmd cmd = dockerClient.createContainerCmd(cfg.getImage());

        String effectiveName = null;
        if (cfg.getContainerName() != null && !cfg.getContainerName().isBlank()) {
            effectiveName = instanceTracker.generateReusedName(cfg.getContainerName());
            cmd.withName(effectiveName);
            cmd.withHostName(effectiveName);
        }

        // Container labels help us correlate configs to running containers
        Map<String, String> labels = new HashMap<>();
        labels.put("portfolio.config.id", String.valueOf(cfg.getId()));
        labels.put("portfolio.config.name", cfg.getName());
        cmd.withLabels(labels);

        // Entrypoint/command overrides
        if (cfg.getEntrypoint() != null && !cfg.getEntrypoint().isBlank()) {
            cmd.withEntrypoint(splitArgs(cfg.getEntrypoint()));
        }
        if (cfg.getCommand() != null && !cfg.getCommand().isBlank()) {
            cmd.withCmd(splitArgs(cfg.getCommand()));
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
        } else if (cfg.getNetworkName() != null && !cfg.getNetworkName().isBlank()) {
            hostConfig.withNetworkMode(cfg.getNetworkName().trim());
        }

        cmd.withHostConfig(hostConfig);

        try {
            // Create
            CreateContainerResponse resp = cmd.exec();

            // Start
            dockerClient.startContainerCmd(resp.getId()).exec();

            ensureStatusBroadcasting();

            // You might record this in a DockerContainerInstance table here
            return resp.getId();
        } catch (DockerException e) {
            // wrap or rethrow as your domain exception
            throw new RuntimeException("Failed to start container from config " + configId, e);
        }
    }

    public void removeContainersForConfig(Long configId, boolean force) {
        List<InspectContainerResponse> containers = findContainersByConfig(configId, true);
        for (InspectContainerResponse container : containers) {
            String containerId = container.getId();
            try {
                dockerClient.removeContainerCmd(containerId)
                        .withForce(force)
                        .withRemoveVolumes(true)
                        .exec();
                log.info("Removed container {} for config {}", containerId, configId);
            } catch (DockerException e) {
                throw new RuntimeException("Failed to remove container " + containerId + " for config " + configId, e);
            }
        }
    }

    public List<DockerContainerStatus> getContainerStatuses() {
        Map<Long, DockerServiceConfig> configsById = dockerRepo.findAll()
                .stream()
                .collect(Collectors.toMap(DockerServiceConfig::getId, c -> c));

        List<InspectContainerResponse> inspectedContainers = findContainersByConfig(null, true);

        Map<Long, List<InspectContainerResponse>> containersByConfig = inspectedContainers.stream()
                .collect(Collectors.groupingBy(c -> Long.parseLong(c.getConfig().getLabels().get("portfolio.config.id"))));

        List<DockerContainerStatus> statuses = new ArrayList<>();

        for (InspectContainerResponse container : inspectedContainers) {
            Long cfgId = Long.parseLong(container.getConfig().getLabels().get("portfolio.config.id"));
            DockerServiceConfig cfg = configsById.get(cfgId);
            statuses.add(buildStatus(cfg, container));
        }

        for (Map.Entry<Long, DockerServiceConfig> entry : configsById.entrySet()) {
            Long cfgId = entry.getKey();
            if (!containersByConfig.containsKey(cfgId)) {
                statuses.add(buildMissingStatus(entry.getValue()));
            }
        }

        return statuses;
    }

    public DockerStatusEvent broadcastContainerStatuses() {
        List<DockerContainerStatus> statuses = getContainerStatuses();
        return statusPublisher.publishStatuses(statuses);
    }

    private void ensureStatusBroadcasting() {
        synchronized (this) {
            if (statusBroadcastTask != null && !statusBroadcastTask.isDone()) {
                return;
            }

            statusBroadcastTask = scheduler.scheduleAtFixedRate(() -> {
                try {
                    List<DockerContainerStatus> statuses = getContainerStatuses();
                    statusPublisher.publishStatuses(statuses);

                    boolean anyRunning = statuses.stream().anyMatch(DockerContainerStatus::getRunning);
                    if (!anyRunning) {
                        stopStatusBroadcasting();
                    }
                } catch (Exception e) {
                    log.error("Failed to publish Docker status update", e);
                }
            }, 0, 5, TimeUnit.SECONDS);
        }
    }

    private void stopStatusBroadcasting() {
        synchronized (this) {
            if (statusBroadcastTask != null) {
                statusBroadcastTask.cancel(false);
                statusBroadcastTask = null;
            }
        }
    }

    private DockerContainerStatus buildStatus(DockerServiceConfig cfg, InspectContainerResponse container) {
        InspectContainerResponse.ContainerState state = container.getState();
        boolean running = Boolean.TRUE.equals(state.getRunning());
        boolean expectedRunning = isExpectedToRun(cfg);
        boolean pid1Running = state.getPid() != null && state.getPid() > 1;

        DockerContainerStatus.Builder builder = DockerContainerStatus.newBuilder()
                .setRunning(running)
                .setExpectedRunning(expectedRunning)
                .setPid1Running(pid1Running)
                .setAttentionNeeded(!running && expectedRunning);

        if (cfg != null) {
            builder.setConfigId(cfg.getId())
                    .setConfigName(cfg.getName());
        }

        if (container.getId() != null) {
            builder.setContainerId(container.getId());
        }
        if (container.getName() != null) {
            builder.setContainerName(container.getName());
        }
        if (state.getStatus() != null) {
            builder.setStatus(state.getStatus());
        }

        return builder.build();
    }

    private DockerContainerStatus buildMissingStatus(DockerServiceConfig cfg) {
        boolean expectedRunning = isExpectedToRun(cfg);
        return DockerContainerStatus.newBuilder()
                .setConfigId(cfg.getId())
                .setConfigName(cfg.getName())
                .setStatus("not_created")
                .setRunning(false)
                .setExpectedRunning(expectedRunning)
                .setPid1Running(false)
                .setAttentionNeeded(expectedRunning)
                .build();
    }

    private boolean isExpectedToRun(DockerServiceConfig cfg) {
        return cfg.getRestartPolicy() != null && !cfg.getRestartPolicy().isBlank() &&
                !cfg.getRestartPolicy().equalsIgnoreCase("no");
    }

    private List<InspectContainerResponse> findContainersByConfig(Long configId, boolean includeStopped) {
        ListContainersCmd listCmd = dockerClient.listContainersCmd().withShowAll(includeStopped);
        if (configId != null) {
            listCmd.withLabelFilter(Map.of("portfolio.config.id", String.valueOf(configId)));
        } else {
            listCmd.withLabelFilter(Map.of("portfolio.config.id", ""));
        }

        List<Container> containers = listCmd.exec();
        List<InspectContainerResponse> inspected = new ArrayList<>();
        for (Container container : containers) {
            inspected.add(dockerClient.inspectContainerCmd(container.getId()).exec());
        }
        return inspected;
    }

    private List<String> splitArgs(String raw) {
        return Arrays.stream(raw.trim().split("\\s+"))
                .filter(s -> !s.isBlank())
                .collect(Collectors.toList());
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

            AccessMode accessMode = mode.equals("ro") ? AccessMode.ro : AccessMode.rw;
            Bind bind = new Bind(vm.getHostPathOrVolume(), volume, accessMode);
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

    private void addPorts(DockerServiceConfig config, List<DockerPortMappingRequest> requests) {
        if (requests == null) {
            return;
        }

        for (DockerPortMappingRequest request : requests) {
            DockerPortMapping mapping = new DockerPortMapping();
            mapping.setConfig(config);
            mapping.setContainerPort(request.getContainerPort());
            mapping.setHostPort(request.getHostPort());
            mapping.setProtocol(request.getProtocol());
            config.getPorts().add(mapping);
        }
    }

    private void addEnvVars(DockerServiceConfig config, List<DockerEnvVarRequest> requests) {
        if (requests == null) {
            return;
        }

        for (DockerEnvVarRequest request : requests) {
            DockerEnvVar envVar = new DockerEnvVar();
            envVar.setConfig(config);
            envVar.setName(request.getName());
            envVar.setValue(request.getValue());
            envVar.setSecret(request.isSecret());
            config.getEnvVars().add(envVar);
        }
    }

    private void addVolumeMappings(DockerServiceConfig config, List<DockerVolumeMappingRequest> requests) {
        if (requests == null) {
            return;
        }

        for (DockerVolumeMappingRequest request : requests) {
            DockerVolumeMapping volumeMapping = new DockerVolumeMapping();
            volumeMapping.setConfig(config);
            volumeMapping.setHostPathOrVolume(request.getHostPathOrVolume());
            volumeMapping.setContainerPath(request.getContainerPath());
            volumeMapping.setMode(request.getMode());
            config.getVolumes().add(volumeMapping);
        }
    }
}
