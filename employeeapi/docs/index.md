# Employee API Documentation

Spring Boot REST API for the EmployeeHub HR management system.

## Table of Contents

- [API Reference](api.md)
- [Architecture](architecture.md)
- [Deployment](deployment.md)
- [Development](development.md)
- [Security](security.md)
- [Performance](performance.md)
- [Diagrams](diagrams/)
  - [Class Diagram](diagrams/class-diagram.md)
  - [Sequence Diagram](diagrams/sequence-diagram.md)

## Quick Start

### With Docker (Recommended)

```bash
# From project root
docker-compose up --build
```

- API: http://localhost:8080
- Swagger: http://localhost:8080/swagger-ui/index.html

### Without Docker

```bash
cd employeeapi
./mvnw spring-boot:run
```

Requires PostgreSQL running locally on port 5432 with an `employeehub` database.

## Key Features

- Full CRUD operations for employees
- Profile photo upload and retrieval
- Leave management with approval workflows
- Timesheet submission and approval
- Salary records, payslip generation, and SA PAYE/UIF tax calculation
- Benefits catalogue and employee applications
- Performance cycles, goals, and reviews
- Document upload and HR verification
- Real-time notifications
- Audit trail
- JWT-based authentication with role-based access control
- Environment variable driven configuration
- Swagger/OpenAPI documentation
- Spring Boot Actuator for monitoring
- Docker-ready with multi-stage build
