# Best-Practices Testing — Requirements

**Source:** The `best-practices-hardening` spec explicitly deferred test infrastructure to this follow-up (see its "Out of scope" + "Suggested delivery"). Also `tech.md` Known Issue #6 ("No controller or integration tests") and the Testing section of `coding-best-practices.md`.
**Status:** Planning.
**Relationship to hardening spec:** hardening (Slices A + B) has **shipped** to master — DTO-only responses and Flyway + `ddl-auto=validate` are done. This spec builds the test coverage that locks that work (and the API's behavior) in place.

## Facts verified in code this session

Grounding so the spec matches reality, not the aspirational docs:

1. **What tests already exist:** 120 service-layer + security unit tests, all `@ExtendWith(MockitoExtension.class)` (no Spring context). Suites: `LeaveServiceTest`, `TimesheetServiceTest`, `SalaryServiceTest`, `BenefitServiceTest`, `PerformanceServiceTest`, `DocumentServiceTest`, `NotificationServiceTest`, `EmployeeServiceTest`, `AuditServiceTest`, `PhotoServiceTest`, `PasswordResetServiceTest`, `RefreshTokenServiceTest`, `LoginAttemptServiceTest`, `JwtUtilTest`, `JwtAuthFilterTest`, plus DTO tests `Tier1ResponseDtoTest` / `Tier3ResponseDtoTest`.
2. **What is missing:** **zero** controller (`@WebMvcTest`) tests, **zero** repository (`@DataJpaTest`) tests, **zero** full-context (`@SpringBootTest`) tests, **no** ArchUnit, **no** Testcontainers, **no** explicit `spring-security-test` dependency (it comes transitively via `spring-boot-starter-test`, but is unused).
3. **Controllers to cover (14 total):** 12 domain — `AuthController`, `EmployeeController`, `LeaveController`, `TimesheetController`, `DocumentController`, `SalaryController`, `BenefitController`, `PerformanceController`, `NotificationController`, `AuditLogController`, `DepartmentController`, `TeamController` — plus `DashboardController` and `HealthController`.
4. **`AuthController` still returns a raw `Map`** in places (not a DTO) — the one remaining DTO-contract gap the hardening spec did not close. Controller tests here will pin its current shape; converting it to a DTO is a small task this spec MAY include (flagged, not silently done).
5. **`BusinessRuleException` now exists** (added this session) and maps to **409**; `GlobalExceptionHandler` also maps `IllegalStateException` and `OptimisticLockingFailureException` to 409, `IllegalArgumentException`/validation to 400, `AccessDeniedException` to 403, JWT/auth to 401/423. These status mappings are currently only exercised indirectly — a controller/handler test tier makes them first-class.
6. **Pom state:** Spring Boot 3.5.13; `spring-boot-starter-test` present; Flyway (`flyway-core` + `flyway-database-postgresql`) already present. Testcontainers and ArchUnit are **not** present and must be added, pinned.
7. **Build/JDK reality (this machine, macOS):** only JDK 25 and 26 are installed — both **outside** Spring Boot 3.5's supported 17–24 range. JDK 25 compiles and runs the suite (Lombok warns but does not crash); JDK 26 is untested for Lombok. CI uses a supported JDK. Testcontainers needs a running Docker daemon — tests must **skip cleanly** (not fail) when Docker is absent locally.

---

## Slice A — Controller / web-layer tests (`@WebMvcTest`)

### Problem
No test exercises the web layer: request mapping, JSON (de)serialization of the new DTOs, Bean Validation (`@Valid`), the `ApiResponse` envelope, `GlobalExceptionHandler` status mapping, or — most importantly — **route-level authorization**. A regression that widens a role rule, breaks a DTO shape a frontend reads, or changes an error status would pass CI today.

### Requirements
- **A1** — Add `spring-security-test` as an explicit test dependency (Boot-managed version) so `@WithMockUser` / `SecurityMockMvcRequestPostProcessors` are available and intentional.
- **A2** — Each domain controller gets a `@WebMvcTest(XController.class)` slice with its collaborating services provided as `@MockitoBean` (`@MockBean` is deprecated since Boot 3.4). Import the real `SecurityConfig` and `JwtAuthFilter` wiring so authorization is **actually exercised**, not disabled.
- **A3** — For every endpoint, cover three cases at minimum: (1) success for an authorized role, (2) request-validation failure → 400 with field errors where the DTO has constraints, and (3) refusal for a role that must not have access → 401/403. Ownership/object-level refusals that live in the service layer stay in service tests; route-level refusals are asserted here.
- **A4** — Assert the `ApiResponse` envelope shape (`success`, `message`, `data`) and the DTO field set via JsonPath — specifically that `password` and `idNumber` never appear in any response body.
- **A5** — Pin `GlobalExceptionHandler` behavior: a `ResourceNotFoundException` → 404, `BusinessRuleException`/`IllegalStateException`/`OptimisticLockingFailureException` → 409, `IllegalArgumentException` + validation → 400, `AccessDeniedException` → 403. At least one test per mapped status, driven through a controller (mock the service to throw).
- **A6** — `AuthController`: pin the current response shape (including the raw `Map` from login/refresh) so any future DTO conversion is a deliberate, test-visible change. If this spec converts `AuthController` to a DTO (optional task), do it in the same change as the test that asserts the new shape.
- **A7** — No production behavior changes in Slice A other than the optional `AuthController` DTO conversion (which, if done, must keep the JSON field names the two frontends read).

### Slice A — Acceptance criteria
1. `spring-security-test` present in `pom.xml`, test scope, Boot-managed version.
2. A `@WebMvcTest` class exists for each of the 12 domain controllers; security is imported and `@WithMockUser(roles = ...)` drives authorization.
3. Each covered endpoint asserts success + a role-refusal; validation failure asserted where the request DTO carries constraints.
4. A JsonPath assertion proves `password` and `idNumber` are absent from representative response bodies.
5. Every `GlobalExceptionHandler` status mapping has at least one controller-driven test.
6. `mvnw verify` green on a supported JDK; new tests need no database or Docker.

---

## Slice B — Repository tests against real Postgres (`@DataJpaTest` + Testcontainers)

### Problem
Every custom `@Query` (the nullable-filter list queries, `findOverlapping`, `existsByEmail`, etc.) is Postgres JPQL/SQL that **breaks at runtime, not compile time**. `coding-best-practices.md` requires a test per custom query, against real Postgres — never H2, whose dialect differs. The Flyway baseline (`V1__baseline.sql`) also has no automated proof that it matches the JPA entities.

### Requirements
- **B1** — Add Testcontainers (`testcontainers`, `junit-jupiter`, `postgresql`) as test dependencies, versions pinned via the Testcontainers BOM (import-scope) or explicit versions — no open ranges.
- **B2** — `@DataJpaTest` + `@AutoConfigureTestDatabase(replace = NONE)` + a Testcontainers `postgres:15-alpine` container wired with `@ServiceConnection`, matching the production DB major version. A single reusable container/base test class (avoid one container per test class where possible).
- **B3** — Every repository with a custom `@Query` gets a test asserting the query returns the right rows, including the `(:param IS NULL OR ...)` nullable-filter branches (both null and non-null), and `findOverlapping` boundary cases (adjacent vs overlapping date ranges).
- **B4** — Let Flyway run the real `V1__baseline.sql` (+ seed) against the container so the migration is validated as a side effect: if the baseline drifts from the entities, `validate` fails the test tier — closing the hardening-spec risk that had only manual verification.
- **B5** — Tests must **skip cleanly** (JUnit assumption / `@EnabledIf`-style guard, or `DockerClientFactory.instance().isDockerAvailable()`) when no Docker daemon is present, so a developer without Docker (like this machine's default state) gets a green build, while CI — which has Docker — runs them for real.
- **B6** — No H2 anywhere.

### Slice B — Acceptance criteria
1. Testcontainers deps in `pom.xml`, pinned; `postgres:15-alpine` used.
2. A `@DataJpaTest` exists for each repository carrying a custom `@Query`; both filter branches covered.
3. Tests run Flyway `V1__baseline.sql` and pass `validate` — proving baseline ↔ entity parity automatically.
4. With Docker present: repository tests execute and pass. Without Docker: they skip (not fail) and the rest of the suite is green.
5. `mvnw verify` green in CI (Docker available).

---

## Slice C — Architecture tests (ArchUnit)

### Problem
The layering rules in `coding-best-practices.md` ("Controller → Service → Repository → Domain", "controllers never call repositories", "services never touch `jakarta.servlet`", "no `System.out`") are stated to be "enforced by an ArchUnit test (`ArchitectureTest`)" — but that test **does not exist**. The rules are review-only today.

### Requirements
- **C1** — Add ArchUnit (`com.tngtech.archunit:archunit-junit5`) as a test dependency, pinned.
- **C2** — Author `ArchitectureTest` asserting: controllers do not depend on repositories; services do not depend on controllers or on any `jakarta.servlet` type; repositories are interfaces; `@Transactional` appears only in the service layer; no class uses `System.out`/`System.err` or `java.util.logging` (use `@Slf4j`).
- **C3** — Encode the documented **known exceptions** so they don't fail the build: `DashboardController` calls repositories directly (read-only aggregation). Each exception in the test carries a comment and must stay in sync with the "Known exceptions" list at the top of `coding-best-practices.md`.
- **C4** — Also assert the DTO rule from the hardening work: no `@RestController` method returns a `employeehub.domain.*` type (all responses are DTOs) — the automated form of Slice A's `grep` acceptance check.

### Slice C — Acceptance criteria
1. `archunit-junit5` in `pom.xml`, pinned.
2. `ArchitectureTest` passes against current code, with the `DashboardController` exception explicitly encoded and commented.
3. The "no controller returns a domain entity" rule passes (proving the hardening DTO refactor holds) and would fail if a raw entity return were reintroduced.
4. `mvnw verify` green.

---

## Cross-cutting requirements
- **X1** — Test data builders/factories for entities and DTOs; no 20-line copy-paste setup across tests (Testing section of steering).
- **X2** — Test names follow `methodName_condition_expectedOutcome`. One behavior per test.
- **X3** — No test depends on execution order, `Thread.sleep`, or today's date. Inject a `Clock` where time matters (the leave-notice / token-expiry paths).
- **X4** — All new dependencies pinned (BOM-managed or explicit) — no open ranges.
- **X5** — Update the stale steering once true: `tech.md` #6 ("No controller or integration tests") and the `coding-best-practices.md` adoption caveat both get corrected in the slice that makes them false.

## Out of scope (this spec)
- Frontend tests (React Testing Library) — separate effort; this spec is backend test infrastructure.
- New product features or endpoints.
- Changing role/authorization *behavior* (tests pin current behavior; they don't redefine it).
- Object-level authorization refactor (403/404 for other users' records) — that's a security task; tests here assert what the code does today.

## Suggested delivery
Three PRs, sequenced on green master: **PR1 = Slice A (WebMvcTest + spring-security-test)**, **PR2 = Slice B (Testcontainers repository tests)**, **PR3 = Slice C (ArchUnit)**. A is highest value (locks the API contract + authorization), C is cheapest. B needs Docker in CI — verify that first.
