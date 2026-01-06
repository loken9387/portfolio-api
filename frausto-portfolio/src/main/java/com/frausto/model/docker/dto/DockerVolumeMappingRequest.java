package com.frausto.model.docker.dto;

public class DockerVolumeMappingRequest {
    private String hostPathOrVolume;
    private String containerPath;
    private String mode;

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
}
