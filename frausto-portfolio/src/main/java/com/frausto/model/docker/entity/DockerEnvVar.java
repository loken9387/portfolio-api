package com.frausto.model.docker.entity;

import jakarta.persistence.*;

import java.util.Objects;

@Entity
@Table(name = "docker_env_var")
public class DockerEnvVar {
    @Id
    @GeneratedValue
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    private DockerServiceConfig config;

    private String name;
    private String value;
    private boolean secret;    // true = value is a placeholder, real value from env/secret store

    public DockerEnvVar(Long id, DockerServiceConfig config, String name, String value, boolean secret) {
        this.id = id;
        this.config = config;
        this.name = name;
        this.value = value;
        this.secret = secret;
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public boolean isSecret() {
        return secret;
    }

    public void setSecret(boolean secret) {
        this.secret = secret;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DockerEnvVar that)) return false;
        return isSecret() == that.isSecret() && Objects.equals(getId(), that.getId()) && Objects.equals(getConfig(), that.getConfig()) && Objects.equals(getName(), that.getName()) && Objects.equals(getValue(), that.getValue());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId(), getConfig(), getName(), getValue(), isSecret());
    }
}