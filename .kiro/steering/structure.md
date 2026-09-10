# Structure — EmployeeHub Conventions

## Repository Layout

```
EmployeeHub/
├── employeeapi/          # Spring Boot REST API
├── employeehub/          # React employee self-service portal (port 3000)
├── hrdashboard/          # React HR admin portal (port 3001)
├── docs/                 # Project-level docs (api-contract, data model, guides)
├── docker-compose.yml
├── validate-setup.sh     # Pre-run checks (Docker, ports, files)
└── test-connection.sh    # Post-run connectivity test
```

---

## Backend — employeeapi

### Package Structure

```
src/main/java/employeehub/
├── Application.java              # @SpringBootApplication entry point
├── config/
│   ├── Config.java               # CorsFilter bean
│   ├── OpenApiConfiguration.java # Swagger/OpenAPI setup
│   └── DataSeeder.java           # ApplicationRunner: seeds admin, leave types, tax brackets, benefits
├── security/
│   ├── SecurityConfig.java       # SecurityFilterChain, AuthManager, PasswordEncoder
│   ├── JwtUtil.java              # Token generation and validation
│   ├── JwtAuthFilter.java        # OncePerRequestFilter — extracts Bearer token
│   └── UserDetailsServiceImpl.java # Loads UserDetails from EmployeeRepository
├── controller/                   # One controller per domain area
├── service/                      # Business logic; one service per domain area
├── repository/                   # JpaRepository interfaces; one per entity
├── domain/                       # JPA entities
│   └── enums/                    # All enum types
├── dto/                          # Request DTOs and ApiResponse<T>
├── exception/
│   ├── GlobalExceptionHandler.java
│   └── ResourceNotFoundException.java
└── constant/
    └── Constant.java             # String constants (X-Requested-With header)
```

### Controller Conventions

Every controller follows this exact pattern — match it precisely:

```java
@RestController
@RequestMapping("/resource-name")
@RequiredArgsConstructor
@Tag(name = "Domain Area")          // Swagger grouping
public class ThingController {

    private final ThingService thingService;
    private final EmployeeService employeeService;  // if current user needed

    @GetMapping
    @Operation(summary = "Brief description")
    public ResponseEntity<ApiResponse<List<Thing>>> getAll(
            @AuthenticationPrincipal UserDetails userDetails) {
        var employee = employeeService.getByEmail(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.ok(thingService.getAll(employee)));
    }
}
```

- Use `@AuthenticationPrincipal UserDetails userDetails` to identify the caller, then resolve the full `Employee` via `employeeService.getByEmail(userDetails.getUsername())`
- Return `ResponseEntity<ApiResponse<T>>` — never return raw objects or bare `ResponseEntity`
- Use `ResponseEntity.status(HttpStatus.CREATED).body(...)` for POST endpoints
- Use `ResponseEntity.ok(ApiResponse.ok("message", null))` for void operations
- Never catch exceptions in controllers — let `GlobalExceptionHandler` handle them

### Service Conventions

- `@Service` + `@RequiredArgsConstructor`
- `@Transactional` on any method that writes to the DB
- Throw `ResourceNotFoundException` (→ 404) when an entity is not found
- Throw `IllegalArgumentException` (→ 400) for bad input / business rule violations
- Throw `IllegalStateException` (→ 409) for conflict conditions (duplicate, wrong state)
- Call `notificationService.send(...)` and `auditService.log(...)` inside write methods where appropriate
- Never call `employeeService` from another service — pass the caller's ID or entity as a parameter

### Repository Conventions

- Extend `JpaRepository<Entity, String>` (String = UUID primary key)
- Use `@Query` with named `@Param` bindings for filtered queries
- Nullable filter parameters use `(:param IS NULL OR ...)` pattern in JPQL

### Entity Conventions

- `@Id` + `@GeneratedValue(strategy = GenerationType.UUID)` → `private String id`
- `@CreationTimestamp` / `@UpdateTimestamp` on `createdAt` / `updatedAt`
- Lazy `@ManyToOne` relationships use `@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})` to avoid serialization errors
- Self-referential relationships (e.g. `Employee.manager`) also exclude `password` and `manager` from JSON: `@JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "password", "manager"})`
- Enums stored as `@Enumerated(EnumType.STRING)`
- Do NOT add `@JsonIgnore` to association fields — use `@JsonIgnoreProperties` instead

### DTO Conventions

- Request DTOs: plain `@Getter @Setter` (or `@Data`) Lombok classes in the `dto` package
- The one standard response wrapper is `ApiResponse<T>` — use its static factory methods (`ok`, `error`)
- `MeResponse` is the only dedicated response DTO; it projects a safe subset of `Employee` (no password)
- When creating new endpoints that return `Employee` directly, be aware the `password` field is currently not `@JsonIgnore`'d — prefer adding a response DTO or annotating the field

### Test Conventions

```java
@ExtendWith(MockitoExtension.class)
class ThingServiceTest {
    @Mock ThingRepository thingRepository;
    @InjectMocks ThingService thingService;

    @Test
    void methodName_shouldDescribeExpectedBehavior() {
        // arrange
        // act
        // assert with AssertJ (assertThat)
        // verify with Mockito (verify)
    }
}
```

- Tests live in `src/test/java/employeehub/service/` (service) or `src/test/java/employeehub/security/` (JWT)
- `@BeforeEach` sets up entity fixtures used across tests
- Use `assertThatThrownBy(() -> ...).isInstanceOf(...).hasMessageContaining(...)` for exception assertions
- No `@SpringBootTest` — all tests are pure unit tests with mocks

---

## Frontend — employeehub

