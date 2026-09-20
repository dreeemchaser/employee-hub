# EmployeeHub API — Coding Best Practices

These rules apply to every code change made in this project. Follow them without exception unless the user explicitly overrides one.

> **Adoption status (updated 2026-09):** Most of this standard is now wired into the codebase. **Shipped:** **Flyway migrations + `ddl-auto=validate`** (`db/migration/V1__baseline.sql`, `validate` in all three environments — hardening Slice B); **DTO-only controller responses** for every `Employee`-embedding / PII-bearing entity (hardening Slice A — see the reference-entity exception below); **`@WebMvcTest` controller tests + `spring-security-test`** (testing Slice A — every controller, plus a `GlobalExceptionHandler` status matrix); **`ArchUnit` `ArchitectureTest`** enforcing layering, the `System.out`/`java.util.logging` ban, and "no domain entity in a controller return" (testing Slice C); and **Testcontainers `@DataJpaTest` repository integration tests** on real PostgreSQL 15, run via Failsafe at `mvnw verify` and also validating the Flyway baseline against the entities (testing Slice B — the `best-practices-testing` spec is now complete). **Still outstanding — do not assume this exists:** **`AuthController` still returns a `Map`** in places rather than a dedicated DTO. When you touch these areas, follow the target rule for *new* code where practical and flag any gap, but a full migration is its own task — do not silently retrofit the whole project mid-feature.

**Precedence:** an explicit instruction from the user overrides a rule here, but only for that change — it does not become the new default. If a task seems to require a pattern this document forbids, say so and propose it before writing the code. Do not introduce a new pattern silently.

**Known exceptions** (do not extend, do not replicate):

- `DashboardController` calls repositories directly (read-only aggregation shortcut).
- Reference/lookup entities are still returned raw by their controllers: `Department`, `Team`, `BenefitType`, `PerformanceCycle` (simple value-like tables, no lazy `Employee`, no PII). These are the only exceptions to "no domain entity in a controller return" and are encoded in `ArchitectureTest.ALLOWED_RAW_ENTITY_RETURNS`. Any new exception must be added in both places.

---

## Stack

- **Java 21**, **Spring Boot 3.5.x**
- **Spring Data JPA** + **PostgreSQL**
- **Flyway** for schema migrations
- **Spring Security 6** + **JJWT 0.12.6** (stateless JWT)
- **Lombok** (`@Getter`, `@Setter`, `@RequiredArgsConstructor`, `@Slf4j`, etc.)
- **springdoc-openapi 2.x** (Swagger UI)
- **Jakarta Bean Validation** (`spring-boot-starter-validation`)
- Testing: **JUnit 5**, **Mockito**, **Testcontainers**, **spring-security-test**, **ArchUnit**
- Build: **Maven** (`mvnw`)

---

## Layer Architecture

Strict layered architecture. Dependencies only flow downward:

```
Controller → Service → Repository → Domain
```

- Controllers call services only — never repositories directly.
- Services call repositories and other services.
- Repositories are Spring Data JPA interfaces — no implementation classes.
- Services never depend on controllers or on any `jakarta.servlet` type.
- Transaction boundaries live in the service layer only — never in a controller, never in a repository.

These rules are enforced by an ArchUnit test (`ArchitectureTest`), not just by review. Any new exception must be added to that test with a comment explaining why, and to the "Known exceptions" list at the top of this document.

---

## Controllers

- Annotate with `@RestController`, `@RequestMapping("/resource")`, `@RequiredArgsConstructor`, and `@Tag(name = "...")`.
- Every method must have `@Operation(summary = "...")` for Swagger documentation.
- All responses must be wrapped in `ResponseEntity<ApiResponse<T>>`.
  - Use `ApiResponse.ok(data)` for success.
  - Use `ResponseEntity.status(HttpStatus.CREATED).body(...)` for POST/create operations.
  - Never return raw domain objects or plain strings — always wrap in `ApiResponse`.
