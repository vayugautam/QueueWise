# QueueWise Docker and CI Configuration Guide

**Files covered:** `.env.example`, `.gitignore`, `backend/Dockerfile`, `frontend/Dockerfile`, `docker-compose.yml`, `.github/workflows/ci.yml`

## 1. Important Repository Prerequisite

The current repository contains the documentation and configuration scaffold but does not yet contain `backend/pom.xml`, `backend/src`, `frontend/package.json`, or frontend source files. Therefore the Docker build contexts and CI workflow are intentionally prepared for the planned application layout, but they cannot build the application until those source trees exist.

Once the backend and frontend are implemented, the expected local command is:

```bash
copy .env.example .env
docker compose up --build
```

The Compose file fails fast when required secrets are missing instead of silently using hardcoded credentials.

## 2. `.env.example` Explanation

- The comment explains that the file is a template and the real `.env` must not be committed.
- `POSTGRES_DB` names the local database.
- `POSTGRES_USER` names the local database user.
- `POSTGRES_PASSWORD` is a placeholder that must be replaced locally.
- `SPRING_PROFILES_ACTIVE` selects the Spring profile.
- `JWT_ISSUER` and `JWT_AUDIENCE` identify the local JWT contract.
- `JWT_SECRET` is a placeholder for a local signing secret; production injects a deployment secret.
- `CORS_ALLOWED_ORIGINS` allows the local Vite browser origin.
- `VITE_API_BASE_URL` is public frontend configuration for REST.
- `VITE_WEBSOCKET_URL` is public frontend configuration for WebSocket.

The template contains no usable production secret. `.gitignore` keeps the real `.env` out of Git while allowing `.env.example` to be committed.

## 3. `backend/Dockerfile`, Line by Line

```dockerfile
FROM maven:3.9-eclipse-temurin-25 AS build
```

Uses a Maven image with Java 25 as a named build stage. Maven and the JDK are needed to compile and package Spring Boot, but they are not needed in the final runtime image.

```dockerfile
WORKDIR /workspace
```

Sets the build working directory inside the image.

```dockerfile
COPY pom.xml .
```

Copies Maven's dependency manifest first. Docker can reuse the dependency layers when application source changes, assuming the project later adds a dependency cache strategy.

```dockerfile
COPY src ./src
```

Copies Java source and resources into the build stage. The expected source tree is `backend/src`.

```dockerfile
RUN mvn --batch-mode --no-transfer-progress -DskipTests package
```

Runs Maven non-interactively and creates the application jar. Tests are run by CI before the image build; a production pipeline may choose to run them again during the image build if desired.

```dockerfile
FROM eclipse-temurin:25-jre-jammy
```

Starts a smaller runtime stage containing a Java 25 JRE rather than the full Maven/JDK build image.

```dockerfile
RUN apt-get update \
    && apt-get install --no-install-recommends --yes curl \
    && useradd --system --uid 10001 --create-home queuewise \
    && rm -rf /var/lib/apt/lists/*
```

Updates package metadata, installs only `curl` for the container health check, creates a non-root runtime user, and removes package lists to reduce image size. The application does not run as root.

```dockerfile
WORKDIR /app
```

Sets the runtime working directory.

```dockerfile
COPY --from=build /workspace/target/*.jar /app/app.jar
```

Copies the packaged jar from the build stage into the runtime image. No Maven cache or source code is copied into the final image.

```dockerfile
RUN chown -R queuewise:queuewise /app
USER queuewise
```

Gives the non-root user ownership of the application directory and runs subsequent container processes as that user.

```dockerfile
EXPOSE 8080
```

Documents the port the Spring Boot application listens on. It does not publish the port by itself; Compose publishes it.

```dockerfile
HEALTHCHECK --interval=10s --timeout=5s --start-period=30s --retries=12 \
    CMD curl --fail --silent http://localhost:8080/actuator/health/readiness || exit 1
```

Asks Docker to check Spring Boot readiness every 10 seconds, allows 30 seconds for startup, waits at most 5 seconds per check, and marks the container unhealthy after 12 failures. The application must expose a safe readiness endpoint.

