# Employee Hub — Backend Build Steps

Each step maps to one or more commits on the backend. Follow in order — each step depends on the previous.

> **Note — historical build plan.** This document records the original step-by-step build order and does not track later changes. The auth surface in particular has evolved since Step 4 (see the note there). For the current API, treat the live controllers, `docs/api-contract.md`, and `employeeapi/docs/api.md` as the source of truth.

---

## Step 1 — Project Restructure & Base Setup
**Branch:** `epic/refactor-step-2-backend-base`

- Rename base package from `contactapi` to `employeehub`
- Update `application.yml` with new DB name and app name
- Remove old `Contact` entity, repository, service, and controller
- Add global exception handler (`GlobalExceptionHandler`)
- Add base response wrapper (`ApiResponse<T>`)
- Add `AuditLog` entity and repository (used throughout all modules)

**Commit:** `refactor: restructure base package, remove Contact, add exception handler and audit log`

---

## Step 2 — Organisation Structure
**Branch:** `epic/refactor-step-3-org-structure`

- Add `Department` entity + repository + service + controller
  - `GET /departments`
  - `POST /departments`
  - `PUT /departments/{id}`
  - `DELETE /departments/{id}`
- Add `Team` entity + repository + service + controller
  - `GET /teams`
  - `GET /teams?departmentId=`
  - `POST /teams`
  - `PUT /teams/{id}`
  - `DELETE /teams/{id}`

**Commit:** `feat: add Department and Team entities, repositories, services, and controllers`

---

## Step 3 — Employee Entity & Core CRUD
**Branch:** `epic/refactor-step-4-employee-core`

- Add `Employee` entity (all fields from data model)
- Add `Role` enum (`EMPLOYEE`, `MANAGER`, `HR_ADMIN`, `PAYROLL_ADMIN`, `SUPER_ADMIN`)
- Add `EmploymentType` enum (`FULL_TIME`, `PART_TIME`)
- Add `EmploymentStatus` enum (`ACTIVE`, `INACTIVE`, `SUSPENDED`, `TERMINATED`)
- Add `EmployeeRepository`
- Add `EmployeeService` with:
  - Create employee
  - Get employee by ID
  - Get all employees (paginated)
  - Update employee
  - Deactivate / terminate employee
- Add `EmployeeController`
  - `GET /employees` (paginated, filterable by department/team/status)
  - `GET /employees/{id}`
  - `POST /employees`
  - `PUT /employees/{id}`
  - `PATCH /employees/{id}/status`
- Add employee number auto-generation (`EMP-001`, `EMP-002` etc.)

**Commit:** `feat: add Employee entity with full CRUD, role/status enums, and employee number generation`

---

## Step 4 — Auth & Security Overhaul
**Branch:** `epic/refactor-step-5-auth`

- Update `UserDetails` implementation to use `Employee` instead of old `User`
- Update `SecurityConfig` to use role-based access rules per endpoint
- Update JWT filter to load employee by email
- Add `POST /auth/login` — returns a JWT with a role claim
- Add `GET /auth/me` — returns current authenticated employee profile
- Ensure `MANAGER` can only access their team's data (service-level enforcement)

**Commit:** `feat: overhaul auth to use Employee entity, add role-based security, update JWT with role claim`

> **Since evolved (current auth surface):** there is **no** `/auth/register` — accounts are created via `POST /employees` (HR/admin) or seeded on first boot. `POST /auth/login` now returns a **pair**: `{accessToken, refreshToken}` (short-lived 15-min access + 7-day rotating refresh). Additional endpoints added later: `POST /auth/refresh`, `POST /auth/logout`, `PATCH /auth/me`, `POST /auth/change-password`, `POST /auth/forgot-password`, `POST /auth/reset-password`, plus account lockout. See `employeeapi/docs/security.md`.

---

## Step 5 — Profile Photo Upload
**Branch:** `epic/refactor-step-6-photo-upload`