- **`T` must always be a DTO, never a domain entity.** `ApiResponse<EmployeeResponse>`, never `ApiResponse<Employee>`. Serialising entities leaks fields, leaks Hibernate proxies, and couples the API contract to the schema.
- Never put business logic in controllers — delegate everything to the service layer. Mapping is not controller work either; see **DTOs**.
- To identify the current authenticated user, use `@AuthenticationPrincipal UserDetails userDetails` and resolve the full `Employee` via `employeeService.getByEmail(userDetails.getUsername())`.
- **Every endpoint that returns a collection must accept `Pageable` and return a page** — not just the filterable ones.
- URL scheme: keep every route on the same convention. If routes move behind an `/api/v1` prefix, move all of them in a single change. Never mix prefixed and unprefixed routes.

---

## Services

- Annotate with `@Service` and `@RequiredArgsConstructor`.
- No interfaces — concrete service classes only (no `XService` / `XServiceImpl` split).
- Apply `@Transactional` at the **method level** on write operations.
- Apply `@Transactional(readOnly = true)` on read methods that touch lazy relationships — it prevents `LazyInitializationException` during mapping and skips dirty checking. Trivial single-entity reads may stay unannotated.
- Never annotate the entire class with `@Transactional`.
- Business rule violations:
  - Bad input that Bean Validation cannot express → `IllegalArgumentException` (400).
  - Conflict with current state (duplicate, already approved, insufficient balance) → `BusinessRuleException` (409).
- Entity not found: throw `ResourceNotFoundException`.
- Do not create new custom exception types beyond `ResourceNotFoundException` and `BusinessRuleException` unless specifically requested.
- Do not add new dependencies for mapping (no MapStruct, no ModelMapper). Manual mapping only — see **DTOs** for where it lives.
- Any method that reads or mutates a record identified by a path variable must perform an authorisation check — see **Security → Object-level authorisation**.

---

## Repositories

- Extend `JpaRepository<Entity, ID>` — interface only, no implementation class.
- For filterable list queries with optional parameters, use JPQL with `@Query` and `@Param`, using the nullable-filter pattern:
  ```java
  @Query("SELECT e FROM Entity e WHERE (:param IS NULL OR e.field = :param)")
  Page<Entity> findAllFiltered(@Param("param") Type param, Pageable pageable);
  ```
- Use derived query method names (`findByEmail`, `existsByEmail`) for simple lookups.
- ID types: UUID-based entities use `String` as the ID type; reference/lookup entities use `Long`.
- Every custom `@Query` must have a test — JPQL breaks at runtime, not at compile time.

---

## Domain Entities

- Annotate with `@Entity`, `@Table(name = "snake_case_plural")`.
- Use Lombok: `@Getter`, `@Setter`, `@NoArgsConstructor`, `@EqualsAndHashCode(of = "id")`. Never `@Data` or `@ToString` on an entity — both will walk lazy relationships.
- UUID primary keys: `@Id @GeneratedValue(strategy = GenerationType.UUID) private String id;`
- Always include audit timestamps: `@CreationTimestamp private LocalDateTime createdAt;` and `@UpdateTimestamp private LocalDateTime updatedAt;`.
- Relationships: use `FetchType.LAZY` by default, on `@ManyToOne` and `@OneToOne` too (they are EAGER unless you say otherwise).
- `@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})` is only needed where an entity is still being serialised directly. New code returns DTOs, so new relationships do not need it. Leave it on existing entities until they are no longer serialised.
- Exclude sensitive fields from JSON with `@JsonIgnore` (e.g., `password`).
- Store all enums as `@Enumerated(EnumType.STRING)`.
- Prefer default field values for status/role fields (e.g., `= EmploymentStatus.ACTIVE`, `= Role.EMPLOYEE`).
- Add `@Version private Long version;` to any entity two users can realistically edit at once (salary, leave balance, leave request, employee record).
- Declare column precision explicitly on monetary and rate fields — see **Financial & Tax Calculations**.

