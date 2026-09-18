# Architecture

## Overview

The Employee API is a RESTful web service built with Spring Boot 3.5.13, Java 21, and PostgreSQL. It follows a layered architecture with clear separation of concerns and JWT-based role-based access control.

## Layers

### Presentation Layer
Controllers handle HTTP requests and responses, grouped by domain module:
- `AuthController` — login, token refresh/logout, profile (`/auth/me`), and password change/reset
- `EmployeeController` — employee CRUD and photo upload
- `DepartmentController`, `TeamController` — organisation structure
- `LeaveController`, `TimesheetController` — workforce management
- `SalaryController`, `BenefitController` — compensation
- `PerformanceController`, `DocumentController` — employee development
- `NotificationController`, `AuditLogController` — system

### Service Layer
Business logic per module: `EmployeeService`, `LeaveService`, `SalaryService`, `TimesheetService`, `BenefitService`, `PerformanceService`, `DocumentService`, `NotificationService`, `AuditService`, etc.

### Repository Layer
JPA repositories extending `JpaRepository` for each domain entity.

### Domain Layer
JPA entities representing the full HR data model. See [../../docs/data-model-uml.md](../../docs/data-model-uml.md) for the full UML diagram.

### Security Layer
- `JwtAuthFilter` — intercepts every request and validates the Bearer token
- `JwtUtil` — token generation, parsing, and validation
- `SecurityConfig` — role-based access rules per endpoint
- `UserDetailsServiceImpl` — loads employees from DB for Spring Security

## Technologies

- **Framework**: Spring Boot 3.5.13
- **Language**: Java 21
- **Database**: PostgreSQL 15
- **ORM**: JPA/Hibernate
- **Migrations**: Flyway (owns the schema; Hibernate runs `ddl-auto=validate`)
- **Security**: Spring Security + JJWT 0.12.6 (stateless JWT; short-lived access + rotating refresh tokens)
- **Build Tool**: Maven
- **Utilities**: Lombok
- **Monitoring**: Spring Boot Actuator

## Key Domain Entities

| Module | Entities |
|--------|----------|
| Organisation | Department, Team |
| Employee | Employee |
| Auth | RefreshToken, PasswordResetToken |
| Leave | LeaveType, LeaveBalance, LeaveRequest |
| Salary | SalaryRecord, PaySlip, TaxBracket, SalaryIncreaseRequest |
| Benefits | BenefitType, EmployeeBenefit, BenefitApplication |
| Timesheets | Timesheet, TimesheetEntry |
| Performance | PerformanceCycle, PerformanceReview, PerformanceGoal |
| Documents | Document |
| System | AuditLog, Notification |

## Configuration

Application configuration is managed through `application.yml`:
- Database connection (PostgreSQL)
- JPA/Hibernate settings (`ddl-auto=validate` — Hibernate validates the mapping against the migrated schema and never mutates it)
- File upload limits
- Server port (8080)
- JWT secret + access-token expiration (`jwt.access-expiration`, 15 min) and refresh-token expiration (`jwt.refresh-expiration`, 7 days)

## Database Schema & Migrations

Flyway owns the database schema. Migrations live in `src/main/resources/db/migration` as `V{n}__snake_case.sql`, applied in order at startup; `V1__baseline.sql` is the initial schema. Hibernate runs with `ddl-auto=validate`, so it only checks that the entities match the migrated schema and never alters it — a mismatch fails startup, surfacing a missing migration early.

Any change to an entity must ship with a new `V{n}` migration in the same commit. Flyway is configured with `baseline-on-migrate` so an existing database created under the old `ddl-auto=update` is adopted (stamped at the baseline) rather than rejected. Reference/seed data is still loaded by `data.sql`, which runs after Flyway has created the schema.

## File Storage

Photos/documents are stored locally at `app.upload.directory` (env `UPLOAD_DIRECTORY`, default `~/employeehub/uploads/`; Docker `/app/photos/`), resolved by `PhotoService`. For production deployments, consider migrating to cloud storage such as AWS S3.

## Error Handling

Global exception handling via `GlobalExceptionHandler` with consistent `ApiResponse<T>` wrapper and proper HTTP status codes.

## Security

JWT authentication with role-based access control. Roles: `EMPLOYEE`, `MANAGER`, `HR_ADMIN`, `PAYROLL_ADMIN`, `SUPER_ADMIN`. See [security.md](security.md) for details.
