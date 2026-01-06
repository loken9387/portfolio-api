package com.frausto.model.docker.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.util.Objects;

@Entity
@Table(name = "docker_port_mapping")
public class DockerPortMapping {
    @Id
    @GeneratedValue
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnore
    private DockerServiceConfig config;

    private Integer containerPort;
    private Integer hostPort;
    private String protocol; // "tcp" or "udp"

    public DockerPortMapping() {
        // JPA
    }

    public DockerPortMapping(Long id, DockerServiceConfig config, Integer containerPort, Integer hostPort, String protocol) {
        this.id = id;
        this.config = config;
        this.containerPort = containerPort;
        this.hostPort = hostPort;
        this.protocol = protocol;
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

    public Integer getContainerPort() {
        return containerPort;
    }

    public void setContainerPort(Integer containerPort) {
        this.containerPort = containerPort;
    }

    public Integer getHostPort() {
        return hostPort;
    }

    public void setHostPort(Integer hostPort) {
        this.hostPort = hostPort;
    }

    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DockerPortMapping that)) return false;
        return Objects.equals(getId(), that.getId()) && Objects.equals(getConfig(), that.getConfig()) && Objects.equals(getContainerPort(), that.getContainerPort()) && Objects.equals(getHostPort(), that.getHostPort()) && Objects.equals(getProtocol(), that.getProtocol());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId(), getConfig(), getContainerPort(), getHostPort(), getProtocol());
    }
}