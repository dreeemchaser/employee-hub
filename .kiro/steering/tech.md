# Tech — EmployeeHub Stack

## Backend — employeeapi

| Concern | Choice |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.5.13 |
| Build | Maven (mvnw wrapper) |
| Database | PostgreSQL 15 (via Spring Data JPA / Hibernate) |
| ORM | Hibernate with DDL-auto `update` (dev/docker) |
| Auth | Spring Security + JWT (jjwt 0.12.6), stateless sessions |
| API docs | springdoc-openapi 2.8.9 (Swagger UI at `/swagger-ui/index.html`) |
| Boilerplate | Lombok (`@Getter`, `@Setter`, `@RequiredArgsConstructor`, `@Slf4j`) |
| Health | Spring Boot Actuator (`/actuator/health`) |
| Testing | JUnit 5 + Mockito (service layer) + `@WebMvcTest` controller tests (`spring-security-test`); no `@DataJpaTest`/Testcontainers integration or ArchUnit yet |
| Assertions | AssertJ (`assertThat`) |

**groupId:** `co.draai`  
**artifactId:** `employeeapi`  
**Base package:** `employeehub`

### JWT Details
- Access token contains `sub` (email) and `role` claim
- Secret via `${JWT_SECRET}`. Access-token expiry via `${JWT_ACCESS_EXPIRATION}` (default 900000ms = 15 min); refresh-token expiry via `${JWT_REFRESH_EXPIRATION}` (default 604800000ms = 7 days). `${JWT_EXPIRATION}` is honoured only as a legacy fallback for the access expiry.
- Login returns a `{accessToken, refreshToken}` pair. The refresh token is an opaque, server-tracked, SHA-256-hashed string that rotates on use (`POST /auth/refresh`) and can be revoked (`POST /auth/logout`); reuse of a revoked token revokes all of that user's sessions.
- `JwtAuthFilter` runs before `UsernamePasswordAuthenticationFilter`, extracts and validates the bearer access token
- Roles stored as `ROLE_<ROLENAME>` in `GrantedAuthority` (e.g. `ROLE_HR_ADMIN`)

### CORS
Configured in `Config.java` via `CorsFilter`. Allowed origins read from `${CORS_ALLOWED_ORIGINS}` (default: `http://localhost:3000,http://localhost:3001`).

### File Storage
Photos and documents stored on disk at `${UPLOAD_DIRECTORY}` (default `~/employeehub/uploads/`, Docker: `/app/photos/`). Served via `GET /employees/photo/{filename}` (public endpoint).

---

## Frontend — employeehub (port 3000)

| Concern | Choice |
|---|---|
| Framework | React 18.2 |
| Routing | react-router-dom **v7.14.0** |
| HTTP | axios ^1.14.0 |
| Build | react-scripts 5.0.1 (Create React App) |
| Styling | Plain CSS with CSS custom properties (no Tailwind, no MUI, no CSS-in-JS) |
| State | Local `useState` + `useCallback` per page; no global store |
| Icons | Bootstrap Icons (loaded via HTML `<link>`, not npm) |

---

## Frontend — hrdashboard (port 3001)

| Concern | Choice |
|---|---|
| Framework | React 18.2 |
| Routing | react-router-dom **v6.30.1** ← different major version from employeehub |
| HTTP | axios ^1.14.0 |
| Build | react-scripts 5.0.1 (Create React App) |
| Styling | Plain CSS with CSS custom properties (shared design language with employeehub) |
| State | Local `useState` + `useCallback` per page; no global store |
| Icons | Bootstrap Icons |

---

## Infrastructure

| Concern | Choice |
|---|---|
| Container runtime | Docker + Docker Compose |
| API container | eclipse-temurin:21-jre-alpine |
| Frontend containers | nginx:alpine serving React production builds |
| Database container | postgres:15-alpine |
| Internal network | `docker-net` bridge; services communicate by container name (`api`, `db`) |
| Volumes | `postgres_data` (DB), `employee_photos` (photo uploads) |
| Port mapping | DB host `5433` → container `5432`; 8080 (API), 3000 (frontend), 3001 (dashboard) |

### Build Flow (Docker)
1. `docker-compose up --build`
2. DB starts first (health-checked with `pg_isready`)
3. API waits for DB healthy, then builds with Maven multi-stage Dockerfile
4. On startup, `data.sql` seeds reference data + accounts (admin user, leave types, SA tax brackets, benefit types) via Spring `spring.sql.init` — there is no Java `DataSeeder` class
5. Both React apps built with Node 20 multi-stage Dockerfile, output served by Nginx
6. Frontends wait for API health before starting

---

## Key Constraints and Known Issues

1. **Controllers still return raw JPA entities in many places** — `Employee.password` IS `@JsonIgnore`'d (no password leak), but most endpoints still return domain entities rather than DTOs. This exposes `Employee.idNumber` (PII) and internal fields, and risks lazy-serialization errors on entities whose embedded `Employee` lacks a `@JsonIgnoreProperties` mask. The `best-practices-hardening` spec (Slice A) addresses this. Prefer a response DTO for any new entity-returning endpoint.

2. **DDL-auto is `update`** in all three environments (local `application.yml`, `docker-compose.yml`, `docker-compose.prod.yml`) — fine for development, not for production. A production deployment needs `validate` with a proper migration tool (Flyway). The `best-practices-hardening` spec (Slice B) addresses this.

3. **`application.yml` has `logging.level` DEBUG for Spring Security** — should be `INFO` or `WARN` in production. The docker-compose overrides `LOGGING_LEVEL_ROOT: INFO` but the yml default still applies when running locally.

4. **react-router-dom version skew** — employeehub uses v7, hrdashboard uses v6. The API surface differs (`useNavigate` behavior, route matching). Keep each app pinned to its own version; do not accidentally upgrade one without testing.

5. **Nginx `/api/` proxy in employeehub is unused** — the React app hits `REACT_APP_API_URL` (localhost:8080) directly. The proxy block in `nginx.conf` exists but is never triggered.

6. **Controller tests exist; integration + architecture tests do not yet.** Service-layer unit tests (plus JWT filter and refresh/lockout/password-reset service tests) and `@WebMvcTest` controller tests (every controller + a `GlobalExceptionHandler` status matrix, driven with `spring-security-test` so route authorization is exercised) both exist. Still missing — the remaining slices of the `best-practices-testing` spec: `@DataJpaTest` + Testcontainers repository/integration tests (real Postgres, per-`@Query` coverage) and the ArchUnit `ArchitectureTest`.

7. **`docs/api-contract.md` is partly aspirational** — it carries a banner to that effect. `/auth/logout`, `/auth/refresh`, and `/auth/change-password` DO exist; `/auth/register` and `/employees/me` do NOT (self-service is `GET`/`PATCH /auth/me`), and any WebSocket/real-time section is unimplemented. Treat the actual controllers (and `employeeapi/docs/api.md`) as the source of truth.
