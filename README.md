# DeployForge

DeployForge is a deployment orchestration backend foundation for modeling deployment projects, services, and environments before later release, rollout, rollback, gate, lock, drift, audit, and recovery features are added.

## Stack

- Java 21
- Spring Boot 3.5.x
- Maven Wrapper
- Spring Web, JDBC, Actuator
- PostgreSQL, Flyway
- JUnit 5, Testcontainers
- Docker Compose

## Commands

```sh
cd apps/api
./mvnw test
./mvnw -DskipTests compile
cd ../..
docker compose -f infra/docker-compose/docker-compose.yml config
docker compose -f infra/docker-compose/docker-compose.yml up -d --build
docker compose -f infra/docker-compose/docker-compose.yml down -v
```

DEPLOY-001 is foundation only.
