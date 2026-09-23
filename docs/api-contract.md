# Employee Hub — API Contract

**Base URL:** `/` (no version prefix — controllers are mounted at the root, e.g. `/auth`, `/employees`)
**Auth:** All endpoints require `Authorization: Bearer <accessToken>` unless marked `[PUBLIC]`
**Content-Type:** `application/json`

> ⚠️ **Partly aspirational.** This contract documents the intended/target API and includes some endpoints and sections that are not implemented (e.g. any WebSocket/real-time section, and a few verbs/paths differ from the live controllers). The **live controllers**, `employeeapi/docs/api.md`, and Swagger (`/swagger-ui/index.html`) are the source of truth. The Authentication and core CRUD tables below have been reconciled with the current code; deeper sections may still drift.

---

## Roles

| Role | Description |
|------|-------------|
| `SUPER_ADMIN` | Full system access |
| `HR_ADMIN` | Manages all employees and approvals |
| `PAYROLL_ADMIN` | Salary, tax and benefits management |
| `MANAGER` | Manages their team |
| `EMPLOYEE` | Self-service access to own data |

> A `MANAGER` is also an `EMPLOYEE` — they have access to all `EMPLOYEE` endpoints plus their own `MANAGER` endpoints.

---

## 1. Authentication

| Method | Endpoint | Access | Description |
|--------|----------|--------|-------------|
| POST | `/auth/login` | PUBLIC | Login. Returns `{ accessToken, refreshToken }` (15-min access + 7-day rotating refresh). Returns `423 Locked` (with `Retry-After` + `retryAfterSeconds`) after repeated failed attempts |
| POST | `/auth/refresh` | PUBLIC | Exchange a refresh token for a new `{ accessToken, refreshToken }` pair (rotates; `401` if invalid/expired/revoked) |
| POST | `/auth/logout` | PUBLIC | Revoke a refresh token (idempotent) |
| GET | `/auth/me` | ALL | Get own profile |
| PATCH | `/auth/me` | ALL | Update own profile |
| POST | `/auth/change-password` | ALL | Change own password |
| POST | `/auth/forgot-password` | PUBLIC | Request a password reset link (generic response, no enumeration) |
| POST | `/auth/reset-password` | PUBLIC | Reset password with a single-use, expiring token |

> There is **no** `/auth/register`. New accounts are created via `POST /employees` (HR/admin) or seeded on first boot. Self-service profile lives under `/auth/me` (not `/employees/me`).

---

## 2. Employees

| Method | Endpoint | Access | Description |
|--------|----------|--------|-------------|
| GET | `/employees` | HR_ADMIN, SUPER_ADMIN | Get all employees |
| GET | `/employees/{id}` | ALL | Get employee by ID |
| POST | `/employees` | HR_ADMIN, SUPER_ADMIN | Create employee |
| PUT | `/employees/{id}` | HR_ADMIN, SUPER_ADMIN | Update employee |
| PATCH | `/employees/{id}/status` | HR_ADMIN, SUPER_ADMIN | Update employment status |
| POST | `/employees/{id}/offboard` | HR_ADMIN, SUPER_ADMIN | Offboard employee: terminate, cancel pending leave requests, deactivate active benefits |
| DELETE | `/employees/{id}` | HR_ADMIN, SUPER_ADMIN | Delete employee |
| POST | `/employees/{id}/photo` | HR_ADMIN, SUPER_ADMIN | Upload employee photo |
| GET | `/employees/photo/{filename}` | PUBLIC | Serve a profile photo |

> `GET /employees` supports filtering via query params (`departmentId`, `teamId`, `status`) and pagination (`Pageable`) rather than dedicated `/department/{id}` / `/team/{id}` / `/manager/{id}` paths. Own-profile read/update is `GET`/`PATCH /auth/me` (see Authentication), not `/employees/me`.

---

## 3. Organisation

### Departments

| Method | Endpoint | Access | Description |
|--------|----------|--------|-------------|
| GET | `/departments` | ALL | Get all departments |
| GET | `/departments/{id}` | ALL | Get department by ID |
| POST | `/departments` | HR_ADMIN, SUPER_ADMIN | Create department |
| PUT | `/departments/{id}` | HR_ADMIN, SUPER_ADMIN | Update department |
| DELETE | `/departments/{id}` | SUPER_ADMIN | Delete department |

### Teams

