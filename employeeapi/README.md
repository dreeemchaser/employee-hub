# Employee API

Spring Boot REST API for the EmployeeHub HR management system, backed by PostgreSQL.

## Tech Stack

| Component | Technology |
|-----------|------------|
| Language | Java 21 |
| Framework | Spring Boot 3.5.13 |
| Persistence | Spring Data JPA + Hibernate |
| Database | PostgreSQL 15 |
| Build Tool | Maven |
| Utilities | Lombok |
| Security | Spring Security + JWT (JJWT 0.12.6; short-lived access + rotating refresh tokens) |
| API Docs | SpringDoc OpenAPI 2.8.9 (Swagger) |

## Project Structure

```
employeeapi/
├── src/main/java/employeehub/
│   ├── Application.java
│   ├── config/
│   │   ├── Config.java                  # CORS configuration
│   │   └── OpenApiConfiguration.java    # Swagger setup
│   ├── constant/
│   │   └── Constant.java                # X_REQUESTED_WITH header constant
│   ├── controller/
│   │   ├── AuthController.java          # /auth endpoints
│   │   ├── EmployeeController.java      # /employees endpoints
│   │   ├── DepartmentController.java
│   │   ├── TeamController.java
│   │   ├── LeaveController.java
│   │   ├── SalaryController.java
│   │   ├── BenefitController.java
│   │   ├── TimesheetController.java
│   │   ├── PerformanceController.java
│   │   ├── DocumentController.java
│   │   ├── NotificationController.java
│   │   ├── AuditLogController.java
│   │   └── HealthController.java
│   ├── domain/                          # JPA entities and enums
│   ├── dto/                             # Request/response DTOs
│   ├── exception/                       # Global exception handler
│   ├── repository/                      # JPA repositories
│   ├── security/                        # JWT filter, SecurityConfig
│   └── service/                         # Business logic
├── src/main/resources/
│   ├── application.yml                  # Config with env var support
│   └── data.sql                         # Startup seed data (spring.sql.init)
├── docs/                                # API and architecture docs
├── postman/                             # Postman collections
└── Dockerfile                           # Multi-stage Maven + JRE build
```

## Running with Docker (Recommended)

From the project root:

```bash
docker-compose up --build
```

API available at: **http://localhost:8080**  
Swagger UI: **http://localhost:8080/swagger-ui/index.html**

## Running Locally

1. Ensure PostgreSQL is running with an `employeehub` database
2. Update credentials in `src/main/resources/application.yml` if needed
3. Run:
   ```bash
   ./mvnw spring-boot:run
   ```

## API Overview

See [docs/api.md](docs/api.md) and the project-level [docs/api-contract.md](../docs/api-contract.md) for the full API reference.

| Module | Base Path |
|--------|-----------|
| Auth | `/auth` |
| Employees | `/employees` |
| Departments | `/departments` |
| Teams | `/teams` |
| Leave | `/leave` |
| Salary | `/salary` |
| Benefits | `/benefits` |
| Timesheets | `/timesheets` |
| Performance | `/performance` |
| Documents | `/documents` |
| Notifications | `/notifications` |
| Audit Log | `/audit-logs` |
| Dashboard | `/dashboard` |

## Authentication

All `/auth/**` endpoints are public (login, refresh, logout, forgot-password, reset-password); every other endpoint requires `Authorization: Bearer <accessToken>`. There is no `/auth/register` — accounts are created via `POST /employees` (HR/admin) or seeded on first boot.

```bash
# Login — returns { accessToken, refreshToken }
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email": "admin@employeehub.com", "password": "Admin@1234"}'

# Use the access token (short-lived, ~15 min)
curl http://localhost:8080/employees \
  -H "Authorization: Bearer <accessToken>"

# When it expires, exchange the refresh token for a new pair
curl -X POST http://localhost:8080/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{"refreshToken": "<refreshToken>"}'
```

## Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `SPRING_DATASOURCE_URL` | PostgreSQL URL | `jdbc:postgresql://localhost:5432/employeehub` |
| `SPRING_DATASOURCE_USERNAME` | DB username | `admin` |
| `SPRING_DATASOURCE_PASSWORD` | DB password | `administrator` |
| `UPLOAD_DIRECTORY` | Photo/document storage path | `~/employeehub/uploads/` |
| `SERVER_PORT` | Server port | `8080` |
| `JWT_SECRET` | JWT signing secret | (see application.yml) |
| `JWT_ACCESS_EXPIRATION` | Access-token expiry (ms) | `900000` (15 min) |
| `JWT_REFRESH_EXPIRATION` | Refresh-token expiry (ms) | `604800000` (7 days) |
| `SPRING_JPA_HIBERNATE_DDL_AUTO` | Hibernate DDL mode | `update` |

> `JWT_EXPIRATION` is still honoured as a legacy fallback for the access-token expiry, but prefer `JWT_ACCESS_EXPIRATION`.

## Photo Storage

Photos/documents are stored at the path defined by `app.upload.directory` (env `UPLOAD_DIRECTORY`), resolved by `PhotoService`:
- Docker: `/app/photos/` (mapped to `employee_photos` volume)
- Local: `~/employeehub/uploads/` (default)

## Building

```bash
# Build JAR
./mvnw clean package -DskipTests

# Run JAR directly
java -jar target/employeeapi-1.0.0.jar
```

## Documentation

- [API Reference](docs/api.md)
- [Architecture](docs/architecture.md)
- [Deployment](docs/deployment.md)
- [Development](docs/development.md)
- [Security](docs/security.md)
- [Performance](docs/performance.md)
