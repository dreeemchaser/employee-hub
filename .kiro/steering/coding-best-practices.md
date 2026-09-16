# EmployeeHub API — Coding Best Practices

These rules apply to every code change made in this project. Follow them without exception unless the user explicitly overrides one.

---

## Stack

- **Java 21**, **Spring Boot 3.5.x**
- **Spring Data JPA** + **PostgreSQL**
- **Spring Security 6** + **JJWT 0.12.6** (stateless JWT)
- **Lombok** (`@Getter`, `@Setter`, `@RequiredArgsConstructor`, `@Slf4j`, etc.)
- **springdoc-openapi 2.x** (Swagger UI)
- **Jakarta Bean Validation** (`spring-boot-starter-validation`)
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
- The only exception is `DashboardController` (read-only aggregation shortcut) — do not replicate this pattern for new features.

---

## Controllers

- Annotate with `@RestController`, `@RequestMapping("/resource")`, `@RequiredArgsConstructor`, and `@Tag(name = "...")`.
- Every method must have `@Operation(summary = "...")` for Swagger documentation.
- All responses must be wrapped in `ResponseEntity<ApiResponse<T>>`.
  - Use `ApiResponse.ok(data)` for success.
  - Use `ResponseEntity.status(HttpStatus.CREATED).body(...)` for POST/create operations.
  - Never return raw domain objects or plain strings — always wrap in `ApiResponse`.
- Never put business logic in controllers — delegate everything to the service layer.
- To identify the current authenticated user, use `@AuthenticationPrincipal UserDetails userDetails` and resolve the full `Employee` via `employeeService.getByEmail(userDetails.getUsername())`.
- List endpoints that support filtering must accept `Pageable` for pagination.

---

## Services

- Annotate with `@Service` and `@RequiredArgsConstructor`.
- No interfaces — concrete service classes only (no `XService` / `XServiceImpl` split).
- Apply `@Transactional` at the **method level** on write operations only. Read methods are left unannotated.
- Never annotate the entire class with `@Transactional`.
- Business rule violations: throw `IllegalArgumentException` for bad input, `IllegalStateException` for conflict/state issues.
- Entity not found: throw `ResourceNotFoundException` (the project's single custom exception).
- Do not create new custom exception types unless specifically requested.
- Do not add new dependencies for mapping (no MapStruct, no ModelMapper). Write manual mapping methods in the service.

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

---

## Domain Entities

- Annotate with `@Entity`, `@Table(name = "snake_case_plural")`.
- Use Lombok: `@Getter`, `@Setter`, `@NoArgsConstructor`, `@EqualsAndHashCode(of = "id")`.
- UUID primary keys: `@Id @GeneratedValue(strategy = GenerationType.UUID) private String id;`
- Always include audit timestamps: `@CreationTimestamp private LocalDateTime createdAt;` and `@UpdateTimestamp private LocalDateTime updatedAt;`.
- Relationships: use `FetchType.LAZY` by default. Add `@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})` on every lazy relationship to prevent Hibernate proxy serialization errors.
- Exclude sensitive fields from JSON with `@JsonIgnore` (e.g., `password`).
- Store all enums as `@Enumerated(EnumType.STRING)`.
- Prefer default field values for status/role fields (e.g., `= EmploymentStatus.ACTIVE`, `= Role.EMPLOYEE`).

---

## DTOs

- Use Lombok `@Data` for request DTOs.
- Use `@Getter` + a constructor accepting the domain entity for response-only DTOs (like `MeResponse`).
- Apply Bean Validation annotations (`@NotNull`, `@NotBlank`, `@FutureOrPresent`, etc.) on all new request DTOs. Mark the controller parameter with `@Valid` to trigger validation.
- Imperative validation in services (null checks / throw `IllegalArgumentException`) is acceptable for existing services that already use this pattern, but prefer `@Valid` + Bean Validation for new endpoints.
- The universal response envelope is `ApiResponse<T>`. Never create alternative response wrappers.

---

## Security

- Never remove or loosen existing route-level security rules in `SecurityConfig` without being explicitly asked.
- When adding a new endpoint, always add an explicit `authorizeHttpRequests` rule for it — do not rely on the catch-all `anyRequest().authenticated()` for new routes that require role restrictions.
- Role hierarchy: `EMPLOYEE` < `MANAGER` < `HR_ADMIN` / `PAYROLL_ADMIN` < `SUPER_ADMIN`. Use `hasAnyRole(...)` with the appropriate set of roles.
- Never hardcode JWT secrets, credentials, or sensitive config values in code. Use `@Value("${property.name}")` and reference `application.properties`.
- Passwords must always be encoded with `PasswordEncoder` (BCrypt). Never store or compare plaintext passwords.
- File serving endpoints that are intentionally public must be explicitly listed in `permitAll()`.

---

## Exception Handling

- Never add `try/catch` blocks in controllers — let `GlobalExceptionHandler` handle all exceptions.
- Only catch exceptions in services or utilities when you need to wrap or recover from them (e.g., `IOException` in `PhotoService`).
- The catch-all `Exception` handler in `GlobalExceptionHandler` logs at `log.error` level. Do not swallow exceptions silently elsewhere.
- Map errors to the correct HTTP semantics:
  - Not found → `ResourceNotFoundException` → 404
  - Invalid input → `IllegalArgumentException` → 400
  - Business rule conflict → `IllegalStateException` → 409
  - Auth failure is handled automatically by Spring Security / `GlobalExceptionHandler`

---

## Logging

- Use `@Slf4j` (Lombok). Never use `System.out.println` or `java.util.logging`.
- Log only at `log.error(...)` for unhandled/unexpected failures.
- Do not add verbose debug/info logging to controllers or service methods unless specifically requested.
- Include the exception as the last argument when logging errors: `log.error("Message: {}", value, exception)`.

---

## OpenAPI / Swagger

- Every new controller needs `@Tag(name = "Feature Name")`.
- Every endpoint method needs `@Operation(summary = "Short description")`.
- The OpenAPI config registers both `localhost:8080` and `api:8080` server URLs — do not remove the Docker server URL.

---

## Financial & Tax Calculations

- Always use `BigDecimal` for monetary values and tax calculations. Never use `double` or `float`.
- Apply `RoundingMode.HALF_UP` and explicit scale on all BigDecimal arithmetic.
- South African PAYE and UIF rules are implemented in `SalaryService` — follow existing patterns when modifying or extending salary logic.

---

## General Code Quality

- Prefer `var` (Java 10+) for local variable type inference where the type is obvious from the right-hand side.
- Use `Optional` returns from repositories — always `.orElseThrow(...)` with a meaningful `ResourceNotFoundException`, never `.get()`.
- Null-safe partial updates (PATCH semantics) use explicit null checks: `if (req.getField() != null) entity.setField(req.getField());`.
- Avoid adding new Maven dependencies without good reason. Check if the standard Spring Boot or existing dependency already covers the need.
- When adding a dependency, pin it to an explicit version. Do not use open version ranges.
- Do not add code that is not required by the task. No speculative abstractions, no premature generalization.
