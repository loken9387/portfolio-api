# Requirements and Product Definition (RPD)

## 1. Overview
The Portfolio API provides a backend service for persisting Docker service configurations, launching containers from saved configurations, tracking container health/status, and enabling terminal access to running containers. It exposes REST endpoints, a WebSocket terminal, and ZeroMQ status broadcasts for integrations and automation workflows.

## 2. Goals
- Persist and manage Docker service configurations in a PostgreSQL-backed store.
- Start and stop containers based on saved configurations.
- Provide near-real-time container status snapshots and broadcasts.
- Enable terminal access to running containers over WebSocket.
- Provide clear, stable client-facing interfaces documented in the ICD.

## 3. In-Scope
- REST endpoints under `/api` for configuration lifecycle, status, and terminal session initiation.
- WebSocket endpoint for terminal I/O.
- ZeroMQ publisher for status broadcasts.
- Local PostgreSQL persistence for Docker configuration entities.
- Docker daemon integration for container lifecycle management.

## 4. Out-of-Scope
- UI/Frontend user interfaces (this is an API-only service).
- Multi-tenant access control beyond basic authorization checks on terminal sessions.
- Container image registry management.
- Kubernetes or orchestration beyond single Docker host.

## 5. Target Users / Personas
- **Platform engineers** who need a lightweight API to manage Docker services.
- **DevOps automation scripts** that store and start Docker configurations.
- **Monitoring systems** that subscribe to container status broadcasts.

## 6. Functional Requirements

### FR-1: Docker configuration persistence
- The system shall allow creation, retrieval, and listing of Docker service configurations.
- The system shall store configuration fields such as name, containerName, description, image, command, entrypoint, restart policy, network mode/name, ports, env vars, and volumes.

### FR-2: Start container from configuration
- The system shall start a container from a stored configuration by ID.
- The system shall return the created container ID.
- The system shall generate an instance-specific name when reusing containerName.

### FR-3: Remove containers for a configuration
- The system shall remove all containers created for a configuration ID.
- The system shall support a `force` flag to remove running containers.

### FR-4: Status snapshot
- The system shall return a snapshot of container status for all known configurations.
- The system shall include attention flags for missing containers when restart policies expect them to run.

### FR-5: Status broadcast
- The system shall publish status events over ZeroMQ while any tracked container is running.
- The system shall allow manual broadcast of the current status set.

### FR-6: Terminal session initiation
- The system shall provide a REST endpoint to request a terminal session for a container.
- The response shall include a WebSocket path for interactive terminal access.

### FR-7: Terminal WebSocket
- The system shall support bidirectional terminal I/O for a running container.
- The system shall accept resize messages to adjust TTY dimensions.

## 7. Non-Functional Requirements

### NFR-1: Availability
- The API should be available for local or single-host deployments where Docker and PostgreSQL are reachable.

### NFR-2: Performance
- Status snapshot responses should be returned within a reasonable time for typical local deployments (tens of containers).

### NFR-3: Reliability
- Status broadcasts should resume automatically when containers are running.
- The system should seed a default configuration if none exist on startup.

### NFR-4: Security
- Terminal session creation should be protected by authorization checks (403 on failure).
- WebSocket terminal should close on missing or unauthorized container ID.

### NFR-5: Observability
- System should log Docker lifecycle operations and broadcast activity to assist debugging.

## 8. External Interfaces

### REST APIs
- `GET /api/docker/configs`
- `GET /api/docker/configs/{configId}`
- `POST /api/docker/configs`
- `POST /api/docker/configs/{configId}/start`
- `DELETE /api/docker/configs/{configId}/containers?force=false`
- `GET /api/docker/status`
- `POST /api/docker/status/broadcast`
- `POST /api/terminal/sessions`

### WebSocket
- `GET /ws/terminal?containerId=<id>&cmd=<cmd>` for interactive terminal access.

### ZeroMQ
- Publisher on `tcp://*:5556` (configurable) using topic `docker.status`.

## 9. Data Model Summary
- **DockerServiceConfig**: persisted configuration used to launch containers.
- **DockerContainerStatus**: status report for a container or expected container.
- **DockerStatusEvent**: wrapper for status array + timestamp.
- **TerminalSessionDescriptor**: response data for terminal session creation.

## 10. Dependencies
- Docker daemon available to the service.
- PostgreSQL database (default: `jdbc:postgresql://localhost:5432/portfolio`).
- ZeroMQ libraries and protobuf definitions for status broadcasts.

## 11. Acceptance Criteria
- A user can create a Docker configuration and retrieve it by ID.
- A user can start a container from a stored configuration and receive a container ID.
- Status snapshot includes expected/running flags and attention indicators for missing containers.
- Status broadcasts are emitted every 5 seconds while any container is running.
- Terminal sessions can be established via WebSocket for a running container.

## 12. Risks and Mitigations
- **Docker daemon unavailability**: return clear error responses; document requirement.
- **Database connectivity issues**: fail fast on startup and log connection errors.
- **Security of terminal access**: ensure authorization checks remain enforced.
