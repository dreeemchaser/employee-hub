# Best-Practices Testing — Design

How the three test tiers are built, the dependencies each needs, and the conventions that keep them fast and deterministic. Grounded in the current code (120 Mockito unit tests, Spring Boot 3.5.13, Flyway already present, no web/repo/arch tests).

## Guiding principles
- **Add coverage, don't refactor production.** The only production change this spec contemplates is the optional `AuthController` → DTO conversion (Slice A), done test-first.
- **Match the existing test style.** AssertJ (`assertThat`), Mockito `verify`, `methodName_condition_expectedOutcome` naming, `@BeforeEach` fixtures. New tiers add Spring slices on top — they don't replace the fast Mockito suite.
- **Never H2.** Repository tests run real Postgres via Testcontainers (dialect parity), per steering.
- **Docker-optional locally, Docker-required in CI.** Testcontainers tests skip cleanly with no daemon so a dev without Docker still gets green; CI has Docker and runs them.

---

## Slice A — `@WebMvcTest` controller tests

### Dependency
- `spring-security-test` (test scope, Boot-managed version). Already on the classpath transitively via `spring-boot-starter-test`; declaring it explicitly makes the intent clear and guarantees `SecurityMockMvcRequestPostProcessors` / `@WithMockUser` availability.

### Slice shape
Each test is `@WebMvcTest(XController.class)`, which loads only that controller + the MVC/JSON/validation infrastructure — **not** services or JPA. Collaborators are `@MockitoBean` (Boot 3.4+ replacement for the deprecated `@MockBean`).

The catch: `@WebMvcTest` does **not** auto-load the app's `SecurityConfig`, and `JwtAuthFilter` depends on `JwtUtil` + `UserDetailsServiceImpl`. To exercise authorization for real without standing up JWT parsing per request:
- `@Import(SecurityConfig.class)` to get the real `authorizeHttpRequests` rules.
- Provide `JwtAuthFilter` (or its dependencies `JwtUtil`, `UserDetailsServiceImpl`) as `@MockitoBean` so the filter chain builds, and drive the authenticated principal with `@WithMockUser(username = "...", roles = "HR_ADMIN")` / `SecurityMockMvcRequestPostProcessors.user(...)` instead of a real bearer token. This tests the **route rules and role checks**, which is the point; JWT parsing itself is already covered by `JwtUtilTest`/`JwtAuthFilterTest`.
- A small `WebMvcTestSecurity` support class (or `@AutoConfigureMockMvc` base) centralizes this wiring so each controller test doesn't repeat it.

### What each controller test asserts
Per endpoint:
1. **Authorized success** — mock the service to return a DTO; assert 200/201, `ApiResponse.success = true`, and DTO fields via JsonPath.
2. **Validation failure** (where the request DTO has constraints) — post an invalid body; assert 400 and that the field error surfaces in the `ApiResponse.message` (the `MethodArgumentNotValidException` handler joins `field: message`).
3. **Role refusal** — call as a role the route forbids; assert 403 (authenticated-but-forbidden) or 401 (unauthenticated). Which one depends on the `SecurityConfig` rule; the test encodes the actual behavior.

Cross-endpoint:
- **PII absence** — for `EmployeeController` and any endpoint embedding an employee, assert JsonPath `$..password` and `$..idNumber` are empty. This is the automated form of hardening acceptance criterion A2.
- **Exception → status matrix** — a dedicated `GlobalExceptionHandlerWebTest` (a tiny `@RestController` test double, or reuse a real controller) drives each mapped exception through MockMvc and asserts the status: 404 / 400 / 409 (×3: `BusinessRuleException`, `IllegalStateException`, `OptimisticLockingFailureException`) / 403 / 401 / 423 / 413.

### `AuthController` note
`AuthController` login/refresh currently return a raw `Map<String,Object>` (`{accessToken, refreshToken}` etc.). Two options, decided at implementation:
- **(default) Pin as-is:** assert the current `Map` JSON shape so a future conversion is deliberate.
- **(optional) Convert to `AuthTokenResponse` DTO** in this slice, test-first, keeping the exact JSON field names both React apps read (`accessToken`, `refreshToken`). If chosen, it closes the last raw-`Map` gap noted in the steering adoption caveat.

Recommendation: pin as-is in PR1 to keep the slice purely additive; do the DTO conversion as a separate tiny follow-up if desired.

---

## Slice B — `@DataJpaTest` + Testcontainers repository tests

### Dependencies (pinned)
Import the Testcontainers BOM in `<dependencyManagement>` (import scope), then declare without versions:
- `org.testcontainers:testcontainers`
- `org.testcontainers:junit-jupiter`
- `org.testcontainers:postgresql`
All test scope. Pinning via BOM keeps them aligned and version-explicit (no open ranges).