- Move photo upload logic from old `ContactController` to `EmployeeController`
- `POST /employees/{id}/photo` — upload profile photo
- `GET /employees/photo/{filename}` — serve photo (public endpoint)
- Store photo path on `Employee.profilePhoto`

**Commit:** `feat: add employee profile photo upload and retrieval`

---

## Step 6 — Leave Management
**Branch:** `epic/refactor-step-7-leave`

- Add `LeaveType` entity + seed data (Annual, Sick, Family Responsibility, Maternity, Parental, Study)
- Add `LeaveBalance` entity + repository + service
  - Auto-create balances when employee is created
  - Recalculate remaining days on approval
- Add `LeaveRequest` entity + repository + service + controller
  - `GET /leave/requests` — all requests (HR/Manager filtered)
  - `GET /leave/requests/my` — current employee's requests
  - `POST /leave/requests` — submit leave request
  - `PATCH /leave/requests/{id}/approve` — Manager/HR approves
  - `PATCH /leave/requests/{id}/reject` — Manager/HR rejects
  - `DELETE /leave/requests/{id}` — cancel (EMPLOYEE, only if PENDING)
- Add `GET /leave/balances/my` — current employee's balances
- Enforce approval chain: Employee → Manager, Manager → HR_ADMIN

**Commit:** `feat: add leave management — types, balances, requests, and approval chain`

---

## Step 7 — Timesheets
**Branch:** `epic/refactor-step-8-timesheets`

- Add `Timesheet` entity + `TimesheetEntry` entity
- Add repository + service + controller
  - `GET /timesheets/my` — current employee's timesheets
  - `POST /timesheets` — create timesheet (DRAFT)
  - `POST /timesheets/{id}/entries` — add entry to timesheet
  - `PATCH /timesheets/{id}/submit` — submit for approval
  - `PATCH /timesheets/{id}/approve` — Manager approves
  - `PATCH /timesheets/{id}/reject` — Manager rejects
  - `GET /timesheets` — all timesheets (Manager/HR filtered by team)

**Commit:** `feat: add timesheet management with entries, submission, and approval`

---

## Step 8 — Documents
**Branch:** `epic/refactor-step-9-documents`

- Add `Document` entity + `DocumentType` enum (`ID`, `CONTRACT`, `CERTIFICATE`, `PAYSLIP`, `OTHER`)
- Add repository + service + controller
  - `POST /documents/upload` — employee uploads document
  - `GET /documents/my` — current employee's documents
  - `GET /documents/{id}/download` — download file
  - `PATCH /documents/{id}/verify` — HR verifies
  - `PATCH /documents/{id}/reject` — HR rejects
  - `GET /documents` — all documents (HR_ADMIN only)

**Commit:** `feat: add document upload, retrieval, and HR verification`

---

## Step 9 — Salary & SA Tax
**Branch:** `epic/refactor-step-10-salary`

- Add `TaxBracket` entity + seed data for current SA tax year (PAYE brackets, UIF, rebates)
- Add `SalaryRecord` entity + repository + service
  - `POST /salary/records` — PAYROLL_ADMIN sets salary
  - `GET /salary/records/{employeeId}` — salary history
- Add `PaySlip` entity + service
  - `POST /salary/payslips/generate` — generate monthly payslip with PAYE, UIF, deductions
  - `GET /salary/payslips/my` — current employee's payslips
  - `GET /salary/payslips/{id}` — single payslip detail
- Add `SalaryIncreaseRequest` entity + service + controller
  - `POST /salary/increase-requests` — Manager submits request
  - `PATCH /salary/increase-requests/{id}/approve` — HR_ADMIN approves
  - `PATCH /salary/increase-requests/{id}/reject` — HR_ADMIN rejects

**Commit:** `feat: add salary records, SA PAYE/UIF tax calculation, payslip generation, and increase requests`

---

## Step 10 — Benefits
**Branch:** `epic/refactor-step-11-benefits`

