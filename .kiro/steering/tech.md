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
| Testing | JUnit 5 + Mockito (service layer only; no controller or integration tests) |
| Assertions | AssertJ (`assertThat`) |

**groupId:** `co.draai`  
**artifactId:** `employeeapi`  
**Base package:** `employeehub`

### JWT Details
- Token contains `sub` (email) and `role` claim
- Secret configured via `${JWT_SECRET}`, expiry via `${JWT_EXPIRATION}` (default 86400000ms = 24h)
- `JwtAuthFilter` runs before `UsernamePasswordAuthenticationFilter`, extracts and validates the bearer token
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
| Port mapping | 5432 (DB), 8080 (API), 3000 (frontend), 3001 (dashboard) |

### Build Flow (Docker)
1. `docker-compose up --build`
2. DB starts first (health-checked with `pg_isready`)
3. API waits for DB healthy, then builds with Maven multi-stage Dockerfile
4. API DataSeeder runs on startup: seeds admin user, leave types, SA tax brackets, benefit types
5. Both React apps built with Node 20 multi-stage Dockerfile, output served by Nginx
6. Frontends wait for API health before starting

---

## Key Constraints and Known Issues

1. **`Employee.password` has no `@JsonIgnore`** — the password hash is included in `Employee` responses from all endpoints that return the entity directly. Only `MeResponse` (used by `GET /auth/me`) is safe. Any new endpoint returning `Employee` must use a response DTO or add `@JsonIgnore` to the field.

2. **DDL-auto is `update`** — fine for development, not for production. A production deployment needs `validate` with a proper migration tool (Flyway/Liquibase).

3. **`application.yml` has `logging.level` DEBUG for Spring Security** — should be `INFO` or `WARN` in production. The docker-compose overrides `LOGGING_LEVEL_ROOT: INFO` but the yml default still applies when running locally.

4. **react-router-dom version skew** — employeehub uses v7, hrdashboard uses v6. The API surface differs (`useNavigate` behavior, route matching). Keep each app pinned to its own version; do not accidentally upgrade one without testing.

5. **`ContactService.js` and `NewContactModal.jsx` in employeehub are dead code** — leftover from when the app was a contact manager. `normaliseEmployee()` is duplicated between `ContactService.js` and `EmployeeService.js`. These files are not imported anywhere active.

6. **Nginx `/api/` proxy in employeehub is unused** — the React app hits `REACT_APP_API_URL` (localhost:8080) directly. The proxy block in `nginx.conf` exists but is never triggered.

7. **No controller or integration tests** — only service-layer unit tests exist. The JWT filter has its own unit tests.

8. **`docs/api-contract.md` is aspirational** — it documents endpoints that don't exist (`/auth/register`, `/auth/logout`, `/auth/refresh`, `/auth/change-password`, `/employees/me` as a URL vs the actual `GET /auth/me`, etc.). Treat the actual controllers as the source of truth.
