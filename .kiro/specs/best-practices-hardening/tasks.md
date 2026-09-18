# Best-Practices Hardening — Tasks

Two sequenced slices, **two separate PRs**. Land Slice A (DTO refactor) first on green master, then Slice B (Flyway) on top. Do NOT combine. Branch per slice off master. Verify with `mvnw verify` (JDK 21: `$env:JAVA_HOME="C:\Program Files\Java\jdk-21.0.12.1"`) and the frontend suites (Node via `npm.cmd`). Do not commit/push unless asked.

> Status: **PLANNING COMPLETE — implementation deferred to next session.** No code written yet.

## Pre-flight (confirm before coding)
- [ ] 0a. Confirm DTO embedded-employee shape convention (flat `employeeName` vs nested object) by grepping each consuming frontend usage. Default: minimize frontend churn.
- [ ] 0b. Confirm `idNumber` isn't required by any HR screen before `@JsonIgnore`-ing it.
- [ ] 0c. Confirm seed-as-`V2__` (run-once) vs repeatable `R__`. Default V2.

---

## Slice A — DTO-response refactor  (PR1: `feature/dto-response-refactor`)

### Tier 1 — the 5 unmasked entities (highest risk)
- [ ] A1. `BenefitApplicationResponse` + wire `BenefitController` apply/approve/reject; map in `BenefitService`.
- [ ] A2. `EmployeeBenefitResponse` + wire `BenefitController.getMy`.
- [ ] A3. `SalaryIncreaseRequestResponse` + wire the increase-request endpoints in `SalaryController`.
- [ ] A4. `PerformanceGoalResponse` + `PerformanceReviewResponse` + wire `PerformanceController` (confirm the 3rd Employee field on review).
- [ ] A5. Verify no `LazyInitializationException` for these under closed-session serialization (test or manual).

### Tier 2 — raw Employee endpoints (reuse EmployeeResponse)
- [ ] A6. `EmployeeController.getById/create/update/updateStatus/uploadPhoto` → return `EmployeeResponse` (map in `EmployeeService`). No new DTO.

### Tier 3 — already-masked entities (lower risk, per A1 mandate)
- [ ] A7. `LeaveRequestResponse` + `LeaveBalanceResponse` → `LeaveController`.
- [ ] A8. `TimesheetResponse` (+ `TimesheetEntryResponse`) → `TimesheetController`.
- [ ] A9. `DocumentResponse` → `DocumentController`.
- [ ] A10. `SalaryRecordResponse` + `PaySlipResponse` → `SalaryController` records/payslips.
- [ ] A11. `NotificationResponse` → `NotificationController`.
- [ ] A12. `AuditLogResponse` → `AuditLogController`.

### PII + Swagger
- [ ] A13. Add `@JsonIgnore` to `Employee.idNumber`; omit from all DTOs.
- [ ] A14. Update `@Operation`/response types so Swagger schema reflects DTOs.

### Frontend + verify
- [ ] A15. Update `employeehub` / `hrdashboard` service reads for any changed shape (mirror in this PR). Grep both apps for nested-entity reads.
- [ ] A16. `mvnw verify` green; both frontend suites green; spot-check the affected pages render.
- [ ] A17. Grep confirms no controller returns `employeehub.domain.*`.
- [ ] A18. Docs: note DTO contract in `api.md`/`api-contract.md` if shapes changed.
- [ ] A19. Open PR1; Progress Log row in `next-gen-features.md`.

---

## Slice B — Flyway baseline + validate  (PR2: `feature/flyway-baseline`, after PR1 merges)

### Dependencies + baseline authoring (throwaway DBs only)
- [ ] B1. Add `flyway-core` + `flyway-database-postgresql` to `pom.xml` (Boot-managed versions).
- [ ] B2. Generate `V1__baseline_schema.sql` by capturing Hibernate's generated DDL on a throwaway empty Postgres 15 (schema-generation scripts action, or dump). Cover all 22 tables + mixed UUID/Long PKs + `failed_login_attempts`/`locked_until` + unique/not-null/enum constraints.
- [ ] B3. Validation loop: fresh DB + `ddl-auto=validate` + Flyway V1 → iterate until validate passes clean. (Crux step; throwaway DBs only.)

### Seed relocation
- [ ] B4. Port `data.sql` seed → `V2__seed_reference_data.sql` (reference tables + seeded employees; keep bcrypt hashes for Admin@1234/Employee@1234). Decide guards vs Flyway run-once.
- [ ] B5. Disable `data.sql` seeding (`spring.sql.init.mode: never`; remove/neuter `data.sql`).

### Config flips (all three files)
- [ ] B6. `application.yml`: `ddl-auto: validate`, remove `generate-ddl`, `sql.init.mode: never`, remove `defer-datasource-initialization`, add `spring.flyway.baseline-on-migrate: true` + `baseline-version: 1`.
- [ ] B7. `docker-compose.yml`: set `SPRING_JPA_HIBERNATE_DDL_AUTO: validate` (+ drop `SPRING_JPA_GENERATE_DDL`).
- [ ] B8. `docker-compose.prod.yml`: same flip to `validate`.

### Verify (wiped volume — mandatory)
- [ ] B9. `docker-compose down -v` → `up --build`: Flyway runs V1+V2, app boots, admin login works, Hibernate applies zero changes (validate passes — check logs).
- [ ] B10. Existing-DB path: boot old-style once, switch to Flyway build → baseline-on-migrate adopts at V1, no data loss.
- [ ] B11. `mvnw verify` + CI (real Postgres) green.

### Docs
- [ ] B12. Fix stale steering: `tech.md` #1 (password already fixed) + #2 (resolved); `structure.md` (remove `DataSeeder.java`); `workflow.md` (schema via Flyway only).
- [ ] B13. `employeeapi/docs/architecture.md` + `deployment.md`: Flyway flow + baseline-on-migrate one-time step.
- [ ] B14. Open PR2; Progress Log row.

---

## Definition of Done (whole spec)
- No controller returns a raw entity; `idNumber` never serialized; the 5 unmasked entities safe.
- Flyway owns the schema; `ddl-auto=validate` in all envs; fresh + existing DBs both work; seed via Flyway.
- `mvnw verify` + both frontend suites + CI green on each PR.
- Stale steering/docs corrected.

## Follow-up (NOT this spec)
`best-practices-testing` spec: WebMvcTest (controller layer), Testcontainers (real-Postgres integration + Flyway migration test), ArchUnit (enforce layer boundaries + "no entity in controller return" rule so Slice A can't regress).
