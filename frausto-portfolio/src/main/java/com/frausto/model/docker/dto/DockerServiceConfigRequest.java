package com.frausto.model.docker.dto;

import java.util.List;

public class DockerServiceConfigRequest {
    private String name;
    private String containerName;
    private String description;
    private String image;
    private String command;
    private String entrypoint;
    private String restartPolicy;
    private String networkMode;
    private String networkName;
    private List<DockerPortMappingRequest> ports;
    private List<DockerEnvVarRequest> envVars;
    private List<DockerVolumeMappingRequest> volumes;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getContainerName() {
        return containerName;
    }

    public void setContainerName(String containerName) {
        this.containerName = containerName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }

    public String getCommand() {
        return command;
    }

    public void setCommand(String command) {
        this.command = command;
    }

    public String getEntrypoint() {
        return entrypoint;
    }

    public void setEntrypoint(String entrypoint) {
        this.entrypoint = entrypoint;
    }

    public String getRestartPolicy() {
        return restartPolicy;
    }

    public void setRestartPolicy(String restartPolicy) {
        this.restartPolicy = restartPolicy;
    }

    public String getNetworkMode() {
        return networkMode;
    }

    public void setNetworkMode(String networkMode) {
        this.networkMode = networkMode;
    }

    public String getNetworkName() {
        return networkName;
    }

    public void setNetworkName(String networkName) {
        this.networkName = networkName;
    }

    public List<DockerPortMappingRequest> getPorts() {
        return ports;
    }

    public void setPorts(List<DockerPortMappingRequest> ports) {
        this.ports = ports;
    }

    public List<DockerEnvVarRequest> getEnvVars() {
        return envVars;
    }

    public void setEnvVars(List<DockerEnvVarRequest> envVars) {
        this.envVars = envVars;
    }

    public List<DockerVolumeMappingRequest> getVolumes() {
        return volumes;
    }

    public void setVolumes(List<DockerVolumeMappingRequest> volumes) {
        this.volumes = volumes;
    }
}
