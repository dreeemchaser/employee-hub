# Best-Practices Hardening — Requirements

**Source:** Tech-debt from `tech.md` steering (Known Issues #1, #2) + `coding-best-practices.md` mandates. Not a numbered item in `next-gen-features.md`, but adjacent to gap B (correctness/hygiene).
**Status:** Planning (code work deferred — planned for next session).
**Scope of THIS spec:** two slices — (A) DTO-response refactor, then (B) Flyway baseline + `validate`. Test infrastructure (WebMvcTest / Testcontainers / ArchUnit) is explicitly a **follow-up spec**, not here.

## Important correction (verified in code)

The steering docs are **stale** on two points, confirmed by reading the code this session:

1. **`tech.md` Known Issue #1 ("`Employee.password` has no `@JsonIgnore`") is WRONG.** `Employee.password` **already carries `@JsonIgnore`** (`domain/Employee.java` line ~45, with the import present). Jackson field-level `@JsonIgnore` is global, so the bcrypt hash does **not** serialize from any endpoint. **There is no password-leak to fix.** This spec does not include one; instead it fixes the *broader* raw-entity-return problem.
2. **`DataSeeder.java` does not exist.** Both `structure.md` and the `data.sql` header comment reference it, but there is no Java seeder. All startup seeding is done by `data.sql` (idempotent `INSERT ... WHERE NOT EXISTS`). This materially affects the Flyway slice (no Java seeder to fall back on).

A task in each slice updates the stale steering so the docs match reality.

---

## Slice A — DTO-response refactor

### Problem
Most controllers return **raw JPA entities** inside `ApiResponse<T>`, violating `coding-best-practices.md` ("`T` must always be a DTO, never a domain entity"). Concrete harms found in code:
- **PII leak:** `Employee.idNumber` (South African ID number) has no `@JsonIgnore` and serializes in full wherever a raw `Employee` is returned. Internal fields (`failedLoginAttempts`, `lockedUntil`, `createdAt`/`updatedAt`) also leak.
- **`LazyInitializationException`-mid-serialization risk:** five entities embed a lazy `Employee` with **no** `@JsonIgnoreProperties` mask — `BenefitApplication`, `EmployeeBenefit`, `SalaryIncreaseRequest`, `PerformanceGoal`, `PerformanceReview`. This is the same class of bug that `EmployeeResponse` was created to fix for `/employees` (see its Javadoc).
- **API/schema coupling:** the wire contract is whatever the entity happens to expose.

### Requirements
- **A1** — No controller returns a raw domain entity. Every `ApiResponse<T>` (and `Page<T>` / `List<T>`) resolves `T` to a response DTO.
- **A2** — Response DTOs fully materialize needed fields **inside the open session** (constructor-takes-entity pattern, exactly like `MeResponse`/`EmployeeResponse`), so no lazy proxy escapes to Jackson. No manual mapping libraries (MapStruct/ModelMapper) — steering forbids them; hand-write mapping (constructor or static factory).
- **A3** — DTOs omit sensitive/internal fields: never expose `password` (already `@JsonIgnore`), `idNumber` (add `@JsonIgnore` to the entity field as defence-in-depth AND omit from DTOs), lockout internals, or raw audit timestamps unless a screen needs them.
- **A4** — The existing `EmployeeResponse` and `MeResponse` are the reference shape. Reuse `EmployeeResponse` for the remaining raw-`Employee` endpoints (`getById`, `create`, `update`, `updateStatus`, `uploadPhoto`) instead of inventing a parallel DTO.
- **A5** — Frontend compatibility: DTO field names/shape must match what the two React apps already read (`res.data.data...`). Where a frontend reads a nested entity field (e.g. `r.employee?.firstName` in hrdashboard), the DTO must preserve an equivalent shape. Any shape change must be mirrored in the consuming app in the same PR.
- **A6** — Swagger annotations (`@Operation`) preserved; response types update to the DTOs so the generated schema reflects the real contract.
- **A7** — No security-rule changes. No behavioral change to what data a role can see — only the serialization shape/field set.
- **A8** — Existing service-layer unit tests stay green; DTO mapping covered where it carries logic (name concatenation, null-safety).

### Priority order within Slice A
1. The 5 unmasked entities (highest `LazyInitializationException` + full-graph-leak risk): `BenefitApplication`, `EmployeeBenefit`, `SalaryIncreaseRequest`, `PerformanceGoal`, `PerformanceReview`.
2. Raw-`Employee` endpoints in `EmployeeController` (reuse `EmployeeResponse`).
3. Remaining entity-returning controllers (`LeaveController`, `TimesheetController`, `DocumentController`, `SalaryController` records/payslips, `NotificationController`, `AuditLogController`) — these already mask via `@JsonIgnoreProperties`, so lower risk, but still raw entities per A1.
4. Add `@JsonIgnore` to `Employee.idNumber` (PII).

### Slice A — Acceptance criteria
1. `grep` for controller methods returning a `domain.*` type finds none (all return DTOs).
2. `Employee.idNumber` no longer appears in any JSON response.
3. The 5 previously-unmasked entities are returned as DTOs; no `LazyInitializationException` under a closed-session serialization test (or manual verification).
4. Both frontends still render employees, approvals, benefits, performance, salary, notifications, audit logs correctly (fields they read are present).
5. `mvnw verify` green; both frontend suites green.

---

## Slice B — Flyway baseline + `validate` (one-way door — sequence carefully)

### Problem
`spring.jpa.hibernate.ddl-auto=update` in **all three** environments (local `application.yml`, `docker-compose.yml`, AND `docker-compose.prod.yml`). `tech.md` #2 flags this as unsafe for production — and CD now deploys to prod, so this is live risk. There is no migration tool. Schema is whatever Hibernate infers; `data.sql` seeds it (`spring.sql.init.mode=always`, `defer-datasource-initialization=true` so Hibernate builds tables before the seed runs).

### Requirements
- **B1** — Add Flyway: `flyway-core` + `flyway-database-postgresql`, pinned to the versions managed by Spring Boot 3.5.13's BOM (no open ranges).
- **B2** — Author a **V1 baseline migration** covering all **22 tables** the entities map to (list in design.md). Must match the Hibernate-generated schema exactly so `validate` passes against existing databases. Mind the mixed PK strategies: UUID-`text` PKs on most, `Long`/identity PKs on `Department`, `Team`, `LeaveType`, `LeaveBalance`, `TaxBracket`, `BenefitType`, `TimesheetEntry`; and the later-added `employees.failed_login_attempts` (`integer default 0`) and `employees.locked_until` columns.
- **B3** — Fold the `data.sql` seed into Flyway. Since there is **no Java `DataSeeder`**, the seed must survive: either a Flyway seed migration (`V2__seed_reference_data.sql` for reference data + admin) or a repeatable/callback. Keep it idempotent-safe. Decide baseline-vs-seed split in design.
- **B4** — Flip `spring.jpa.hibernate.ddl-auto` → `validate` and disable `spring.sql.init` (set `mode: never`) across `application.yml`, `docker-compose.yml`, and `docker-compose.prod.yml`. Remove `generate-ddl: true`.
- **B5** — Existing databases (dev volumes, any prod) must not break: use Flyway **baseline-on-migrate** so an already-populated DB is adopted at V1 rather than re-created. Document the one-time baseline step.
- **B6** — Fresh `docker-compose up` (empty volume) must produce a working, seeded stack identical to today's behavior, now via Flyway instead of Hibernate+data.sql.
- **B7** — CI's Docker-build validation and the `mvnw verify` job must pass with `validate` (they spin a real Postgres, so migrations run there).
- **B8** — Future schema changes (e.g. the deferred test-infra spec, or B.6 data-model features) add `V{n}__*.sql` migrations — no more `ddl-auto` edits. Note this in `workflow.md` steering.

### Slice B — Acceptance criteria
1. `flyway-core` + `flyway-database-postgresql` in `pom.xml`, Boot-managed versions.
2. Fresh empty-volume `docker-compose up --build` boots, runs V1 + seed, app starts, admin login works (`admin@employeehub.com` / `Admin@1234`).
3. `ddl-auto=validate` everywhere; app starts with **no schema changes applied by Hibernate** (validate passes → baseline matches entities).
4. An existing populated DB adopts the baseline via baseline-on-migrate without data loss.
5. `spring.sql.init` disabled; `data.sql` no longer the seed mechanism (moved into Flyway).
6. `mvnw verify` + CI green.

### Slice B — Risks
- **Baseline drift:** the hand-authored V1 must exactly match Hibernate's generated DDL or `validate` fails on boot. Mitigation: generate the baseline by dumping the schema Hibernate produces on a fresh DB, then check it in (design.md details the capture method).
- **Seed relocation:** no Java seeder fallback — if the Flyway seed is wrong, a fresh stack comes up empty and login fails. Verify on a wiped volume before merge.
- **One-way door:** once `validate` ships, `update` is gone; any missed column becomes a boot failure. Land Slice A first (stable base), then B in its own PR with a wiped-volume smoke test.

---

## Out of scope (this spec)
- WebMvcTest / Testcontainers / ArchUnit — **follow-up spec** (`best-practices-testing`).
- Any new product feature (B.4 API client, B.6 data-model gaps).
- Changing role/authorization behavior.
- BusinessRuleException or new exception types (steering forbids without explicit ask).

## Suggested delivery
Two PRs, sequenced: **PR1 = Slice A (DTO refactor)**, then **PR2 = Slice B (Flyway)** on top of a green master. Do not combine — Flyway needs an isolated, wiped-volume verification.