| Method | Endpoint | Access | Description |
|--------|----------|--------|-------------|
| GET | `/teams` | ALL | Get all teams |
| GET | `/teams/{id}` | ALL | Get team by ID |
| GET | `/teams/department/{departmentId}` | ALL | Get teams by department |
| POST | `/teams` | HR_ADMIN, SUPER_ADMIN | Create team |
| PUT | `/teams/{id}` | HR_ADMIN, SUPER_ADMIN | Update team |
| DELETE | `/teams/{id}` | SUPER_ADMIN | Delete team |

---

## 4. Leave

### Leave Types

| Method | Endpoint | Access | Description |
|--------|----------|--------|-------------|
| GET | `/leave/types` | ALL | Get all leave types |
| GET | `/leave/types/{id}` | ALL | Get leave type by ID |
| POST | `/leave/types` | HR_ADMIN, SUPER_ADMIN | Create leave type |
| PUT | `/leave/types/{id}` | HR_ADMIN, SUPER_ADMIN | Update leave type |
| DELETE | `/leave/types/{id}` | SUPER_ADMIN | Delete leave type |

### Leave Balances

| Method | Endpoint | Access | Description |
|--------|----------|--------|-------------|
| GET | `/leave/balances/me` | ALL | Get own leave balances |
| GET | `/leave/balances/{employeeId}` | MANAGER, HR_ADMIN, SUPER_ADMIN | Get employee leave balances |
| GET | `/leave/balances/forecast` | ALL (authenticated) | Project own remaining leave at cycle end, factoring in pending requests |

### Leave Requests

| Method | Endpoint | Access | Description |
|--------|----------|--------|-------------|
| GET | `/leave/requests/me` | ALL | Get own leave requests |
| GET | `/leave/requests/{id}` | ALL | Get leave request by ID |
| GET | `/leave/requests/pending` | MANAGER, HR_ADMIN, SUPER_ADMIN | Get all pending requests |
| GET | `/leave/requests/team/{teamId}` | MANAGER, HR_ADMIN, SUPER_ADMIN | Get team leave requests |
| POST | `/leave/requests` | ALL | Submit leave request (validates balance automatically; sick leave over 3 working days requires `documentationConfirmed: true` in the body) |
| PUT | `/leave/requests/{id}/approve` | MANAGER, HR_ADMIN | Approve leave request |
| PUT | `/leave/requests/{id}/reject` | MANAGER, HR_ADMIN | Reject leave request |
| PUT | `/leave/requests/{id}/cancel` | ALL | Cancel own leave request |

### Team Leave Calendar

| Method | Endpoint | Access | Description |
|--------|----------|--------|-------------|
| GET | `/leave/types` | ALL (authenticated) | List leave types for filters and apply forms |
| GET | `/leave/calendar` | EMPLOYEE, MANAGER, HR_ADMIN, SUPER_ADMIN | Approved leave for a given month, filterable by leave type / employee / department / team |
| GET | `/leave/conflicts` | EMPLOYEE, MANAGER, HR_ADMIN, SUPER_ADMIN | Approved team leave overlapping a `startDate`/`endDate` range, excluding the caller (busy-period preview) |

---

## 5. Salary

### Salary Records

| Method | Endpoint | Access | Description |
|--------|----------|--------|-------------|
| GET | `/salary/records/me` | ALL | Get own salary history |
| GET | `/salary/records/{employeeId}` | PAYROLL_ADMIN, HR_ADMIN, SUPER_ADMIN | Get employee salary history |
| POST | `/salary/records` | PAYROLL_ADMIN, SUPER_ADMIN | Create salary record |
| PUT | `/salary/records/{id}` | PAYROLL_ADMIN, SUPER_ADMIN | Update salary record |

### Pay Slips

| Method | Endpoint | Access | Description |
|--------|----------|--------|-------------|
| GET | `/salary/payslips/me` | ALL | Get own payslips |
| GET | `/salary/payslips/me/{month}` | ALL | Get own payslip by month |
| GET | `/salary/payslips/{employeeId}` | PAYROLL_ADMIN, HR_ADMIN, SUPER_ADMIN | Get employee payslips |
| POST | `/salary/payslips/generate` | PAYROLL_ADMIN, SUPER_ADMIN | Generate payslip |

### Tax Brackets

| Method | Endpoint | Access | Description |
|--------|----------|--------|-------------|
| GET | `/salary/tax-brackets` | PAYROLL_ADMIN, SUPER_ADMIN | Get all tax brackets |
| GET | `/salary/tax-brackets/{taxYear}` | PAYROLL_ADMIN, SUPER_ADMIN | Get brackets by tax year |
| POST | `/salary/tax-brackets` | SUPER_ADMIN | Create tax bracket |
| PUT | `/salary/tax-brackets/{id}` | SUPER_ADMIN | Update tax bracket |
| GET | `/salary/tax-calculator` | ALL | Calculate own PAYE |