```dockerfile
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

Starts the Spring Boot jar as the container's main process. The JSON array form passes arguments without a shell.

## 4. `frontend/Dockerfile`, Line by Line

```dockerfile
FROM node:22-alpine
```

Uses a small Node.js 22 image for the Vite development container.

```dockerfile
WORKDIR /app
```

Sets the frontend working directory.

```dockerfile
COPY package*.json ./
```

Copies `package.json` and the lock file before source code so dependency installation can be cached.

```dockerfile
RUN npm ci
```

Installs exactly the locked dependency set. CI-style `npm ci` requires a committed lock file.

```dockerfile
COPY . .
```

Copies frontend source and Vite configuration into the image.

```dockerfile
ENV HOST=0.0.0.0
```

Makes Vite listen on all container interfaces rather than only loopback, allowing the host browser to reach it.

```dockerfile
EXPOSE 5173
```

Documents the Vite development port.

```dockerfile
CMD ["npm", "run", "dev", "--", "--host", "0.0.0.0"]
```

Starts the Vite development server and explicitly binds it to all interfaces.

## 5. `docker-compose.yml`, Line by Line

```yaml
services:
```

Begins the set of local application services.

```yaml
  postgres:
    image: postgres:16-alpine
```

Runs PostgreSQL 16 on a compact Alpine image. The actual project should pin and periodically review the approved image version.

```yaml
    environment:
      POSTGRES_DB: ${POSTGRES_DB:-queuewise}
      POSTGRES_USER: ${POSTGRES_USER:-queuewise}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:?POSTGRES_PASSWORD must be set in .env}
```

Passes database configuration from the shell or `.env`. Database name/user have local defaults; the password has no default and Compose stops with a clear message if it is missing.

```yaml
    volumes:
      - queuewise-postgres-data:/var/lib/postgresql/data
```

Mounts a named persistent volume at PostgreSQL's data directory. Container recreation does not delete local database data unless the volume is deliberately removed.

```yaml
    networks:
      - queuewise-network
```

Connects PostgreSQL only to the QueueWise bridge network. Backend uses the service name `postgres`, not `localhost`, for container-to-container access.

```yaml
    ports:
      - "${POSTGRES_PORT:-5432}:5432"
```

Optionally publishes PostgreSQL to the development host. Production should normally keep the database private and use managed PostgreSQL/network controls.

```yaml
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U $${POSTGRES_USER} -d $${POSTGRES_DB}"]
      interval: 5s
      timeout: 5s
      retries: 12
      start_period: 10s
```

Checks database readiness using PostgreSQL's `pg_isready`. The doubled dollar signs defer variable expansion to the container so Compose does not consume the values prematurely. The timing fields define check frequency, timeout, retry count, and startup grace period.

```yaml
    restart: unless-stopped
```

Restarts the local database after an unexpected container failure, but not after an intentional stop.

```yaml
  backend:
    build:
      context: ./backend
      dockerfile: Dockerfile
