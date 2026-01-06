package com.frausto.config;

import com.frausto.service.util.InstanceTracker;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import jakarta.ws.rs.SeBootstrap;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Wires the docker java client into the application context. It builds a DefaultDockerClientConfig from the environment
 * (honoring Docker host/SSL settings), creates an ApacheDockerHttpClient with connection/time limits, and exposes a
 * DockerClient bean via DockerClientImpl.getInstance(...) so services can execute Docker operations with pooled HTTP
 * connections and sensible timeouts.
 */
@Configuration
public class DockerClientConfig {

    @Bean
    public DockerClient dockerClient() {
        DefaultDockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder().build();

        DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .sslConfig(config.getSSLConfig())
                .maxConnections(100)
                .connectionTimeout(Duration.ofSeconds(30))
                .responseTimeout(Duration.ofSeconds(30))
                .build();

        return DockerClientImpl.getInstance(config, httpClient);
    }

    @Bean
    public InstanceTracker instanceTracker() {
        return new InstanceTracker();
    }
}
