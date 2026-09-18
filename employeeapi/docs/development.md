# Development Guide

## Prerequisites

- Java 21
- Maven 3.6+ (or the bundled `./mvnw` wrapper)
- PostgreSQL 15 (for local dev without Docker)
- Docker + Docker Compose (for containerized dev)
- Node.js 20+ (for frontend local dev)

## Project Structure

```
employeeapi/src/main/java/employeehub/
├── Application.java
├── config/
│   ├── Config.java                  # CORS configuration
│   └── OpenApiConfiguration.java    # Swagger/OpenAPI setup
├── constant/
│   └── Constant.java                # X_REQUESTED_WITH header constant
├── controller/
│   ├── AuthController.java          # /auth endpoints
│   ├── EmployeeController.java      # /employees endpoints
│   ├── DepartmentController.java
│   ├── TeamController.java
│   ├── LeaveController.java
│   ├── SalaryController.java
│   ├── BenefitController.java
│   ├── TimesheetController.java
│   ├── PerformanceController.java
│   ├── DocumentController.java
│   ├── NotificationController.java
│   ├── AuditLogController.java
│   └── HealthController.java        # /health endpoint
├── domain/                          # JPA entities and enums
├── dto/                             # Request/response DTOs
├── exception/                       # GlobalExceptionHandler
├── repository/                      # JPA repositories
├── security/                        # JWT filter, JwtUtil, SecurityConfig
└── service/                         # Business logic per module
```

## Running the Backend

### With Docker (Recommended)

```bash
# From project root
docker-compose up --build
```

### Locally

```bash
cd employeeapi
./mvnw spring-boot:run
```

Requires PostgreSQL on `localhost:5432` with database `employeehub`, user `admin`, password `administrator`.

## Configuration

`application.yml` uses environment variables with local fallback defaults:

```yaml
spring:
  datasource:
    url: ${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5432/employeehub}
    username: ${SPRING_DATASOURCE_USERNAME:admin}
    password: ${SPRING_DATASOURCE_PASSWORD:administrator}
```

Override any value via environment variable — Docker Compose injects these automatically.

## Photo Storage

Resolved by `PhotoService` from the `app.upload.directory` property:
```java
@Value("${app.upload.directory:${user.home}/employeehub/uploads/}")
private String uploadDirectory;
```

- Docker: `/app/photos/` (set via the `UPLOAD_DIRECTORY` env var)
- Local: `~/employeehub/uploads/` (default)

## Seed Data

Startup seed data (departments, teams, leave types, SA tax brackets, benefit types,
and the seeded admin/employee accounts) is loaded from `src/main/resources/data.sql`
via Spring's `spring.sql.init` (`mode: always`). Inserts are idempotent
(`INSERT ... WHERE NOT EXISTS`). There is no Java `DataSeeder` class.

## Adding New Features

1. Add or update the domain entity in `domain/`
2. Add repository methods in the relevant `repository/` interface
3. Implement business logic in the relevant `service/`
4. Expose via the relevant `controller/`
5. Add role-based access rules in `SecurityConfig` if needed

## Building

```bash
# Build JAR
./mvnw clean package -DskipTests

# Run tests
./mvnw test
```

## IDE Setup

- IntelliJ IDEA: Import as Maven project, enable Lombok annotation processing
- VS Code: Install Java Extension Pack + Spring Boot Extension Pack

## Troubleshooting

**Port 8080 in use:**
```bash
lsof -i :8080
kill -9 <PID>
```

**Database connection failed:**
- Check PostgreSQL is running
- Verify credentials in `application.yml`
- Confirm `employeehub` database exists
