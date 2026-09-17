# QueueWise Deployment Architecture

**Version:** 1.0  
**Scope:** Student portfolio project and simple production-style deployment  
**Technologies:** React/Vite, Spring Boot, PostgreSQL, Docker, Docker Compose, GitHub Actions

## 1. Deployment Goals

QueueWise should be easy to run locally, easy to demonstrate, and safe to deploy without introducing Kubernetes or distributed infrastructure. The MVP deployment consists of:

```text
Browser
  |
  | HTTPS + WebSocket upgrade
  v
Frontend container (React/Vite production build)
  |
  | REST + WSS
  v
Backend container (Spring Boot modular monolith)
  |
  | PostgreSQL connection
  v
PostgreSQL (Compose locally, managed PostgreSQL preferred in production)
```

The backend remains the consistency boundary. WebSocket updates are served by the Spring Boot application and do not require Redis or Kafka for a single backend instance.

## 2. Environments

### Local

- Docker Compose runs PostgreSQL, backend, and optionally the frontend.
- Developer secrets live in an ignored `.env` file or local environment, never Git.
- Vite dev server may run outside Docker for fast frontend iteration.
- Spring profile: `local`.

### CI

- GitHub Actions runs compilation, tests, migrations/integration tests, frontend build, and image builds.
- No production secrets are used in ordinary pull-request tests.
- PostgreSQL service container or Testcontainers provides isolated integration database.

### Production/demo

- Frontend image served over HTTPS.
- One Spring Boot backend image behind an HTTPS reverse proxy.
- Managed PostgreSQL is preferred; PostgreSQL Compose is acceptable for a controlled portfolio demo.
- Spring profile: `prod`.
- WebSocket upgrade is enabled by the proxy.

Kubernetes is intentionally not used. It would add orchestration, ingress, secret, observability, and deployment complexity beyond this MVP's needs.

## 3. Local Development

Prerequisites:

- Docker Desktop with Compose.
- Java 25 and Maven if running the backend directly.
- Node.js version selected by the frontend project if running Vite directly.

Typical flow:

```bash
cp .env.example .env
docker compose up --build
```

Open the configured frontend URL, usually `http://localhost:5173` for Vite development or `http://localhost:8080` for the production frontend container. The backend should expose HTTP and WebSocket endpoints on its configured port.

For fast iteration:

```bash
docker compose up postgres
mvn spring-boot:run
npm install
npm run dev
```

The local frontend API/WebSocket URLs must point to the local backend, and CORS must allow the Vite origin.

## 4. Environment Variables

Backend example:

```text
SPRING_PROFILES_ACTIVE=local
DATABASE_URL=jdbc:postgresql://postgres:5432/queuewise
DATABASE_USERNAME=queuewise
DATABASE_PASSWORD=local-only-secret
JWT_ISSUER=queuewise-api
JWT_AUDIENCE=queuewise-web
JWT_PRIVATE_KEY=local-only-key-or-mounted-development-key
JWT_ACCESS_TOKEN_MINUTES=15
CORS_ALLOWED_ORIGINS=http://localhost:5173,http://localhost:8080
APP_TIME_ZONE=UTC
LOG_LEVEL_ROOT=INFO
```

Frontend public configuration:

```text
VITE_API_BASE_URL=http://localhost:8080/api/v1
VITE_WEBSOCKET_URL=ws://localhost:8080/ws
```

Rules:

- Only `VITE_*` public values may be included in the frontend bundle.
- Database passwords, JWT keys, provider credentials, and signing secrets must never use `VITE_*`.
- `.env` is ignored by Git; commit `.env.example` with placeholders only.
- Production values are injected by the hosting environment or secret store.
- The application fails startup in production when required secrets are missing.

## 5. Docker Images

### Backend Dockerfile shape

Use a multi-stage build:

1. Maven/JDK build stage with Java 25.
2. Small Java runtime stage containing the built Spring Boot jar.
3. Non-root runtime user.
4. No source code, Maven cache, or secrets in the final image.
5. Health endpoint available to the container runtime.

Example commands:

```bash
docker build -f backend/Dockerfile -t queuewise-backend:local ./backend
docker run --env-file .env -p 8080:8080 queuewise-backend:local
```

### Frontend Dockerfile shape

1. Node build stage runs `npm ci` and `npm run build`.
2. A small static web server image serves the Vite `dist` directory.
3. The image contains only public runtime configuration.
4. The server supports SPA fallback to `index.html` and WebSocket proxying is configured at the outer proxy where needed.

