# Client Interface Control Document (ICD)

This document describes the client-facing interfaces exposed by the Portfolio API service, including REST endpoints, WebSocket flows, and ZeroMQ publications.

## REST endpoints

All REST endpoints are rooted at `/api` and return JSON unless otherwise noted.

### Docker configuration lifecycle

#### `GET /api/docker/configs`
- **Description:** List all stored Docker service configurations.
- **Response:** Array of `DockerServiceConfig` objects persisted by the service.

#### `GET /api/docker/configs/{configId}`
- **Description:** Retrieve a single Docker service configuration by id.
- **Response:** `DockerServiceConfig`.
- **Errors:** `404` if the configuration id does not exist.

#### `POST /api/docker/configs`
- **Description:** Create a new Docker service configuration.
- **Request body:**
  ```json
  {
    "name": "nginx",
    "containerName": "nginx",
    "description": "Reverse proxy",
    "image": "nginx:latest",
    "command": "",                 // optional override, whitespace-separated string
    "entrypoint": "",              // optional override, whitespace-separated string
    "restartPolicy": "always",     // examples: "no", "always", "on-failure" or "on-failure:3"
    "networkMode": "bridge",       // optional
    "networkName": "",             // optional, alternative network identifier
    "ports": [
      { "containerPort": 80, "hostPort": 8080, "protocol": "tcp" }
    ],
    "envVars": [
      { "name": "APP_ENV", "value": "prod", "secret": false }
    ],
    "volumes": [
      { "hostPathOrVolume": "/data", "containerPath": "/var/www", "mode": "rw" }
    ]
  }
  ```
- **Response:** Created `DockerServiceConfig` including generated id.

#### `POST /api/docker/configs/{configId}/start`
- **Description:** Start a container from a stored configuration. Generates an instance-specific name if `containerName` is provided and reused.
- **Response:** `{ "containerId": "..." }` with the created container id.
- **Side effects:** Begins periodic ZeroMQ status broadcasts while any container is running.

#### `DELETE /api/docker/configs/{configId}/containers?force=false`
- **Description:** Remove all containers created from the given configuration.
- **Query parameters:**
  - `force` (boolean, default `false`): Force removal of running containers.
- **Response:** `204 No Content` on success.

### Docker status

#### `GET /api/docker/status`
- **Description:** Snapshot of container statuses for all known configurations.
- **Response:** Array of `DockerContainerStatus` protobuf-derived objects serialized to JSON:
  ```json
  {
    "configId": 1,
    "configName": "nginx",
    "containerId": "<docker id>",
    "containerName": "nginx",
    "status": "running",           // Docker state string or "not_created"
    "running": true,
    "expectedRunning": true,
    "pid1Running": true,
    "attentionNeeded": false
  }
  ```
  Missing containers for a configuration are reported with `status: "not_created"` and `attentionNeeded: true` when a restart policy expects it to be running.

#### `POST /api/docker/status/broadcast`
- **Description:** Manually publish the current container statuses over ZeroMQ.
- **Response:** `DockerStatusEvent` containing the same `statuses` array and a `generatedAtEpochMs` timestamp.

### Terminal access

#### `POST /api/terminal/sessions`
- **Description:** Request an interactive shell session for a running container.
- **Request body:** `{ "containerId": "<docker id>", "cmd": "sh" }` (`cmd` optional to override the default shell).
- **Response:**
  ```json
  {
    "containerId": "<docker id>",
    "cmd": "sh",
    "websocketPath": "/ws/terminal?containerId=<docker id>&cmd=sh"
  }
  ```
- **Errors:** `400` when `containerId` is missing, `403` when authorization fails.

### WebSocket: `/ws/terminal`
- **Query parameters:**
  - `containerId` (required)
  - `cmd` (optional) – whitespace-separated command to run instead of default shell.
- **Behavior:** Upgrades to a bidirectional terminal session backed by the Docker exec API. Text or binary frames are forwarded as input. A JSON text message with shape `{"type": "resize", "cols": <int>, "rows": <int>}` resizes the TTY.
- **Close conditions:** Missing/unauthorized `containerId` closes with protocol error; server errors close with status 1011.

## ZeroMQ publications

### Docker status publisher
- **Socket type:** `PUB`.
- **Default bind address:** `tcp://*:5556` (configurable via `docker.status.pubEndpoint`).
- **Topic:** `docker.status` (first frame).
- **Payload:** Protobuf `DockerStatusEvent` serialized bytes (second frame). The message contains:
  - `statuses`: repeated `DockerContainerStatus` entries (fields listed in REST section).
  - `generated_at_epoch_ms`: server-side timestamp in milliseconds.
- **Frequency:**
  - Emitted every 5 seconds while any tracked container is running.
  - Emitted once on demand via `POST /api/docker/status/broadcast`.

### Proxy sockets
A background XPUB/XSUB proxy binds to `ipc:///zmq/xsub.sock` and `ipc:///zmq/xpub.sock` for in-process fan-out. External subscribers should connect directly to the configured publisher endpoint.

## Data types (summary)
- **DockerServiceConfig**: persisted entity containing the fields submitted to `/api/docker/configs` plus generated `id`.
- **DockerContainerStatus** (protobuf): fields `config_id`, `config_name`, `container_id`, `container_name`, `status`, `running`, `expected_running`, `pid1_running`, `attention_needed`.
- **DockerStatusEvent** (protobuf): fields `statuses` (array of `DockerContainerStatus`), `generated_at_epoch_ms`.
- **TerminalSessionDescriptor**: `{ containerId, cmd, websocketPath }` used to establish the terminal WebSocket.

## Notes
- REST and WebSocket endpoints are served by the Spring Boot application; ensure it can reach the Docker daemon and configured PostgreSQL instance.
- ZeroMQ consumers must subscribe to the `docker.status` topic and deserialize the payloads using the protobuf definitions in `src/main/proto/service/docker_service.proto`.
