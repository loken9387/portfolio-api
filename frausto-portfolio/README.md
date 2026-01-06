# Portfolio API

## Local database
A PostgreSQL container is provided for persisting Docker service configurations. Start it with:

```bash
docker compose up -d postgres
```

The application expects the database at `jdbc:postgresql://localhost:5432/portfolio` with username `portfolio_user` and password `portfolio_pass` (see `src/main/resources/application.yml`). Data is stored in the `postgres_data` volume.

## Docker configuration endpoints
- `POST /api/docker/configs` — create a Docker service configuration.
- `POST /api/docker/configs/{configId}/start` — start a container from a saved configuration.
- `DELETE /api/docker/configs/{configId}/containers` — remove containers created from a configuration.
- `GET /api/docker/status` — fetch container status summaries.
- `POST /api/docker/status/broadcast` — manually broadcast statuses over ZeroMQ.

A default `nginx:latest` configuration is seeded automatically on startup if none exist. Status broadcasts automatically start after the first container is launched and continue until no containers remain running.
