# Product — EmployeeHub

## What It Is

EmployeeHub is a full-stack HR management system built for a South African context. It consists of three independently deployed services: a Spring Boot REST API, a React self-service portal for employees, and a separate React admin portal for HR staff.

## Users and Roles

There are five roles, enforced by Spring Security on the API and decoded from the JWT on the frontend:

| Role | Who | What They Can Do |
|---|---|---|
| `SUPER_ADMIN` | System administrator | Full access, seeded by DataSeeder on first boot (admin@employeehub.com) |
| `HR_ADMIN` | HR staff | Manage all employees, approve leave/timesheets, verify documents, view audit logs |
| `PAYROLL_ADMIN` | Payroll staff | Manage salary records, generate payslips, approve salary increase requests |
| `MANAGER` | Team leads | Approve leave/timesheets for their direct reports, submit salary increase requests |
| `EMPLOYEE` | All staff | Self-service: leave, timesheets, documents, performance, salary, benefits |

A `MANAGER` is also an `EMPLOYEE` — they access self-service features and their team management features through the same portal.

## The Two Frontend Applications

**employeehub** (port 3000) — Employee Self-Service Portal
- Used by all employees including HR and managers
- Features: Dashboard, Leave management, Timesheets, Documents, Performance (goals & reviews), Salary (payslips), Benefits
- HR/Admin users additionally see the Employees section (CRUD, photo upload, status management)

**hrdashboard** (port 3001) — HR Admin Portal
- Used by HR_ADMIN and SUPER_ADMIN only
- Focused on approvals and oversight: Leave approvals, Timesheet approvals, Document verification, Employee directory, Audit logs
- Does not have salary, benefits, performance, or notifications pages — those workflows are handled in the API or are employee-facing only

## Core Features

- **Employee management** — Full CRUD with profile photos stored on disk, auto-generated employee numbers (EMP-001, EMP-002…), department and team assignment, manager hierarchy
- **Leave management** — Submit requests against typed leave balances (Annual, Sick, Family Responsibility, Maternity, Parental, Study); manager/HR approval workflow; balance deduction on approval
- **Timesheets** — Create drafts, add time entries, submit for approval, manager/HR approves or rejects
- **Salary** — HR sets salary records; payslips generated with South African PAYE and UIF tax calculation using seeded 2025/2026 SARS tax brackets; salary increase request workflow
- **Benefits** — Catalogue of benefit types (Medical Aid, Pension Fund, Life Cover); employees apply; HR approves; enrolled benefits tracked per employee
- **Performance** — HR creates performance cycles; managers set goals for employees; managers submit reviews; employees acknowledge reviews
- **Documents** — Employee uploads (ID, contracts, certificates, etc.); HR verifies or rejects
- **Notifications** — System notifications generated on workflow events (leave approved/rejected, salary increase approved, etc.); employees mark as read
- **Audit log** — All write operations logged with actor, action, entity type, and before/after values; paginated and filterable by HR

## Business Context

- Tax calculations use South African SARS PAYE brackets and UIF (1% employee contribution, capped at R177.12/month)
- Default admin password: `Admin@1234`; default employee password: `Employee@1234` (set by DataSeeder on startup)
- Employee numbers follow the pattern `EMP-NNN` (zero-padded, sequential)