### Salary Increase Requests

| Method | Endpoint | Access | Description |
|--------|----------|--------|-------------|
| GET | `/salary/increase-requests` | HR_ADMIN, SUPER_ADMIN | Get all increase requests |
| GET | `/salary/increase-requests/team/{teamId}` | MANAGER | Get team increase requests |
| GET | `/salary/increase-requests/{id}` | MANAGER, HR_ADMIN, SUPER_ADMIN | Get request by ID |
| POST | `/salary/increase-requests` | MANAGER | Submit increase request |
| PUT | `/salary/increase-requests/{id}/approve` | HR_ADMIN, SUPER_ADMIN | Approve increase request |
| PUT | `/salary/increase-requests/{id}/reject` | HR_ADMIN, SUPER_ADMIN | Reject increase request |

---

## 6. Benefits

### Benefit Types

| Method | Endpoint | Access | Description |
|--------|----------|--------|-------------|
| GET | `/benefits/types` | ALL | Get all benefit types |
| GET | `/benefits/types/{id}` | ALL | Get benefit type by ID |
| POST | `/benefits/types` | HR_ADMIN, SUPER_ADMIN | Create benefit type |
| PUT | `/benefits/types/{id}` | HR_ADMIN, SUPER_ADMIN | Update benefit type |
| DELETE | `/benefits/types/{id}` | SUPER_ADMIN | Delete benefit type |

### Employee Benefits

| Method | Endpoint | Access | Description |
|--------|----------|--------|-------------|
| GET | `/benefits/me` | ALL | Get own enrolled benefits |
| GET | `/benefits/{employeeId}` | HR_ADMIN, SUPER_ADMIN | Get employee benefits |

### Benefit Applications

| Method | Endpoint | Access | Description |
|--------|----------|--------|-------------|
| GET | `/benefits/applications/me` | ALL | Get own applications |
| GET | `/benefits/applications/pending` | HR_ADMIN, SUPER_ADMIN | Get all pending applications |
| POST | `/benefits/applications` | ALL | Apply for a benefit |
| PUT | `/benefits/applications/{id}/approve` | HR_ADMIN, SUPER_ADMIN | Approve application |
| PUT | `/benefits/applications/{id}/reject` | HR_ADMIN, SUPER_ADMIN | Reject application |

---

## 7. Timesheets

| Method | Endpoint | Access | Description |
|--------|----------|--------|-------------|
| GET | `/timesheets/me` | ALL | Get own timesheets |
| GET | `/timesheets/me/{id}` | ALL | Get own timesheet by ID |
| GET | `/timesheets/pending` | MANAGER, HR_ADMIN, SUPER_ADMIN | Get all pending timesheets |
| GET | `/timesheets/team/{teamId}` | MANAGER, HR_ADMIN, SUPER_ADMIN | Get team timesheets |
| GET | `/timesheets/{id}` | MANAGER, HR_ADMIN, SUPER_ADMIN | Get timesheet by ID |
| POST | `/timesheets` | ALL | Create weekly timesheet |
| PUT | `/timesheets/{id}` | ALL | Update own draft timesheet |
| PUT | `/timesheets/{id}/submit` | ALL | Submit timesheet for approval |
| PUT | `/timesheets/{id}/approve` | MANAGER, HR_ADMIN | Approve timesheet |
| PUT | `/timesheets/{id}/reject` | MANAGER, HR_ADMIN | Reject timesheet |
| POST | `/timesheets/{id}/entries` | ALL | Add entry to timesheet |
| PUT | `/timesheets/{id}/entries/{entryId}` | ALL | Update timesheet entry |
| DELETE | `/timesheets/{id}/entries/{entryId}` | ALL | Delete timesheet entry |

---

## 8. Performance

### Performance Cycles

| Method | Endpoint | Access | Description |
|--------|----------|--------|-------------|
| GET | `/performance/cycles` | ALL | Get all cycles |
| GET | `/performance/cycles/{id}` | ALL | Get cycle by ID |
| POST | `/performance/cycles` | HR_ADMIN, SUPER_ADMIN | Create cycle |
| PUT | `/performance/cycles/{id}` | HR_ADMIN, SUPER_ADMIN | Update cycle |
| PUT | `/performance/cycles/{id}/close` | HR_ADMIN, SUPER_ADMIN | Close cycle |