- Add `BenefitType` entity + seed data (Medical Aid, Pension Fund, Life Cover)
- Add `EmployeeBenefit` entity + `BenefitApplication` entity
- Add repository + service + controller
  - `GET /benefits` — list all available benefit types
  - `GET /benefits/my` — current employee's active benefits
  - `POST /benefits/apply` — employee applies for a benefit
  - `PATCH /benefits/applications/{id}/approve` — HR approves
  - `PATCH /benefits/applications/{id}/reject` — HR rejects

**Commit:** `feat: add benefits catalogue, employee benefit applications, and HR approval`

---

## Step 11 — Performance & KPIs
**Branch:** `epic/refactor-step-12-performance`

- Add `PerformanceCycle` entity + service + controller
  - `POST /performance/cycles` — HR creates a cycle
  - `GET /performance/cycles` — list cycles
- Add `PerformanceGoal` entity + service + controller
  - `POST /performance/goals` — Manager or HR creates goal for employee
  - `GET /performance/goals/my` — employee views their goals
  - `PATCH /performance/goals/{id}/status` — update goal status
- Add `PerformanceReview` entity + service + controller
  - `POST /performance/reviews` — Manager submits review
  - `GET /performance/reviews/my` — employee views their reviews
  - `PATCH /performance/reviews/{id}/acknowledge` — employee acknowledges

**Commit:** `feat: add performance cycles, goals, and reviews with manager submission and employee acknowledgement`

---

## Step 12 — Notifications
**Branch:** `epic/refactor-step-13-notifications`

- Add `Notification` entity + repository + service
- Trigger notifications on key events:
  - Leave submitted → notify Manager
  - Leave approved/rejected → notify Employee
  - Timesheet submitted → notify Manager
  - Salary increase approved → notify Employee
  - Document verified/rejected → notify Employee
- Add controller
  - `GET /notifications/my` — current employee's notifications
  - `PATCH /notifications/{id}/read` — mark as read
  - `PATCH /notifications/read-all` — mark all as read

**Commit:** `feat: add notification system with event-driven triggers across all modules`

---

## Step 13 — Audit Trail
**Branch:** `epic/refactor-step-14-audit`

- Wire `AuditLog` writes into all key service actions (leave approval, salary change, document verification etc.)
- Add controller (HR_ADMIN / SUPER_ADMIN only)
  - `GET /audit-logs` — paginated, filterable by entity type, employee, date range

**Commit:** `feat: wire audit logging across all modules and expose audit log endpoint`

---

## Step 14 — Swagger & API Docs Cleanup
**Branch:** `epic/refactor-step-15-api-docs`

- Add `@Tag`, `@Operation`, `@ApiResponse` annotations to all controllers
- Group endpoints by module in Swagger UI
- Update `application.yml` with correct app title and version

**Commit:** `docs: add full Swagger/OpenAPI annotations across all controllers`

---

## Summary

| Step | Module | Branch |
|------|--------|--------|
| 1 | Base restructure | `epic/refactor-step-2-backend-base` |
| 2 | Organisation structure | `epic/refactor-step-3-org-structure` |
| 3 | Employee CRUD | `epic/refactor-step-4-employee-core` |
| 4 | Auth overhaul | `epic/refactor-step-5-auth` |
| 5 | Photo upload | `epic/refactor-step-6-photo-upload` |
| 6 | Leave management | `epic/refactor-step-7-leave` |
| 7 | Timesheets | `epic/refactor-step-8-timesheets` |
| 8 | Documents | `epic/refactor-step-9-documents` |
| 9 | Salary & SA tax | `epic/refactor-step-10-salary` |
| 10 | Benefits | `epic/refactor-step-11-benefits` |
| 11 | Performance & KPIs | `epic/refactor-step-12-performance` |
| 12 | Notifications | `epic/refactor-step-13-notifications` |
| 13 | Audit trail | `epic/refactor-step-14-audit` |
| 14 | API docs | `epic/refactor-step-15-api-docs` |
