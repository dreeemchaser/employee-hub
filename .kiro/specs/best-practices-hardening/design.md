# Best-Practices Hardening — Design

Grounded in a code audit this session (context-gatherer + direct reads). Key facts:
- Spring Boot **3.5.13**, Java **21**, Postgres **15**. `pom.xml` has JPA + postgresql driver; **no Flyway, no Testcontainers, no ArchUnit, no spring-security-test, no H2**.
- `Employee.password` **already** `@JsonIgnore` (no leak). `Employee.idNumber` is **not** ignored (PII exposure).
- Only two response DTOs exist: `MeResponse`, `EmployeeResponse` (both constructor-takes-entity, session-open materialization).
- Persistence: `ddl-auto=update` (all 3 envs), `spring.sql.init.mode=always`, `defer-datasource-initialization=true`; seed is `data.sql` (~340 lines, idempotent); **no `DataSeeder.java`**.
- 22 entities / tables (full list in Slice B).

---

## Slice A — DTO-response refactor

### Pattern (follow `EmployeeResponse` / `MeResponse` exactly)
- `@Getter` class, `final` fields, single constructor `public XResponse(XEntity e)` that reads everything **while the session is open** (the service method building the DTO is the safe place).
- Flatten associations to ids + display names (e.g. `employeeId` + `employeeName`), never nest the raw entity.
- Static list helper where useful: `static List<XResponse> from(List<X> xs)`.
- Mapping lives in the **service** (steering: no mapping libs; controllers stay thin). Services already run in an open-session/`@Transactional`(read) context.

### DTOs to create (by priority tier from requirements)

**Tier 1 — the 5 unmasked entities (highest risk):**
| Entity | New DTO | Embeds (flatten to id+name) |
|---|---|---|
| `BenefitApplication` | `BenefitApplicationResponse` | `employee`, `reviewedBy`, `benefitType` |
| `EmployeeBenefit` | `EmployeeBenefitResponse` | `employee`, `benefitType` |
| `SalaryIncreaseRequest` | `SalaryIncreaseRequestResponse` | `employee`, `requestedBy`, `reviewedBy` |
| `PerformanceGoal` | `PerformanceGoalResponse` | `employee`, `createdBy` |
| `PerformanceReview` | `PerformanceReviewResponse` | `employee`, `reviewer`, (+3rd employee field — confirm in code) |

**Tier 2 — raw `Employee` endpoints:** reuse existing `EmployeeResponse` in `EmployeeController.getById/create/update/updateStatus/uploadPhoto`. No new DTO. (getAll already uses it.)

**Tier 3 — already-masked entities (lower risk, still raw entities → per A1):**
| Entity | New DTO |
|---|---|
| `LeaveRequest` | `LeaveRequestResponse` (embeds employee, approvedBy, leaveType) |
| `LeaveBalance` | `LeaveBalanceResponse` |
| `Timesheet` (+`TimesheetEntry`) | `TimesheetResponse` (+ nested `TimesheetEntryResponse`) |
| `Document` | `DocumentResponse` (employee, uploadedBy, verifiedBy) |
| `SalaryRecord` | `SalaryRecordResponse` |
| `PaySlip` | `PaySlipResponse` |
| `Notification` | `NotificationResponse` (recipient) |
| `AuditLog` | `AuditLogResponse` (performedBy) |

**PII:** add `@JsonIgnore` to `Employee.idNumber` (defence-in-depth) and omit from all DTOs. Confirm no screen needs it; if a specific HR screen does, expose via a dedicated admin-only DTO field, not the general one.

### Controller changes
Each affected controller method changes its return generic from the entity to the DTO; the service either returns the DTO or the controller maps via `new XResponse(entity)` / `XResponse.from(list)`. Prefer mapping in the service to keep controllers thin (matches how `getAll` → `EmployeeResponse` works today).

### Frontend impact (A5) — verify before changing
- `hrdashboard` reads nested fields like `r.employee?.firstName` (raw shape). New DTOs should expose `employeeName` (or keep an `employee` object with `firstName`/`lastName`) — **pick the shape that minimizes frontend churn**, decide per-DTO by grepping the consuming code. Any change mirrored in the same PR.
- `employeehub` uses `normaliseEmployee()` on the employee shape — reusing `EmployeeResponse` (already used by getAll) means its detail/create/update flows get the same shape they already normalize. Low risk.

### Slice A verification
- `mvnw verify` (105+ tests) green.
- Both frontend suites green; manual/CI check that approvals, benefits, performance, salary, notifications, audit pages render.
- Grep: no controller returns `employeehub.domain.*`.

---

## Slice B — Flyway baseline + `validate`

### Dependencies (B1)
Add to `pom.xml` (versions managed by Boot 3.5.13 BOM — omit explicit `<version>` so Boot pins them):
```xml
<dependency><groupId>org.flywaydb</groupId><artifactId>flyway-core</artifactId></dependency>
<dependency><groupId>org.flywaydb</groupId><artifactId>flyway-database-postgresql</artifactId></dependency>
```

