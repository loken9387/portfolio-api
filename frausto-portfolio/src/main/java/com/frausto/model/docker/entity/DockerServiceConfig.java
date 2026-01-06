package com.frausto.model.docker.entity;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "docker_service_config")
public class DockerServiceConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;            // "portfolio-backend"
    private String containerName;     // "Portfolio Backend"
    private String description;

    private String image;           // "ghcr.io/frausto/portfolio-backend:1.0.0"

    private String command;         // optional
    private String entrypoint;      // optional

    private String restartPolicy;   // e.g. "always"

    private String networkMode;     // "bridge", "host", or null
    private String networkName;     // custom network if any

    @OneToMany(mappedBy = "config", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DockerPortMapping> ports = new ArrayList<>();

    @OneToMany(mappedBy = "config", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DockerEnvVar> envVars = new ArrayList<>();

    @OneToMany(mappedBy = "config", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DockerVolumeMapping> volumes = new ArrayList<>();

    public DockerServiceConfig() {
        // JPA
    }

    public DockerServiceConfig(Long id, String name, String containerName, String description, String image,
                               String command, String entrypoint, String restartPolicy, String networkMode,
                               String networkName, List<DockerPortMapping> ports, List<DockerEnvVar> envVars,
                               List<DockerVolumeMapping> volumes) {
        this.id = id;
        this.name = name;
        this.containerName = containerName;
        this.description = description;
        this.image = image;
        this.command = command;
        this.entrypoint = entrypoint;
        this.restartPolicy = restartPolicy;
        this.networkMode = networkMode;
        this.networkName = networkName;
        this.ports = ports;
        this.envVars = envVars;
        this.volumes = volumes;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getContainerName() {
        return containerName;
    }

    public void setContainerName(String displayName) {
        this.containerName = displayName;
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

    public List<DockerPortMapping> getPorts() {
        return ports;
    }

    public void setPorts(List<DockerPortMapping> ports) {
        this.ports = ports;
    }

    public List<DockerEnvVar> getEnvVars() {
        return envVars;
    }

    public void setEnvVars(List<DockerEnvVar> envVars) {
        this.envVars = envVars;
    }

    public List<DockerVolumeMapping> getVolumes() {
        return volumes;
    }

    public void setVolumes(List<DockerVolumeMapping> volumes) {
        this.volumes = volumes;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DockerServiceConfig that)) return false;
        return Objects.equals(getId(), that.getId()) && Objects.equals(getName(), that.getName()) &&
                Objects.equals(getContainerName(), that.getContainerName()) && Objects.equals(getDescription(), that.getDescription()) &&
                Objects.equals(getImage(), that.getImage()) && Objects.equals(getCommand(), that.getCommand()) &&
                Objects.equals(getEntrypoint(), that.getEntrypoint()) && Objects.equals(getRestartPolicy(), that.getRestartPolicy()) &&
                Objects.equals(getNetworkMode(), that.getNetworkMode()) && Objects.equals(getNetworkName(), that.getNetworkName()) &&
                Objects.equals(getPorts(), that.getPorts()) && Objects.equals(getEnvVars(), that.getEnvVars()) &&
                Objects.equals(getVolumes(), that.getVolumes());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId(), getName(), getContainerName(), getDescription(), getImage(), getCommand(),
                getEntrypoint(), getRestartPolicy(), getNetworkMode(), getNetworkName(), getPorts(), getEnvVars(),
                getVolumes());
    }
}