```

Builds the Spring Boot image from `backend/Dockerfile`. The context must contain the planned Maven project.

```yaml
    environment:
      SPRING_PROFILES_ACTIVE: ${SPRING_PROFILES_ACTIVE:-local}
      DATABASE_URL: jdbc:postgresql://postgres:5432/${POSTGRES_DB:-queuewise}
      DATABASE_USERNAME: ${POSTGRES_USER:-queuewise}
      DATABASE_PASSWORD: ${POSTGRES_PASSWORD:?POSTGRES_PASSWORD must be set in .env}
      JWT_ISSUER: ${JWT_ISSUER:-queuewise-api}
      JWT_AUDIENCE: ${JWT_AUDIENCE:-queuewise-web}
      JWT_SECRET: ${JWT_SECRET:?JWT_SECRET must be set in .env}
      CORS_ALLOWED_ORIGINS: ${CORS_ALLOWED_ORIGINS:-http://localhost:5173}
```

Passes Spring profile, container DNS database URL, database credentials, JWT metadata/signing secret, and CORS origin. Required secrets have no fallback and are never embedded in YAML.

```yaml
    depends_on:
      postgres:
        condition: service_healthy
```

Starts the backend only after PostgreSQL's health check passes. The backend still needs its own connection retry/migration behavior because health order is not a substitute for application resilience.

```yaml
    networks:
      - queuewise-network
    ports:
      - "${BACKEND_PORT:-8080}:8080"
```

Places the backend on the isolated application network and publishes HTTP/WebSocket port 8080 to the local host.

```yaml
    healthcheck:
      test: ["CMD-SHELL", "curl --fail --silent http://localhost:8080/actuator/health/readiness || exit 1"]
      interval: 10s
      timeout: 5s
      retries: 12
      start_period: 30s
```

Uses the backend readiness endpoint so Compose knows when the application and database/migrations are ready. The timing values allow Spring Boot startup time.

```yaml
    restart: unless-stopped
```

Restarts an unexpected backend failure during local operation.

```yaml
  frontend:
    build:
      context: ./frontend
      dockerfile: Dockerfile
```

Builds the Vite development container from the planned frontend project.

```yaml
    environment:
      VITE_API_BASE_URL: ${VITE_API_BASE_URL:-http://localhost:8080/api/v1}
      VITE_WEBSOCKET_URL: ${VITE_WEBSOCKET_URL:-ws://localhost:8080/ws}
```

Passes public browser URLs. These are not secrets and are intentionally reachable by frontend code.

```yaml
    volumes:
      - ./frontend:/app
      - frontend-node-modules:/app/node_modules
```

Mounts source for live development while keeping container-installed dependencies in a named volume so the host does not overwrite `node_modules`.

```yaml
    depends_on:
      backend:
        condition: service_healthy
```

Starts the development server after backend readiness. This is convenient for a demo; frontend code should still handle temporary backend failure.

```yaml
    networks:
      - queuewise-network
    ports:
      - "${FRONTEND_PORT:-5173}:5173"
```

Connects the frontend to the isolated network and publishes Vite to the host browser.

```yaml
    healthcheck:
      test: ["CMD", "node", "-e", "fetch('http://localhost:5173').then(r => process.exit(r.ok ? 0 : 1)).catch(() => process.exit(1))"]
      interval: 10s
      timeout: 5s
      retries: 12
      start_period: 15s
```

Uses Node's built-in fetch to verify that Vite serves a successful response.

```yaml
volumes:
  queuewise-postgres-data:
    name: queuewise-postgres-data
  frontend-node-modules:
    name: queuewise-frontend-node-modules
```

Declares named volumes for persistent PostgreSQL data and reusable frontend dependencies.

```yaml
networks:
  queuewise-network:
    name: queuewise-network
    driver: bridge
```

Creates a dedicated bridge network so the three services communicate through named service DNS without sharing the default Compose network.

## 6. GitHub Actions Workflow Explanation

### Workflow trigger and permissions

```yaml
name: QueueWise CI
```

Names the workflow in the Actions interface.

```yaml
on:
  pull_request:
  push:
    branches: [main]
```

Runs on every pull request and on pushes to `main`. It does not deploy.

```yaml
permissions:
  contents: read
```

Grants the minimum repository permission needed to checkout source.

### Backend job

`runs-on: ubuntu-latest` selects the GitHub-hosted Linux runner.

The `postgres` service container supplies an isolated test database. Its environment creates a test-only database/user/password, and its health options make the runner wait for `pg_isready`.

`defaults.run.working-directory: backend` makes Maven commands execute in the backend project directory.

```yaml
- uses: actions/checkout@v4
```

Downloads repository source for the job.

```yaml
- uses: actions/setup-java@v4
  with:
    distribution: temurin
    java-version: '25'
    cache: maven
```

Installs Java 25 from Eclipse Temurin and enables Maven dependency caching. The cache speeds CI but does not change build correctness.

The secret-generation step runs `openssl rand` on the runner and exposes a per-job JWT test value through the step output. It is not a production secret and is not committed or printed.

The backend test step sets the test profile and PostgreSQL/JWT test configuration, then runs:

```bash
mvn --batch-mode --no-transfer-progress test
```

`--batch-mode` prevents interactive prompts; `--no-transfer-progress` keeps logs concise; `test` compiles and runs JUnit/Spring tests.

The backend build step runs:

```bash
mvn --batch-mode --no-transfer-progress -DskipTests package
```

It verifies the application can be packaged after tests have passed. Docker image publishing/deployment is intentionally absent.

### Frontend job

`setup-node` installs Node 22 and caches npm dependencies using `frontend/package-lock.json`. `npm ci` installs the exact lockfile dependency graph. `npm run build` verifies the Vite production bundle with public test URLs.

### Failure report job

The `report` job depends on backend and frontend jobs and uses `if: failure()` so it runs only when a required job fails. It appends a concise message to `GITHUB_STEP_SUMMARY`; the failed job logs remain the source of detailed diagnostics.

No deployment step, registry login, cloud credential, or production secret is present in this workflow.

## 7. Local Startup Checklist

After the application source trees exist:

1. Copy `.env.example` to `.env` and replace local placeholders.
2. Confirm backend exposes `/actuator/health/readiness`.
3. Confirm backend uses the Compose variables for PostgreSQL/JWT/CORS.
4. Confirm frontend has `package.json` and `package-lock.json`.
5. Run `docker compose up --build`.
6. Wait for PostgreSQL, backend readiness, and frontend health.
7. Open the frontend port and verify login/service browsing.
8. Stop with `docker compose down`; keep data with the named volume.
9. Remove local data only with deliberate `docker compose down -v`.