---

## DTOs

- Use Lombok `@Data` for request DTOs.
- Use `@Getter` + a constructor accepting the domain entity for response DTOs (the `MeResponse` pattern).
- **Mapping lives in the response DTO constructor**, or in a static `XMapper` class when one DTO is built from several entities. Not inline in the controller, and not as a growing pile of private methods in the service.
- Apply Bean Validation annotations (`@NotNull`, `@NotBlank`, `@Email`, `@FutureOrPresent`, `@Positive`, etc.) on all new request DTOs. Mark the controller parameter with `@Valid` to trigger validation.
- Imperative validation in services (null checks / throw `IllegalArgumentException`) is acceptable for existing services that already use this pattern, but prefer `@Valid` + Bean Validation for new endpoints.
- Request DTOs never contain `id`, `createdAt`, `updatedAt`, `role`, or `password` unless the endpoint genuinely exists to change them. Mass-assignment is the easiest way to hand an employee an admin role.
- The universal response envelope is `ApiResponse<T>`, for errors as well as successes. Never create alternative response wrappers.

---

## Database & Migrations

- **Flyway owns the schema.** `spring.jpa.hibernate.ddl-auto=validate` in every environment. Never `update`, never `create-drop` outside test configuration.
- Migrations live in `src/main/resources/db/migration` and are named `V{n}__snake_case_description.sql`, numbered sequentially.
- Any change to an entity ships with its migration **in the same commit**. An entity field with no matching column fails startup — that is the point.
- Never edit a migration that has already been applied anywhere. Write a new one.
- Destructive changes (dropping a column or table, renaming, narrowing a type) are done expand-then-contract across two releases: add the new shape, migrate the data, stop writing the old shape, drop it later.
- Index anything used as a filter or join key on a table that will grow (`employee_id`, `status`, `department_id`, `created_at`).
- Add a `NOT NULL` column with a default, or backfill in the same migration. Never leave a nullable column that the code assumes is populated.

---

## Security

### Route-level

- Never remove or loosen existing route-level security rules in `SecurityConfig` without being explicitly asked.
- When adding a new endpoint, always add an explicit `authorizeHttpRequests` rule for it — do not rely on the catch-all `anyRequest().authenticated()` for new routes that require role restrictions.
- Role hierarchy: `EMPLOYEE` < `MANAGER` < `HR_ADMIN` / `PAYROLL_ADMIN` < `SUPER_ADMIN`. Use `hasAnyRole(...)` with the appropriate set of roles.

### Object-level authorisation

Route rules answer "may this role call this endpoint". They cannot answer "may this caller see *this record*". Both are required.

- Any endpoint taking an identifier that is not the caller's own must verify access in the service layer before returning or mutating data: the caller owns the record, or manages the owner, or holds an admin role.
- An `EMPLOYEE` calling `GET /employees/{id}` for another employee, or `GET /payslips/{id}` for someone else's payslip, must be refused. This is the most likely real vulnerability in an HR system.
- Prefer returning **404** rather than 403 for records the caller is not allowed to know exist — a 403 confirms the record is real.
- Use `@PreAuthorize` on service methods for rules that cannot be expressed as a URL pattern. `@EnableMethodSecurity` must stay enabled.

### Secrets and credentials

- Never hardcode JWT secrets, credentials, or sensitive config values in code. Use `@Value("${property.name}")` and reference `application.properties`.
- Properties holding secrets read from the environment with no committed production default: `jwt.secret=${JWT_SECRET}`.
- Passwords must always be encoded with `PasswordEncoder` (BCrypt). Never store, log, or compare plaintext passwords.
- Never log a token, password, ID number, or full salary figure — not even at error level.

### File upload and serving