### File Structure

```
src/
├── index.js              # ReactDOM.createRoot, wraps App in BrowserRouter
├── App.js                # Auth gate (useState(isLoggedIn())), route definitions
├── index.css             # All styles — CSS custom properties, utility classes, component CSS
├── api/
│   ├── AuthService.js    # login, logout, getToken, isLoggedIn, getRole, isHrOrAdmin
│   └── EmployeeService.js # All API calls: employees, leave, timesheets, docs, salary, benefits, performance, notifications
├── pages/                # One file per route (.jsx)
└── components/           # Shared UI components (.jsx)
```

### Page Conventions

```jsx
import { useState, useEffect, useCallback } from 'react';
import TopBar from '../components/TopBar';
import { somethingService } from '../api/EmployeeService';

export default function ThingPage() {
  const [data, setData] = useState([]);
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    try {
      const res = await somethingService();
      setData(res.data?.data ?? []);
    } catch {
      // silent fail on reads; show feedback on writes
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { load(); }, [load]);

  return (
    <>
      <TopBar title='Page Title' breadcrumb='Employee Hub / Section' />
      <div className='page'>
        {/* content */}
      </div>
    </>
  );
}
```

- `useCallback` on the `load` function, `useEffect` depends on `[load]`
- Read errors are silently caught (empty state shown)
- Write errors are surfaced via local `feedback` state: `{ type: 'success'|'error', msg: string }`
- Every page starts with `<TopBar title='' breadcrumb='' />` then `<div className='page'>`
- No global state — all state is local to the page

### API Service Conventions

All functions in `EmployeeService.js`:
- Take the minimum required parameters
- Return the raw axios response (callers access `res.data.data`)
- Auth header injected via `authHeaders()` = `{ headers: { Authorization: 'Bearer <token>' } }`
- `BASE_URL` from `process.env.REACT_APP_API_URL || 'http://localhost:8080'`
- `normaliseEmployee(emp)` flattens nested API shape to UI shape (adds `name`, `title`, `status`, `department` as strings, `photoURL`)

### CSS Conventions

- All styles in `src/index.css` — no CSS modules, no Tailwind
- CSS custom properties (defined on `:root`): `--brand`, `--border`, `--text-muted`, `--text-secondary`, `--radius-*`, `--blue`, `--amber`, `--green`, `--red`
- Utility classes: `.card`, `.card__header`, `.card__title`, `.card__body`, `.table-wrap`, `.stat-card`, `.stat-card__icon--{color}`, `.badge`, `.badge--{status}`, `.btn`, `.btn-sm`, `.btn-ghost`, `.btn-success`, `.btn-danger`, `.form-group`, `.form-label`, `.form-control`, `.form-grid`, `.page`, `.app-shell`, `.sidebar`, `.sidebar__link`, `.sidebar__link.active`, `.feedback`, `.feedback--success`, `.feedback--error`, `.empty-state`
- Status badge colors: `badge--active` (green), `badge--inactive` (grey), `badge--pending` (amber), `badge--approved` (green), `badge--rejected` (red)
- Icons are Bootstrap Icons loaded from CDN: `<i className='bi bi-icon-name'></i>`

---

## Frontend — hrdashboard

### File Structure

```
src/
├── index.js              # ReactDOM.createRoot, BrowserRouter
├── App.js                # Auth gate + routes (react-router-dom v6)
├── index.css             # Shared design language with employeehub but smaller subset
├── api/
│   ├── AuthService.js    # Identical to employeehub AuthService
│   └── HrService.js      # HR-specific calls: approvals, audit logs, getDashboardStats
├── pages/                # DashboardPage, EmployeesPage, LeaveApprovalsPage,
│                         # TimesheetApprovalsPage, DocumentsPage, AuditLogsPage
└── components/           # Sidebar (data-driven nav array), TopBar, LoginPage, Spinner
```

### Differences from employeehub

- Uses react-router-dom **v6** — `useNavigate` is available but `<Link>` active state via callback is the same
- `Sidebar.jsx` defines nav as a data array (`NAV = [{ section, links: [{to, icon, label}] }]`) rather than hardcoded JSX — follow this pattern when adding nav items
- `HrService.js` imports pattern: `const auth = () => ({...})` (shorter alias) vs employeehub's `authHeaders()`
- hrdashboard does not have `normaliseEmployee` — it renders raw API fields directly (e.g. `r.employee?.firstName`)
- All pages follow the same `load / useCallback / useEffect` pattern as employeehub

---

## Naming Conventions

| Layer | Convention | Example |
|---|---|---|
| Java packages | lowercase | `employeehub.service` |
| Java classes | PascalCase | `LeaveService`, `TimesheetController` |
| Java methods | camelCase | `getAllRequests`, `approveLeave` |
| Java fields | camelCase | `employeeNumber`, `startDate` |
| DB tables | snake_case (Hibernate default) | `employees`, `leave_requests` |
| DB columns | snake_case | `employee_number`, `start_date` |
| Enums | SCREAMING_SNAKE_CASE | `HR_ADMIN`, `ANNUAL_LEAVE` |
| React components | PascalCase files | `LeavePage.jsx`, `NewEmployeeModal.jsx` |
| React pages | `ThingPage.jsx` | `DashboardPage.jsx` |
| React API modules | `DomainService.js` | `EmployeeService.js`, `HrService.js` |
| CSS classes | BEM-ish kebab-case | `sidebar__link`, `stat-card__icon--blue` |
| API endpoints | kebab-case nouns | `/leave-approvals`, `/audit-logs` |
| URL params | camelCase in code, `:id` in path | `@PathVariable String id` |