```bash
docker build -f frontend/Dockerfile -t queuewise-frontend:local ./frontend
docker run -p 8080:80 queuewise-frontend:local
```

Images are tagged with the Git commit SHA in CI and with a release version for deployment.

## 6. Docker Compose

Local Compose should contain only the necessary services:

```yaml
services:
  postgres:
    image: postgres:approved-version
    environment:
      POSTGRES_DB: queuewise
      POSTGRES_USER: queuewise
      POSTGRES_PASSWORD: ${DATABASE_PASSWORD}
    ports:
      - "5432:5432"
    volumes:
      - queuewise-postgres:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U queuewise -d queuewise"]
      interval: 5s
      timeout: 5s
      retries: 10

  backend:
    build: ./backend
    env_file: .env
    depends_on:
      postgres:
        condition: service_healthy
    ports:
      - "8080:8080"

  frontend:
    build: ./frontend
    depends_on:
      - backend
    ports:
      - "5173:80"

volumes:
  queuewise-postgres:
```

Pin approved image versions in the actual repository rather than using `latest`. Do not add Redis, Kafka, Elasticsearch, or Kubernetes services to local Compose.

## 7. Database Startup and Migrations

Startup order:

1. PostgreSQL container starts.
2. PostgreSQL health check confirms it accepts connections.
3. Backend starts and validates configuration.
4. Migration tool, such as Flyway selected by the project, applies versioned migrations.
5. Backend readiness becomes healthy only after migrations and database connectivity succeed.
6. Frontend becomes available.

Migrations:

- Are committed under a versioned migration directory.
- Run automatically before the application accepts traffic.
- Are backward-compatible across rolling/rollback boundaries where practical.
- Add nullable/defaulted structures before code requires them, then tighten constraints after backfill.
- Never drop production data as part of an ordinary startup migration.

Example local reset, only for disposable local data:

```bash
docker compose down -v
docker compose up --build
```

Never use `down -v` against production or a database containing user data.

## 8. Backend Deployment

Deploy the immutable backend image with:

- Database URL and credentials injected externally.
- JWT signing key and issuer/audience configuration.
- Explicit CORS origins.
- Resource CPU/memory limits appropriate to the hosting platform.
- HTTPS at the reverse proxy.
- HTTP health/readiness endpoints.
- WebSocket upgrade support at the proxy.
- Structured logs to the hosting log sink.
- One replica initially unless multi-instance WebSocket event delivery is designed.

The backend should listen on a configurable port and shut down gracefully so in-flight requests finish and WebSocket clients reconnect. Queue correctness is preserved in PostgreSQL across process restarts.

## 9. Frontend Deployment

Build the React/Vite application in CI and serve the static artifact from the frontend container or a simple static hosting service. Configure public API and WebSocket URLs at build time or through a documented runtime configuration file that contains no secrets.

The frontend deployment must:

- Support SPA route fallback.
- Use HTTPS in production.
- Point REST calls to `/api/v1` or the configured backend origin.
- Point WebSocket calls to the secure `wss` endpoint.
- Handle `401`, `403`, `409`, `429`, and reconnect states.
- Not contain database credentials, JWT signing keys, or provider secrets.

## 10. CORS and Proxy Configuration

For local development, allow only the configured Vite origin, such as `http://localhost:5173`. For production, allow only the deployed frontend origin, such as `https://app.example.com`.

Allow required methods and headers only:

```text
Methods: GET, POST, PATCH, PUT, OPTIONS
Headers: Authorization, Content-Type, Accept, X-Correlation-Id
```

If refresh tokens use cookies, configure `allowCredentials=true`, explicit origins, Secure/HttpOnly/SameSite cookie attributes, and the documented CSRF strategy. Never use wildcard origins with credentials.

The reverse proxy must pass:

```text
Upgrade: websocket
Connection: Upgrade
```

for `/ws` while preserving authentication and request correlation behavior.

## 11. Production Secrets

Use the hosting platform's secret mechanism or encrypted GitHub Actions environment secrets for:

- Database password.
- JWT private/signing key.
- Notification provider credentials.
- Any refresh-token or encryption secret.

Secret rules:

- Separate secrets per environment.
- Rotate signing keys with a planned overlap window.
- Do not print secrets in CI, startup logs, health output, images, or frontend artifacts.
- Restrict production secret access to deployment jobs and runtime identity.
- Fail safely if required secrets are absent.
- Use a local-only `.env` for development and never commit it.

## 12. Health Checks

Expose separate concepts:

- **Liveness:** process is running and can respond; should not fail merely because PostgreSQL is temporarily unavailable.
- **Readiness:** backend can serve traffic, required migrations are complete, and PostgreSQL is reachable.
- **Dependency status:** admin/operations view for database, notification processing, and WebSocket health without secrets.

Container checks should call a safe health endpoint, not a business endpoint. Health responses must not reveal connection strings, stack traces, table names, or credentials.

Example local Compose backend health check:

```yaml
healthcheck:
  test: ["CMD-SHELL", "wget --spider -q http://localhost:8080/actuator/health/readiness"]
  interval: 10s
  timeout: 5s
  retries: 12
```

## 13. Logs and Observability

Use structured backend logs with:

- Timestamp and level.
- Application version/commit SHA.
- Correlation ID.
- Safe organization and actor identifiers.
- Endpoint, status, duration, and error code.
- Database/notification/WebSocket outcome where useful.

Do not log passwords, JWTs, refresh tokens, private keys, provider credentials, or full personal data. Configure retention and access according to the hosting platform. Frontend errors should include a correlation ID when returned by the API, not secrets or full tokens.

Monitor:

- Readiness/liveness failures.
- API p95 latency and error rate.
- Database connectivity/pool saturation.
- Queue transition conflicts.
- WebSocket active sessions/update latency.
- Notification retry backlog.
- Migration version and failure.

## 14. GitHub Actions CI/CD

Pull request workflow:

```yaml
name: ci

on:
  pull_request:
  push:
    branches: [main]

jobs:
  backend:
    runs-on: ubuntu-latest
    services:
      postgres:
        image: postgres:approved-version
        env:
          POSTGRES_DB: queuewise_test
          POSTGRES_USER: queuewise
          POSTGRES_PASSWORD: test-password
        ports: ["5432:5432"]
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '25'
          distribution: temurin
          cache: maven
      - run: ./mvnw test

  frontend:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with:
          node-version-file: frontend/.nvmrc
          cache: npm
          cache-dependency-path: frontend/package-lock.json
      - run: npm ci
        working-directory: frontend
      - run: npm test -- --run
        working-directory: frontend
      - run: npm run build
        working-directory: frontend
```

Deployment workflow after approval:

1. Build and scan backend/frontend images.
2. Tag with commit SHA and release version.
3. Push to the selected container registry.
4. Inject environment/secrets through the hosting platform.
5. Run/verify database migrations.
6. Deploy backend and frontend images.
7. Wait for readiness.
8. Run smoke tests for login, service browsing, booking, and health.
9. Publish deployment result and image versions.

Pin action versions according to the repository's security policy and do not echo secrets.

## 15. Rollback Strategy

### Application rollback

1. Stop promotion if readiness or smoke tests fail.
2. Keep the previous known-good backend/frontend image tags.
3. Redeploy previous images with the same compatible configuration.
4. Verify readiness, login, queue read, and WebSocket connection.
5. Record the incident and correlation IDs.

### Database rollback

Prefer forward-compatible migrations and a corrective migration rather than automatically down-migrating production. Before destructive schema changes, take a verified backup. A database rollback requires a documented restore plan, because old application images may not understand new schema/data.

### Release artifacts

Every release stores:

- Backend image tag.
- Frontend image tag.
- Git commit SHA.
- Migration range.
- Environment variable changes.
- Rollback image/tag.
- Smoke-test result.

## 16. Backup and Recovery

Production PostgreSQL requires automated encrypted backups, retention, and periodic restore verification. Include appointments, tokens, token events, audit logs, and migration metadata. Store recovery instructions outside the database.

An initial portfolio deployment should document an agreed recovery point and recovery time target rather than claiming an enterprise SLA. A restore test must verify the backend can start, migrations are compatible, customers can authenticate, and queue history remains available.

## 17. Deployment Acceptance Criteria

1. A new developer can start PostgreSQL/backend/frontend locally with documented commands.
2. Backend does not accept traffic before database health and migrations pass.
3. Production secrets are externalized and absent from images/frontend bundles.
4. CORS allows only configured frontend origins.
5. HTTPS and WebSocket upgrade work in the deployed environment.
6. Health endpoints distinguish liveness and readiness without sensitive output.
7. CI runs Java, PostgreSQL, frontend, and build checks.
8. Images are immutable and traceable to a commit.
9. A failed deployment can return to the previous application image.
10. No Kubernetes, Redis, Kafka, Elasticsearch, or microservices are required for the MVP deployment.