### Performance Reviews

| Method | Endpoint | Access | Description |
|--------|----------|--------|-------------|
| GET | `/performance/reviews/me` | ALL | Get own reviews |
| GET | `/performance/reviews/{id}` | ALL | Get review by ID |
| GET | `/performance/reviews/cycle/{cycleId}` | MANAGER, HR_ADMIN, SUPER_ADMIN | Get reviews by cycle |
| GET | `/performance/reviews/team/{teamId}` | MANAGER, HR_ADMIN, SUPER_ADMIN | Get team reviews |
| POST | `/performance/reviews` | MANAGER | Create review |
| PUT | `/performance/reviews/{id}` | MANAGER | Update review |
| PUT | `/performance/reviews/{id}/submit` | MANAGER | Submit review |
| PUT | `/performance/reviews/{id}/acknowledge` | ALL | Employee acknowledges review |

### Performance Goals

| Method | Endpoint | Access | Description |
|--------|----------|--------|-------------|
| GET | `/performance/goals/me` | ALL | Get own goals |
| GET | `/performance/goals/{id}` | ALL | Get goal by ID |
| GET | `/performance/goals/cycle/{cycleId}` | MANAGER, HR_ADMIN, SUPER_ADMIN | Get goals by cycle |
| POST | `/performance/goals` | MANAGER, HR_ADMIN | Create goal |
| PUT | `/performance/goals/{id}` | MANAGER, HR_ADMIN | Update goal |
| PUT | `/performance/goals/{id}/status` | ALL | Update goal status |
| DELETE | `/performance/goals/{id}` | MANAGER, HR_ADMIN | Delete goal |

---

## 9. Documents

| Method | Endpoint | Access | Description |
|--------|----------|--------|-------------|
| GET | `/documents/me` | ALL | Get own documents |
| GET | `/documents/{id}` | ALL | Get document by ID |
| GET | `/documents/employee/{employeeId}` | HR_ADMIN, SUPER_ADMIN | Get employee documents |
| GET | `/documents/pending` | HR_ADMIN, SUPER_ADMIN | Get all pending documents |
| POST | `/documents` | ALL | Upload document |
| PUT | `/documents/{id}/verify` | HR_ADMIN, SUPER_ADMIN | Verify document |
| PUT | `/documents/{id}/reject` | HR_ADMIN, SUPER_ADMIN | Reject document |
| DELETE | `/documents/{id}` | ALL | Delete own document |
| GET | `/documents/{id}/download` | ALL | Download document file |

---

## 10. Notifications

### REST

| Method | Endpoint | Access | Description |
|--------|----------|--------|-------------|
| GET | `/notifications/me` | ALL | Get own notifications |
| PUT | `/notifications/{id}/read` | ALL | Mark notification as read |
| PUT | `/notifications/read-all` | ALL | Mark all as read |
| DELETE | `/notifications/{id}` | ALL | Delete notification |

### WebSocket

| Channel | Direction | Description |
|---------|-----------|-------------|
| `/ws` | Connect | WebSocket connection endpoint |
| `/user/{employeeId}/notifications` | Subscribe | Receive real-time notifications |
| `/app/notifications` | Publish | Send notification (server-side only) |

**Flow:**
```
Employee submits request
        ↓
Backend processes and saves
        ↓
Backend pushes via WebSocket
        ↓
Manager receives real-time alert on Dashboard
        ↓
Manager approves/rejects
        ↓
Employee receives real-time alert on Employee Hub
```

---

## 11. Audit Log

| Method | Endpoint | Access | Description |
|--------|----------|--------|-------------|
| GET | `/audit-logs` | SUPER_ADMIN | Get all audit logs |
| GET | `/audit-logs/employee/{employeeId}` | HR_ADMIN, SUPER_ADMIN | Get logs by employee |
| GET | `/audit-logs/entity/{entityType}/{entityId}` | HR_ADMIN, SUPER_ADMIN | Get logs by entity |

---

## Summary

| Module | Endpoints |
|--------|-----------|
| Auth | 5 |
| Employees | 12 |
| Organisation | 11 |
| Leave | 16 |
| Salary | 16 |
| Benefits | 12 |
| Timesheets | 13 |
| Performance | 18 |
| Documents | 9 |
| Notifications | 4 + WebSocket |
| Audit Log | 3 |
| **Total** | **119** |