### The 22-table schema the baseline must cover (B2)
`employees, departments, teams, leave_types, leave_requests, leave_balances, timesheets, timesheet_entries, documents, salary_records, salary_increase_requests, pay_slips, tax_brackets, benefit_types, benefit_applications, employee_benefits, performance_cycles, performance_goals, performance_reviews, notifications, audit_logs, refresh_tokens, password_reset_tokens`

PK strategies to reproduce faithfully:
- **UUID `text` PKs:** employees, leave_requests, leave_balances, timesheets, documents, salary_records, salary_increase_requests, pay_slips, benefit_applications, employee_benefits, performance_cycles/goals/reviews, notifications, audit_logs, refresh_tokens, password_reset_tokens.
- **`Long` identity PKs:** departments, teams, leave_types, tax_brackets, benefit_types, timesheet_entries (`entryId`).
- Special columns: `employees.failed_login_attempts integer default 0 not null`, `employees.locked_until timestamp`, `refresh_tokens.token_hash unique`, all the `@Column(unique=…)`/`nullable=false` constraints, enum-as-string columns, `@CreationTimestamp`/`@UpdateTimestamp`.

### How to author V1 without hand-guessing (mitigates baseline-drift risk)
1. On a **throwaway** empty Postgres 15, boot the app **once** with current `ddl-auto=update` and `hibernate.hbm2ddl` script generation, OR set `spring.jpa.properties.jakarta.persistence.schema-generation.scripts.action=create` to dump the exact DDL Hibernate produces.
2. Capture that DDL as `V1__baseline_schema.sql` (clean it: drop Hibernate's `create sequence` noise only if not used; keep everything `validate` will check).
3. Boot a second fresh DB with `ddl-auto=validate` + Flyway running V1 — if validate passes, the baseline matches. Iterate until clean. **This capture-then-validate loop is the crux of Slice B and must be done on throwaway DBs, never a real one.**

### Seed relocation (B3) — no Java seeder exists
Current `data.sql` = reference data (departments, teams, leave_types, tax_brackets, benefit_types) + 10 seeded employees + leave_balances. Options:
- **Chosen approach:** `V2__seed_reference_data.sql` = the reference tables + admin/seed employees, ported from `data.sql` (keep the `INSERT ... WHERE NOT EXISTS` idempotency, or rely on Flyway's run-once guarantee and drop the guards). Repeatable seed (`R__`) is an alternative if we want re-application, but run-once V2 is simpler and matches "seed on first boot".
- Delete `data.sql` (or neuter `spring.sql.init`) so seeding happens exactly once via Flyway.
- Keep the bcrypt hashes for `Admin@1234`/`Employee@1234` identical so existing docs/logins hold.

### Config flips (B4) — across all three files
| Setting | Now | After |
|---|---|---|
| `spring.jpa.hibernate.ddl-auto` | `update` | `validate` |
| `spring.jpa.generate-ddl` | `true` | remove (or `false`) |
| `spring.sql.init.mode` | `always` | `never` |
| `spring.flyway.baseline-on-migrate` | — | `true` (B5) |
| `spring.flyway.enabled` | — | `true` (default) |
Files: `application.yml`, `docker-compose.yml` (env `SPRING_JPA_HIBERNATE_DDL_AUTO`), `docker-compose.prod.yml` (same env). `defer-datasource-initialization` becomes irrelevant once sql.init is off — remove.

### Existing-DB adoption (B5)
`baseline-on-migrate=true` + `baseline-version=1` lets Flyway adopt an already-populated DB (dev volumes, prod) at V1 without re-running it, then apply V2+. Document the one-time behavior in `deployment.md`. For a truly fresh volume, Flyway runs V1 then V2 normally.

### Slice B verification (must do on wiped volume)
1. `docker-compose down -v` then `up --build` → app boots, Flyway logs V1+V2, admin login works.
2. Confirm Hibernate applies **zero** changes (validate passes) — check startup logs.
3. Simulate existing DB: boot old-style once (update), then switch to Flyway build → baseline-on-migrate adopts it, no data loss.
4. `mvnw verify` + CI (real Postgres) green.

---

## Docs to update on completion
- `tech.md` steering — correct Known Issue #1 (password already fixed) and #2 (now resolved by Flyway); fix stale `DataSeeder`/port refs noted by the audit.
- `structure.md` — remove `DataSeeder.java` from the documented package layout (it doesn't exist).
- `workflow.md` — add "schema changes go through Flyway `V{n}__` migrations, never `ddl-auto`".
- `employeeapi/docs/architecture.md` / `deployment.md` — Flyway migration flow, baseline-on-migrate one-time step.
- `employeeapi/docs/api.md` + `docs/api-contract.md` — response shapes now DTOs (if any shape changed vs. the raw entity).
- `next-gen-features.md` — add a Progress Log row when each slice merges.

## Open decisions to confirm at build time (tomorrow)
1. **DTO shape for embedded employees** — flat `employeeName` string vs. small nested `{id, firstName, lastName}` object. Decide per-DTO by what the frontend already reads (minimize churn). Default: match current nested shape where a frontend reads nested fields, flat elsewhere.
2. **Seed as run-once `V2__` vs. repeatable `R__`** — default run-once V2 (matches first-boot seeding).
3. **`idNumber`** — confirm no HR screen needs it before `@JsonIgnore`-ing; if one does, dedicated admin DTO field.