- Generate the stored filename yourself (UUID + validated extension). Never use the client-supplied filename, and never interpolate it into a path.
- Validate content type and enforce a maximum size (`spring.servlet.multipart.max-file-size`).
- File serving endpoints that are intentionally public must be explicitly listed in `permitAll()` — and because a public URL is effectively unauthenticated, those filenames must be unguessable. Employee photos are personal data.

---

## Exception Handling

- Never add `try/catch` blocks in controllers — let `GlobalExceptionHandler` handle all exceptions.
- Only catch exceptions in services or utilities when you need to wrap or recover from them (e.g., `IOException` in `PhotoService`).
- The catch-all `Exception` handler in `GlobalExceptionHandler` logs at `log.error` level. Do not swallow exceptions silently elsewhere.
- Error responses use the same `ApiResponse` envelope as successes.
- Mapping:

| Condition | Exception | Status |
|---|---|---|
| Entity not found / not visible to caller | `ResourceNotFoundException` | 404 |
| Invalid input | `IllegalArgumentException` | 400 |
| Bean Validation failure | `MethodArgumentNotValidException` | 400 + field errors |
| Business rule conflict | `BusinessRuleException` | 409 |
| Concurrent modification | `OptimisticLockingFailureException` | 409 |
| Authorisation failure | `AccessDeniedException` | 403 |

`BusinessRuleException` exists because Spring and third-party libraries throw `IllegalArgumentException` and `IllegalStateException` themselves. Without a dedicated type, a framework bug is reported to the client as a 400 and never investigated.

---

## Logging

- Use `@Slf4j` (Lombok). Never use `System.out.println` or `java.util.logging` (enforced by ArchUnit).
- `log.error(...)` for unhandled or unexpected failures. Include the exception as the last argument: `log.error("Message: {}", value, exception)`.
- `log.info(...)` for security- and money-relevant state changes only: role change, salary change, account enable/disable, password reset, leave approval. Log the actor, the target id, and what changed — never the values of sensitive fields.
- No verbose debug/info logging in controllers or ordinary service methods unless specifically requested.

---

## OpenAPI / Swagger

- Every new controller needs `@Tag(name = "Feature Name")`.
- Every endpoint method needs `@Operation(summary = "Short description")`.
- Document the non-200 outcomes that a client must handle with `@ApiResponses` (404, 409, 403).
- The OpenAPI config registers both `localhost:8080` and `api:8080` server URLs — do not remove the Docker server URL.

---

## Financial & Tax Calculations

- Always use `BigDecimal` for monetary values and tax calculations. Never `double` or `float` — including in DTOs and in test data.
- Never construct from a double: `BigDecimal.valueOf(19.99)` or `new BigDecimal("19.99")`, never `new BigDecimal(19.99)`.
- **Never compare with `equals()`** — `2.50` and `2.5` are not equal by `equals()`. Use `compareTo(other) == 0`, and `signum()` for zero and sign checks.
- Persist with explicit precision: `@Column(precision = 19, scale = 2)` for amounts, `scale = 4` for rates and percentages.
- Carry full precision through intermediate steps and round **once**, at the final result, with `setScale(2, RoundingMode.HALF_UP)`. Rounding at every step compounds the error across a payroll run.
- South African PAYE and UIF rules are implemented in `SalaryService` — follow existing patterns when modifying or extending salary logic.
- Tax brackets, rebates and UIF ceilings are values, not code: keep them in configuration or clearly versioned constants tagged by tax year, so last year's payslips remain reproducible.
- Every salary calculation change needs tests at the bracket boundaries and at the UIF ceiling, not just a mid-range example.

---

## Performance

- **N+1 queries.** Lazy relationships plus mapping over a list produces one query per row. Before returning a list DTO that touches a relationship, make the fetch explicit.
  - To-one relationships: `@EntityGraph(attributePaths = {"department", "manager"})` or `JOIN FETCH`. Safe to combine with `Pageable`.
  - To-many collections: do **not** `JOIN FETCH` a collection alongside `Pageable` — Hibernate falls back to paginating in memory (`HHH000104`). Use `@BatchSize(size = 25)` on the collection, or fetch a page of ids first and load the entities in a second query.
