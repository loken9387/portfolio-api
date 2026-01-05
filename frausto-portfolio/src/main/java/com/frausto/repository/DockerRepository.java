package com.frausto.repository;

import com.frausto.model.docker.entity.DockerServiceConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository

public interface DockerRepository extends JpaRepository<DockerServiceConfig, Long> {
}