### Slice shape
```
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class XRepositoryTest {
    @Container @ServiceConnection
    static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:15-alpine");
    ...
}
```
- `@ServiceConnection` (Boot 3.1+) auto-binds the container to the datasource — no manual `@DynamicPropertySource`.
- `replace = NONE` keeps the real Postgres datasource instead of an embedded one.
- **Container reuse:** declare the container `static` and share a base class across repository tests so one Postgres serves the whole tier (faster than one-per-class). Optionally enable Testcontainers reuse (`.withReuse(true)` + `~/.testcontainers.properties`) for local iteration; CI starts fresh.

### Flyway in the test
Let Flyway run automatically against the container (it's on the classpath, `@DataJpaTest` triggers it). This means `V1__baseline.sql` + the seed migration execute, and Hibernate's `validate` runs against the Flyway-built schema — so **the migration-vs-entity parity is asserted for free**. If the baseline drifts, this tier goes red. That closes the hardening Slice B risk ("baseline drift — only manual verification").

### What each repository test asserts
- Custom `@Query` methods return the right rows. For the nullable-filter pattern `(:param IS NULL OR e.field = :param)`, test **both** branches: `param = null` (returns all) and `param = value` (filters). Repositories with such queries: employee/leave/timesheet/document/salary/benefit/notification/audit list queries (enumerate during implementation via a grep for `@Query`).
- `LeaveRequestRepository.findOverlapping` boundary cases: ranges that touch at an endpoint vs genuinely overlap vs disjoint.
- Derived queries with real semantics (`existsByEmail`, `findByEmail`) — a smoke assertion each.

### Docker-absent guard
A JUnit 5 `assumeTrue(DockerClientFactory.instance().isDockerAvailable())` in the base class (or a custom `@EnabledIfDockerAvailable` meta-annotation) makes the whole tier **skip** — not fail — when no daemon is present. This machine currently has no confirmed Docker; CI does. Skipped ≠ failed keeps local `mvnw test` green.

---

## Slice C — ArchUnit architecture tests

### Dependency (pinned)
- `com.tngtech.archunit:archunit-junit5`, test scope, explicit version (e.g. the current stable line — pin at implementation, no range).

### `ArchitectureTest` rules
Import all classes under `employeehub` once (`@AnalyzeClasses(packages = "employeehub")`), then:
- **Layering:** `controller` may not access `repository`; `service` may not access `controller`; `repository`/`service`/`domain` may not access `jakarta.servlet..`.
- **Repositories are interfaces** in `repository`.
- **`@Transactional` only in `service`** (field/method/class) — none in `controller` or `repository`.
- **No `System.out`/`System.err`** and no `java.util.logging` — use `@Slf4j`. (ArchUnit's `GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS`.)
- **No controller method returns `employeehub.domain..`** — the automated guard for the hardening DTO refactor. Implement as a custom `ArchCondition` over methods of `@RestController` classes checking the raw return type and generic type args (`ApiResponse<Employee>`, `Page<Employee>`, etc.).

### Encoded known exceptions
- `DashboardController` → repository access is **allowed** (documented read-only aggregation). Express as `.and().doNotHaveSimpleName("DashboardController")` on the controller-no-repository rule, with a comment tying it to the "Known exceptions" list in `coding-best-practices.md`. Any future exception must be added in both places.

---

## Cross-cutting design
- **Test data builders:** a `test/.../support/` package with `EmployeeTestFactory`, `LeaveTestFactory`, etc. (static builders returning entities/DTOs with sensible defaults + `with...` overrides). Removes the repeated 20-line `@BeforeEach` fixtures the Testing steering warns against; the existing Mockito suites can adopt them opportunistically (not required).
- **Clock injection:** the leave advance-notice check and token expiry use `LocalDate.now()`/`Instant.now()`. Where a new test would otherwise depend on today's date, prefer asserting via the service's injected `Clock` if present; if not present, keep the test date-relative (compute expected from a fixed reference) rather than absolute. Do **not** add `Clock` injection to production classes purely for tests unless a flaky test forces it — flag it if so.
- **Speed:** Slice A (no context beyond the web slice) and Slice C (static analysis) are fast. Slice B pays the container-startup cost once via a shared static container.

## Risks
- **`@WebMvcTest` + custom `JwtAuthFilter`:** the filter's dependencies must be satisfiable as mocks or the context fails to load. Mitigation: mock `JwtUtil` + `UserDetailsServiceImpl`, drive auth via `@WithMockUser`. Verify the context loads on the first controller before writing all 12.
- **Testcontainers in CI:** CI must have a Docker daemon and pull `postgres:15-alpine`. The CI workflow already builds Docker images, so a daemon is present — confirm the test job (not just the docker-build job) can reach it.
- **ArchUnit false positives on generics:** the "no domain in controller return" condition must inspect generic type arguments, not just the raw `ResponseEntity`/`ApiResponse`. Unit-test the condition against a known-bad sample before trusting it.
- **JDK on this machine:** only 25/26 installed (outside SB 3.5's 17–24). Suite compiles/runs on JDK 25; install JDK 21 for parity, or rely on CI's supported JDK for the authoritative run.
