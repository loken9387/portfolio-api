package com.frausto.model.docker.entity;

import jakarta.persistence.*;

import java.util.Objects;

@Entity
@Table(name = "docker_volume_mapping")
public class DockerVolumeMapping {
    @Id
    @GeneratedValue
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    private DockerServiceConfig config;

    private String hostPathOrVolume;
    private String containerPath;
    private String mode; // "ro"/"rw"

    public DockerVolumeMapping() {
        // JPA
    }

    public DockerVolumeMapping(Long id, DockerServiceConfig config, String hostPathOrVolume, String containerPath, String mode) {
        this.id = id;
        this.config = config;
        this.hostPathOrVolume = hostPathOrVolume;
        this.containerPath = containerPath;
        this.mode = mode;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public DockerServiceConfig getConfig() {
        return config;
    }

    public void setConfig(DockerServiceConfig config) {
        this.config = config;
    }

    public String getHostPathOrVolume() {
        return hostPathOrVolume;
    }

    public void setHostPathOrVolume(String hostPathOrVolume) {
        this.hostPathOrVolume = hostPathOrVolume;
    }

    public String getContainerPath() {
        return containerPath;
    }

    public void setContainerPath(String containerPath) {
        this.containerPath = containerPath;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DockerVolumeMapping that)) return false;
        return Objects.equals(getId(), that.getId()) && Objects.equals(getConfig(), that.getConfig()) && Objects.equals(getHostPathOrVolume(), that.getHostPathOrVolume()) && Objects.equals(getContainerPath(), that.getContainerPath()) && Objects.equals(getMode(), that.getMode());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId(), getConfig(), getHostPathOrVolume(), getContainerPath(), getMode());
    }
}