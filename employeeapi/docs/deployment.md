# Deployment Guide

## Docker Deployment (Primary)

The recommended way to run the full stack is via Docker Compose from the project root.

### Prerequisites
- Docker 20.10+
- Docker Compose 2.0+

### Start All Services

```bash
docker-compose up --build
```

| Service       | URL |
|---------------|-----|
| Frontend      | http://localhost:3000 |
| HR Dashboard  | http://localhost:3001 |
| API           | http://localhost:8080 |
| Swagger       | http://localhost:8080/swagger-ui/index.html |

### Stop Services

```bash
# Stop containers
docker-compose down

# Stop and remove all volumes (deletes DB data and photos)
docker-compose down -v
```

### Rebuild After Code Changes

```bash
docker-compose up --build
```

### View Logs

```bash
# All services
docker-compose logs -f

# Specific service
docker-compose logs -f api
docker-compose logs -f frontend
docker-compose logs -f db
```

---

## CI/CD

### CI — `.github/workflows/ci.yml`

Runs on every push and pull request to `master`: builds and tests the backend (`./mvnw verify`) and both React apps, then a gated `docker-build` job validates that the full stack builds.

### CD — `.github/workflows/cd.yml`

Runs on push to `master` and on `v*.*.*` tags.

- **`publish`** builds and pushes all three images to GitHub Container Registry (GHCR) at `ghcr.io/<owner>/<repo>/<component>` for `api`, `employeehub`, and `hrdashboard`. Tags include the branch, long commit SHA, semver (on tags), `edge` (on master), and `latest` (on tags). Authentication uses the built-in `GITHUB_TOKEN` (`packages: write`) — no extra registry credentials are required.
- **`deploy`** (GitHub environment `production`) pulls the published images onto a host over SSH and runs `docker compose -f docker-compose.prod.yml up -d`. It is a no-op until the deployment secrets are configured.

### Production compose — `docker-compose.prod.yml`

Unlike `docker-compose.yml` (which builds locally), this file **pulls** the images published to GHCR. It is copied to the deploy host and run with:

```bash
REGISTRY=ghcr.io IMAGE_PREFIX=<owner>/<repo> IMAGE_TAG=edge \
  docker compose -f docker-compose.prod.yml up -d
```

`POSTGRES_PASSWORD` and `JWT_SECRET` are required at runtime (provided via the host environment or a `.env` file next to the compose file) — the compose file fails fast if they are missing.

### Enabling server deploys

Set these secrets (and a `production` environment) in the repository. Without them, CD only publishes images and skips the deploy step.

| Secret | Purpose |
|--------|---------|
| `DEPLOY_HOST` | Target server hostname/IP |
| `DEPLOY_USER` | SSH user |
| `DEPLOY_SSH_KEY` | Private key for SSH access |
| `DEPLOY_PATH` | Directory on the host where the compose file is placed |

The frontend API URL baked into the published images can be set via the `DEPLOY_API_URL` repository variable (defaults to `http://localhost:8080`).

---

## Service Architecture

```
frontend (Nginx:3000) → api (Spring Boot:8080) → db (PostgreSQL:5432)
hrdashboard (Nginx:3001) ↗
```

All services communicate over the `docker-net` bridge network. Service names resolve as DNS hostnames inside containers (e.g. `http://api:8080`).

### Volumes
- `postgres_data` — persists PostgreSQL data
- `employee_photos` — persists uploaded employee photos at `/app/photos/`

---

## Environment Variables

Configured in `docker-compose.yml`. Key variables:

| Variable | Value in Docker |
|----------|----------------|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://db:5432/employeehub` |
| `SPRING_DATASOURCE_USERNAME` | `admin` |
| `SPRING_DATASOURCE_PASSWORD` | `administrator` |
| `UPLOAD_DIRECTORY` | `/app/photos/` |
| `JWT_SECRET` | (set in docker-compose or env) |
| `JWT_ACCESS_EXPIRATION` | `900000` (15 min) |
| `JWT_REFRESH_EXPIRATION` | `604800000` (7 days) |
| `REACT_APP_API_URL` | `http://localhost:8080` (build arg) |

### Auth hardening (optional)

| Variable | Default | Purpose |
|----------|---------|---------|
| `AUTH_LOCKOUT_MAX_ATTEMPTS` | 5 | Failed logins before lockout |
| `AUTH_LOCKOUT_DURATION_MINUTES` | 15 | Lockout duration |
| `AUTH_PASSWORD_RESET_TTL_MINUTES` | 30 | Reset token validity |
| `AUTH_PASSWORD_RESET_URL` | `http://localhost:3000/reset-password` | Reset link base URL |
| `MAIL_ENABLED` | false | Enable outbound email (reset links) |
| `MAIL_FROM` | `no-reply@employeehub.local` | From address |
| `SPRING_MAIL_HOST` / `SPRING_MAIL_PORT` / `SPRING_MAIL_USERNAME` / `SPRING_MAIL_PASSWORD` | (unset) | SMTP settings; required for email delivery |

> Email delivery is a no-op unless `MAIL_ENABLED=true` and SMTP is configured, so the stack runs without a mail server.

---

## Local Development (Without Docker)

### Backend

1. Ensure PostgreSQL is running locally on port 5432 with an `employeehub` database
2. Run:
   ```bash
   cd employeeapi
   ./mvnw spring-boot:run
   ```

### Frontend

```bash
cd employeehub
npm install
npm start
```

### HR Dashboard

```bash
cd hrdashboard
npm install
npm start
```

> Hot reload is active in local dev mode. In Docker, changes require `docker-compose up --build`.

---

## Building the Backend JAR

```bash
cd employeeapi
./mvnw clean package -DskipTests
java -jar target/employeeapi-1.0.0.jar
```

---

## Troubleshooting

**Port already in use:**
```bash
lsof -i :8080
lsof -i :3000
lsof -i :3001
```

**API not starting (DB not ready):**  
The API depends on the DB health check. Wait for `pg_isready` to pass or check DB logs:
```bash
docker-compose logs db
```

**Frontend showing stale data:**  
React is served as a production build. Always rebuild after frontend changes:
```bash
docker-compose up --build
```

**Access PostgreSQL directly:**
```bash
docker exec -it employeehub-db psql -U admin -d employeehub
```
