# Best-Practices Testing — Tasks

Three sequenced slices, **three separate PRs**, each branched off green master. Verify with `mvnw verify` on a supported JDK (17–24; CI's JDK is authoritative — this machine only has 25/26, which run but are out of range). Slice B additionally needs Docker. Do not commit/push unless asked.

> Status: **PLANNING COMPLETE — implementation not started.** No test code written yet.

## Pre-flight (confirm before coding)
- [ ] 0a. Confirm `@WebMvcTest` context loads with the real `SecurityConfig` imported and `JwtAuthFilter` deps mocked — prove on ONE controller before scaling to 12.
- [ ] 0b. Confirm CI's test job (not only docker-build) has a reachable Docker daemon for Testcontainers.
- [ ] 0c. Decide `AuthController`: pin the raw `Map` shape (default) vs convert to `AuthTokenResponse` DTO this slice.
- [ ] 0d. Grep `repository/` for every `@Query` to enumerate the exact repository-test targets for Slice B.

---

## Slice A — WebMvcTest controller tests  (PR1: `feature/webmvc-controller-tests`)

### Dependency + shared wiring
- [ ] A1. Add `spring-security-test` (test scope, Boot-managed) to `pom.xml`.
- [ ] A2. Create a shared web-test support base (`@Import(SecurityConfig.class)`, `JwtUtil`/`UserDetailsServiceImpl`/`JwtAuthFilter` as `@MockitoBean`, `MockMvc` + `ObjectMapper`) so per-controller tests stay small.
- [ ] A3. Create `support/` test-data factories for the request/response DTOs used across controller tests (X1).

### Per-controller slices (each: success + role-refusal + validation-where-applicable)
- [ ] A4. `EmployeeControllerTest` — incl. JsonPath assertions that `password`/`idNumber` are absent (A4/PII).
- [ ] A5. `AuthControllerTest` — login/refresh/logout/forgot/reset/change-password; pin current shape (or DTO per 0c).
- [ ] A6. `LeaveControllerTest`.
- [ ] A7. `TimesheetControllerTest`.
- [ ] A8. `DocumentControllerTest`.
- [ ] A9. `SalaryControllerTest`.
- [ ] A10. `BenefitControllerTest`.
- [ ] A11. `PerformanceControllerTest`.
- [ ] A12. `NotificationControllerTest`.
- [ ] A13. `AuditLogControllerTest`.
- [ ] A14. `DepartmentControllerTest` + `TeamControllerTest`.
- [ ] A15. `DashboardControllerTest` (HR/admin-only) + `HealthControllerTest` (public).

### Exception-handler matrix
- [ ] A16. `GlobalExceptionHandlerWebTest` — drive each mapped exception through MockMvc: 404 (`ResourceNotFoundException`), 400 (`IllegalArgumentException` + validation), 409 (`BusinessRuleException`, `IllegalStateException`, `OptimisticLockingFailureException`), 403 (`AccessDeniedException`), 401/423 (auth/locked), 413 (max upload).

### Verify + docs
- [ ] A17. `mvnw verify` green (new tests need no DB/Docker).
- [ ] A18. If `AuthController` converted to DTO: update both frontends only if a field name changed (it must not) + note in `api.md`.
- [ ] A19. Correct stale steering: `tech.md` #6 (controller tests now exist).
- [ ] A20. Open PR1; Progress Log row in `next-gen-features.md`.

---

## Slice B — Testcontainers repository tests  (PR2: `feature/repository-testcontainers`, after PR1)

### Dependencies
- [ ] B1. Add Testcontainers BOM (import scope) + `testcontainers`, `junit-jupiter`, `postgresql` (test scope) to `pom.xml`.

### Base + Docker guard
- [ ] B2. `AbstractRepositoryIT` base: static `PostgreSQLContainer<>("postgres:15-alpine")` + `@ServiceConnection`, `@AutoConfigureTestDatabase(replace = NONE)`, and an `assumeTrue(Docker available)` guard so the tier skips cleanly without Docker (B5).
- [ ] B3. Confirm Flyway `V1__baseline.sql` (+ seed) runs against the container and Hibernate `validate` passes — asserts baseline↔entity parity automatically (B4).

### Repository tests (one per repo carrying a custom @Query; enumerate via 0d)
- [ ] B4. Leave repositories incl. `findOverlapping` boundary cases (adjacent vs overlap vs disjoint) + nullable-filter both-branches.
- [ ] B5. Employee / Timesheet / Document / Salary / Benefit / Notification / AuditLog list-query repositories — nullable-filter null + non-null branches.
- [ ] B6. Derived-query smoke tests: `existsByEmail`, `findByEmail`, etc.

### Verify + docs
- [ ] B7. With Docker: tests run + pass. Without Docker: skip (build still green).
- [ ] B8. `mvnw verify` + CI (Docker present) green.
- [ ] B9. `employeeapi/docs/development.md`: how to run the integration tier (Docker required); note the skip-without-Docker behavior.
- [ ] B10. Open PR2; Progress Log row.

---

## Slice C — ArchUnit architecture tests  (PR3: `feature/archunit-rules`, after PR2)

- [ ] C1. Add `archunit-junit5` (test scope, pinned) to `pom.xml`.
- [ ] C2. `ArchitectureTest`: layering (controller↛repository, service↛controller, no `jakarta.servlet` below web), repositories-are-interfaces, `@Transactional` only in service, no `System.out`/`java.util.logging`.
- [ ] C3. Custom rule: no `@RestController` method returns `employeehub.domain..` (inspect generic type args) — automates the hardening DTO guard. Unit-test the condition against a known-bad sample first.
- [ ] C4. Encode the `DashboardController` known exception (repository access allowed) with a comment referencing `coding-best-practices.md`.
- [ ] C5. `mvnw verify` green; correct the `coding-best-practices.md` adoption caveat (ArchUnit + WebMvcTest + Testcontainers now exist).
- [ ] C6. Open PR3; Progress Log row.

---

## Definition of Done (whole spec)
- `@WebMvcTest` for all 12 domain controllers (+ Dashboard/Health): success, role-refusal, validation, PII-absence, and the full exception-status matrix.
- Testcontainers `@DataJpaTest` for every custom `@Query`, real Postgres 15, Flyway-validated, skips cleanly without Docker.
- `ArchUnit` `ArchitectureTest` enforcing the layering + `System.out` ban + "no domain entity in controller return", with the Dashboard exception encoded.
- All new deps pinned; `mvnw verify` + CI green per PR.
- Stale steering corrected (`tech.md` #6; `coding-best-practices.md` adoption caveat).

## Out of scope
Frontend (React Testing Library) tests; new features/endpoints; changing authorization behavior; object-level-authorization (403/404) refactor.
