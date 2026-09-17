<div align="center">

# 🧑‍💼 EmployeeHub

### A full-stack HR management platform built for the South African context 🇿🇦

[![Java](https://img.shields.io/badge/Java-21-orange.svg?logo=openjdk&logoColor=white)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5-6DB33F.svg?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![React](https://img.shields.io/badge/React-18.2-61DAFB.svg?logo=react&logoColor=black)](https://react.dev/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-15-4169E1.svg?logo=postgresql&logoColor=white)](https://www.postgresql.org/)
[![Docker](https://img.shields.io/badge/Docker-Compose-2496ED.svg?logo=docker&logoColor=white)](https://docs.docker.com/compose/)
[![JWT](https://img.shields.io/badge/Auth-JWT-000000.svg?logo=jsonwebtokens&logoColor=white)](https://jwt.io/)
[![Swagger](https://img.shields.io/badge/API-Swagger-85EA2D.svg?logo=swagger&logoColor=black)](http://localhost:8080/swagger-ui.html)

</div>

---

## 📖 Overview

**EmployeeHub** is a full-stack HR management system composed of three independently deployed services:

| 🧩 Service | 📦 Stack | 📝 Description |
|-----------|----------|----------------|
| ⚙️ **`employeeapi`** | Java 21 · Spring Boot 3.5 | REST API for all HR operations |
| 👤 **`employeehub`** | React 18 · port `3000` | Employee self-service portal |
| 🛠️ **`hrdashboard`** | React 18 · port `3001` | HR admin portal (approvals & oversight) |

---

## ✨ Features

- 👥 **Employee Management** — full CRUD with profile photo upload & auto-generated employee numbers
- 🌴 **Leave Management** — typed balances (Annual, Sick, Maternity…) with manager/HR approval workflows
- ⏱️ **Timesheets** — draft, submit, and approve time entries
- 💰 **Salary & Payroll** — payslips with South African **PAYE** & **UIF** tax calculation (SARS 2025/26 brackets)
- 🎁 **Benefits** — catalogue, applications, and HR approval (Medical Aid, Pension, Life Cover)
- 📈 **Performance** — cycles, goals, and reviews with employee acknowledgement
- 📄 **Documents** — employee uploads with HR verification
- 🔔 **Notifications** — event-driven system alerts
- 📜 **Audit Trail** — every write logged with actor, action, and before/after values
- 🔐 **Security** — JWT auth with role-based access, account lockout, password reset & **rotating refresh tokens**
- 🐳 **Dockerized** — full stack with PostgreSQL, Spring Boot, and Nginx

---

## 🔑 Roles

| Role | 👤 Who | 🛡️ Access |
|------|--------|-----------|
| `SUPER_ADMIN` | System administrator | Full access |
| `HR_ADMIN` | HR staff | Manage employees, approvals, verification, audit logs |
| `PAYROLL_ADMIN` | Payroll staff | Salary records, payslips, increase approvals |
| `MANAGER` | Team leads | Approve their team's leave & timesheets |
| `EMPLOYEE` | All staff | Self-service (leave, timesheets, docs, performance, salary, benefits) |

---

## 🚀 Quick Start — Docker (Recommended)

```bash
docker-compose up --build
```

Once it's up, everything's wired together automatically:

| 🌐 Service | 🔗 URL |
|-----------|--------|
| 👤 Frontend | http://localhost:3000 |
| 🛠️ HR Dashboard | http://localhost:3001 |
| ⚙️ API | http://localhost:8080 |
| 📚 Swagger | http://localhost:8080/swagger-ui.html |

```bash
docker-compose down      # 🛑 stop the stack
docker-compose down -v   # 🧹 stop and wipe data volumes
```

> 🔑 **Default logins** (seeded on first boot):
> - Admin — `admin@employeehub.com` / `Admin@1234`
> - Employee — `Employee@1234`

---

## 🧑‍💻 Running Locally (Without Docker)

> 📋 **Prerequisites:** JDK **21** · Node **20** · PostgreSQL **15**

<details>
<summary>⚙️ <b>Backend</b> — <code>employeeapi</code></summary>

```bash
cd employeeapi
# 1. Configure PostgreSQL in src/main/resources/application.yml
# 2. Run:
./mvnw spring-boot:run
```
</details>

<details>
<summary>👤 <b>Frontend</b> — <code>employeehub</code></summary>

```bash
cd employeehub
npm install
npm start
```
</details>

<details>
<summary>🛠️ <b>HR Dashboard</b> — <code>hrdashboard</code></summary>

```bash
cd hrdashboard
npm install
npm start
```
</details>

---

## ⚙️ Environment Variables

Key variables used in `docker-compose.yml`:

| 🔧 Variable | 📝 Description | 🎯 Default |
|-------------|----------------|-----------|
| `SPRING_DATASOURCE_URL` | PostgreSQL connection URL | `jdbc:postgresql://db:5432/employeehub` |
| `SPRING_DATASOURCE_USERNAME` | DB username | `admin` |
| `SPRING_DATASOURCE_PASSWORD` | DB password | `administrator` |
| `UPLOAD_DIRECTORY` | Photo/document storage path | `/app/photos/` |
| `JWT_SECRET` | JWT signing secret | _(see `application.yml`)_ |
| `JWT_ACCESS_EXPIRATION` | Access-token lifetime (ms) | `900000` _(15 min)_ |
| `JWT_REFRESH_EXPIRATION` | Refresh-token lifetime (ms) | `604800000` _(7 days)_ |
| `CORS_ALLOWED_ORIGINS` | Allowed frontend origins | `http://localhost:3000,http://localhost:3001` |
| `REACT_APP_API_URL` | API base URL used by the React build | `http://localhost:8080` |

---

## 📁 Repository Structure

```
EmployeeHub/
├── ⚙️  employeeapi/         # Java Spring Boot backend REST API
├── 👤  employeehub/         # React employee self-service portal
├── 🛠️  hrdashboard/         # React HR admin dashboard
├── 📚  docs/                # Project-level guides & references
├── 🐳  docker-compose.yml   # Full-stack orchestration
├── ✅  validate-setup.sh    # Pre-run validation script
└── 🔌  test-connection.sh   # Post-run connectivity test
```

---

## 📝 Notes

- 🌐 Frontends are served by **Nginx** from React production builds
- 🔄 API changes require `docker-compose up --build` to take effect
- 💾 Photos persist in the `employee_photos` volume; DB data in the `postgres_data` volume

---

## 📚 Documentation

| 📄 Doc | 📖 Covers |
|--------|-----------|
| [`employeeapi/README.md`](employeeapi/README.md) | Backend setup & usage |
| [`employeehub/README.md`](employeehub/README.md) | Frontend scripts & development |
| [`docs/contianerization-guide.md`](docs/contianerization-guide.md) | Full Docker setup walkthrough |
| [`docs/swagger-guide.md`](docs/swagger-guide.md) | Swagger / OpenAPI usage |
| [`docs/api-contract.md`](docs/api-contract.md) | API contract reference |
| [`docs/backend-plan.md`](docs/backend-plan.md) | Step-by-step backend build guide |
| [`docs/data-model-uml.md`](docs/data-model-uml.md) | Data model UML diagram |
| [`employeeapi/docs/`](employeeapi/docs/) | API reference, architecture & security |

---

<div align="center">

Built with ☕ **Java Spring Boot** and ⚛️ **React** · Made for 🇿🇦 South African HR

</div>