- Pagination limits in `application.properties`:
  ```properties
  spring.data.web.pageable.default-page-size=20
  spring.data.web.pageable.max-page-size=100
  ```
  Without a cap, `?size=999999` is a free denial of service.
- No queries inside loops, ever. Fetch the set up front and work in memory.
- Do not add caching, async, or batching speculatively. Add it when a specific endpoint is measurably slow, and say which one.

---

## Concurrency & Auditing

- Entities carrying `@Version` (see **Domain Entities**) will throw `OptimisticLockingFailureException` on a conflicting update. That maps to 409 — do not catch and retry silently.
- Track *who* changed a record, not just when: `@CreatedBy` / `@LastModifiedBy` with `@EntityListeners(AuditingEntityListener.class)`, an `AuditorAware<String>` bean resolving the current user from the `SecurityContext`, and `@EnableJpaAuditing` on the application class. Hibernate's `@CreationTimestamp` / `@UpdateTimestamp` continue to handle the timestamps.
- Approvals, salary changes and role changes must be attributable after the fact. "The system changed it" is not an acceptable answer in an HR application.

---

## Testing

A change is not complete without its tests. If a task adds a service method with a branch in it, or an endpoint, the tests are part of that task.

### What must be tested

- Every service method containing a business rule or conditional — including the failure path, not only the happy path.
- Every new endpoint: success, validation failure, and refusal for a caller who should not have access.
- Every custom `@Query`.
- Every salary, PAYE and UIF calculation, at boundaries.

Do not test getters, setters, or framework behaviour.

### How

- **Services** — plain JUnit 5 + Mockito, `@ExtendWith(MockitoExtension.class)`, no Spring context. Fast, and the majority of tests.
- **Controllers** — `@WebMvcTest(XController.class)` with `@MockitoBean` for the services (`@MockBean` is deprecated since Spring Boot 3.4). Import `SecurityConfig` and use `@WithMockUser(roles = "HR_ADMIN")` from `spring-security-test`, so that authorisation is actually exercised rather than disabled.
- **Repositories** — `@DataJpaTest` against a Testcontainers PostgreSQL container with `@ServiceConnection`, and `@AutoConfigureTestDatabase(replace = NONE)`. Never H2: its JPQL and SQL dialect differ from Postgres, so an H2-green test proves nothing.
- **Architecture** — `ArchitectureTest` asserts the layering rules and the `System.out` ban.

### Conventions

- Name tests `methodName_condition_expectedOutcome`, e.g. `approveLeave_whenBalanceInsufficient_throwsBusinessRuleException`.
- One behaviour per test. Assert the exception type and the message where the message is part of the contract.
- Use a test data builder or factory method for entities; do not copy 20 lines of setup between tests.
- Tests must not depend on execution order, on `Thread.sleep`, or on today's date. Inject a `Clock` where time matters.

---

## General Code Quality

- Prefer `var` (Java 10+) for local variable type inference where the type is obvious from the right-hand side.
- Use `Optional` returns from repositories — always `.orElseThrow(...)` with a meaningful `ResourceNotFoundException`, never `.get()`.
- Null-safe partial updates (PATCH semantics) use explicit null checks: `if (req.getField() != null) entity.setField(req.getField());`.
- Avoid adding new Maven dependencies without good reason. Check if the standard Spring Boot or existing dependency already covers the need.
- When adding a dependency, pin it to an explicit version. Do not use open version ranges.
- Do not add code that is not required by the task. No speculative abstractions, no premature generalization.
- Prefer deleting code to commenting it out. Git remembers.
- If a method needs a comment to explain *what* it does, rename it. Comments explain *why*.
