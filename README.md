# portfolio-api

This repository hosts the Portfolio API backend plus the supporting infrastructure used to run it locally. The main Spring Boot service lives in `frausto-portfolio/`, which includes the API implementation, Docker Compose setup, and service documentation.

## What it does
- Manages Docker service configurations and launches containers from those saved configs via REST endpoints.
- Broadcasts container status updates over ZeroMQ after containers are started.
- Seeds a default `nginx:latest` configuration if none exist on startup.

## How it runs locally
- Provides a PostgreSQL container via Docker Compose for persisting Docker service configurations.
- Expects the database at `jdbc:postgresql://localhost:5432/portfolio` with `portfolio_user` / `portfolio_pass`.

## Where to look next
Head into `frausto-portfolio/README.md` for the full API endpoint list and operational details.
