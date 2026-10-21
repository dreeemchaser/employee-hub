# Dashboard Personalization + Department Chart — Design

## Part A — employeehub widget personalization

### Storage: localStorage, not backend

Confirmed no `DashboardPreference`-style entity exists. Adding one would mean a full vertical (entity + migration + repository + service + controller + DTO + tests) for data that is: (a) purely cosmetic, (b) never read by anything except the one page that wrote it, (c) fine to lose (worst case, a user's dashboard resets to default order — no data loss in the business sense). `localStorage` is the appropriate weight for this. If a future requirement genuinely needs cross-device sync, that's a new, explicit ask — not something to build speculatively now.

**Key scheme:** `dashboard-layout:{employeeId}` — not a fixed key like `dashboard-layout`, so that two different accounts logging into the same browser (a shared kiosk machine, or a developer testing multiple seeded accounts) don't inherit each other's layout. `employeeId` is available via `getRole()`'s JWT-decode pattern already in `AuthService.js` — extend that file with a `getEmployeeId()` helper reading the `sub` claim (confirmed via the JWT details in steering: the token's `sub` is the employee's email, not id — check this at implementation time; if `sub` is the email rather than a stable id, key on the email instead, lower-cased, since email is also a stable per-account identifier and is already what `sub` carries per the JWT steering doc).

### Data shape stored

```js
// localStorage key: `dashboard-layout:${accountKey}`
// value (JSON-stringified):
{
  order: ['annualLeave', 'expiringDocs', 'pendingLeave', 'pendingTimesheets', 'employeeCount', 'leaveBalances', 'recentActivity'],
  hidden: ['recentActivity'],  // subset of the ids above
}
```

Every card (stat cards AND the two body cards, "Leave Balances"/"Recent Activity") gets a stable string id. The clock-in widget and Notifications card are not in this list at all — per R1 they are not configurable, so they always render at their fixed positions regardless of what's in `order`/`hidden`.

### Card id list (fixed, matches current `DashboardPage.jsx` cards)

| id | Current card | Conditional on data? |
|---|---|---|
| `employeeCount` | "Total Employees" stat | HR/Admin only (existing `hrAdmin &&` guard stays — a non-admin never has this id available to show even if `hidden` doesn't list it) |
| `pendingLeave` | "Pending Leave" stat | Always available (shows 0) |
| `pendingTimesheets` | "Timesheets Awaiting" stat | Always available (shows 0) |
| `annualLeaveLeft` | "Annual Leave Left" stat | Only if `annualBalance` exists (per R1, preference cannot force it to appear with nothing to show) |
| `expiringDocs` | "Documents Expiring Soon" stat | Only if `expiringDocsCount > 0` (same reasoning) |
| `leaveBalances` | "Leave Balances" body card | Always available |
| `recentActivity` | "Recent Activity" body card | Always available |

### Rendering logic change in `DashboardPage.jsx`

Today, `STAT_CARDS` is built as a filtered array in a fixed order (see the existing `.filter(Boolean)` chain). The change:

1. Build the same "candidate" card objects as today (unchanged logic for *whether* a card is eligible to show).
2. Load the saved layout (`order`, `hidden`) from `localStorage` via a small new hook/helper, `useDashboardLayout(accountKey, allCardIds)` — returns `{ order, hidden, setOrder, setHidden }`, defaulting to the full id list in today's fixed order and an empty `hidden` set when nothing is saved (satisfies R1's "default = today's order" requirement exactly, by construction, since the default *is* today's literal order).
3. Render: take the candidate cards, keep only those whose id is not in `hidden`, sort by their position in `order` (any candidate id not present in a stale/partial saved `order` — e.g. after this feature ships and adds a new card type later — falls back to appending at the end, so old saved layouts degrade gracefully instead of erroring).
4. The two body cards ("Leave Balances", "Recent Activity") currently render in a fixed 2-column grid unconditionally. They become part of the same ordered/hideable list, rendered below the stat grid in their resolved order — same visual card style, just reorderable/hideable now like the stat cards. (Decide at implementation time whether they stay visually distinct in their own 2-column row or fold into one single reorderable stack — the simpler, lower-risk choice is a single vertical stack once personalization is in play, since a mixed "2-column grid where the user picks which 2 (or 1, or 0) of N cards land in each column" is meaningfully more UI complexity than this feature warrants. Stacking vertically when personalized is an acceptable, minor visual simplification — call this out in the PR description as a deliberate tradeoff, not a silent regression.)

### Settings UI (R2)

A small panel — a button in the dashboard's `TopBar` area or a card of its own titled "Customize Dashboard" — listing every card id with a checkbox (visible/hidden) and up/down buttons reordering within the visible set. No new route; this can be a collapsible section on the dashboard page itself (simplest, matches this codebase's preference for inline UI over separate settings pages — there is no existing "Settings" page/route in `employeehub` to extend instead).

### New file: `employeehub/src/hooks/useDashboardLayout.js`

```js
// Pure client-side hook, no backend calls. Exported separately from
// DashboardPage.jsx so its localStorage-key/default-merging logic has a
// single, testable (if this project later adds RTL coverage) home.
export function useDashboardLayout(accountKey, allCardIds) { ... }
```

`employeehub` has no existing `src/hooks/` directory (confirmed: `src/api`, `src/components`, `src/pages` only) — this is the first hook extracted to its own file. Justified because the merge-stale-saved-order-with-current-card-list logic (point 3 above) is non-trivial enough to want isolated from the page's render logic, and because `DashboardPage.jsx` is already the largest page file in the app after this session's earlier additions (clock-in widget, documents-expiring card) — adding personalization logic inline would make it materially harder to read.

## Part B — hrdashboard department breakdown chart

### Backend: extend `DashboardController`

New endpoint, same controller (per fact 6 in requirements.md — this controller is the sanctioned exception for read-only cross-entity aggregation):

```java
@GetMapping("/department-breakdown")
@Operation(summary = "Get pending leave/timesheet/document counts grouped by department (HR/Admin)")
public ResponseEntity<ApiResponse<List<DepartmentBreakdownEntry>>> getDepartmentBreakdown() {
    return ResponseEntity.ok(ApiResponse.ok(dashboardAggregationService.getDepartmentBreakdown()));
}
```

New DTO:
```java
// employeehub/dto/DepartmentBreakdownEntry.java
@Getter
@AllArgsConstructor
public class DepartmentBreakdownEntry {
    private final String departmentName;
    private final long pendingLeave;
    private final long pendingTimesheets;
    private final long pendingDocuments;
}
```

**Decision: extract the aggregation into a small `DashboardAggregationService`, not inline in the controller.** `DashboardController`'s existing `/stats` method inlines its aggregation directly in the controller, which is itself already a deviation from the "controllers delegate to services" rule (tolerated because the whole controller is a named, documented exception for *repository access*, not necessarily for *business logic in the controller*). Rather than compound that deviation by adding a second, more complex aggregation (three separate GROUP BY queries plus stitching multiple result sets by department name) directly in the controller, this one gets its own thin service. This keeps the new code testable with a plain Mockito unit test (mirroring every other service in this codebase) without needing `@WebMvcTest`/`MockMvc` just to exercise the aggregation math. The existing `/stats` endpoint is left exactly as it is — not refactored to match, per requirements R5 / "no regression," and because touching working, unrelated code is not this task's job.

### Repository queries

Three new `@Query` methods (one per repository, since each entity's association path to `Employee`/`Department` differs only in the root entity):

```java
// LeaveRequestRepository
@Query("SELECT lr.employee.department.name AS departmentName, COUNT(lr) AS count " +
       "FROM LeaveRequest lr WHERE lr.status = :status " +
       "GROUP BY lr.employee.department.name")
List<DepartmentCountProjection> countByDepartmentAndStatus(@Param("status") LeaveStatus status);
```

```java
// TimesheetRepository
@Query("SELECT t.employee.department.name AS departmentName, COUNT(t) AS count " +
       "FROM Timesheet t WHERE t.status = :status " +
       "GROUP BY t.employee.department.name")
List<DepartmentCountProjection> countByDepartmentAndStatus(@Param("status") TimesheetStatus status);
```

```java
// DocumentRepository
@Query("SELECT d.employee.department.name AS departmentName, COUNT(d) AS count " +
       "FROM Document d WHERE d.status = :status " +
       "GROUP BY d.employee.department.name")
List<DepartmentCountProjection> countByDepartmentAndStatus(@Param("status") DocumentStatus status);
```

Shared projection interface (Spring Data JPA interface-based projection, avoids three near-identical DTOs for the same `{name, count}` shape):
```java
// employeehub/repository/support/DepartmentCountProjection.java -- lives in
// the main repository package (not test/support), since it's a real
// production-code projection interface, unlike the test-only EntityFactory.
public interface DepartmentCountProjection {
    String getDepartmentName();
    long getCount();
}
```

**Per R3's "departments with zero pending items are still listed" requirement:** a `GROUP BY` query only returns rows for departments that have at least one matching row — a department with zero pending leave requests simply doesn't appear in that query's result. `DashboardAggregationService.getDepartmentBreakdown()` therefore starts from the full department list (`departmentRepository.findAll()`), then merges in whatever each of the three count queries returns, defaulting missing departments to `0` for that category. This guarantees every department appears in the final list exactly once, with `0`s where there's nothing pending — satisfying R3 without needing a `LEFT JOIN ... GROUP BY` across three different entity types (which JPQL cannot express cleanly in one query anyway, since `LeaveRequest`/`Timesheet`/`Document` aren't related to each other directly).

```java
@Service
@RequiredArgsConstructor
public class DashboardAggregationService {

    private final DepartmentRepository departmentRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final TimesheetRepository timesheetRepository;
    private final DocumentRepository documentRepository;

    public List<DepartmentBreakdownEntry> getDepartmentBreakdown() {
        Map<String, Long> pendingLeaveByDept = toMap(leaveRequestRepository.countByDepartmentAndStatus(LeaveStatus.PENDING));
        Map<String, Long> pendingTimesheetsByDept = toMap(timesheetRepository.countByDepartmentAndStatus(TimesheetStatus.SUBMITTED));
        Map<String, Long> pendingDocumentsByDept = toMap(documentRepository.countByDepartmentAndStatus(DocumentStatus.PENDING));

        return departmentRepository.findAll().stream()
                .map(dept -> new DepartmentBreakdownEntry(
                        dept.getName(),
                        pendingLeaveByDept.getOrDefault(dept.getName(), 0L),
                        pendingTimesheetsByDept.getOrDefault(dept.getName(), 0L),
                        pendingDocumentsByDept.getOrDefault(dept.getName(), 0L)))
                .toList();
    }

    private Map<String, Long> toMap(List<DepartmentCountProjection> rows) {
        return rows.stream().collect(Collectors.toMap(
                DepartmentCountProjection::getDepartmentName, DepartmentCountProjection::getCount));
    }
}
```

Note the "SUBMITTED"/"PENDING" status literals here match the exact status values `DashboardController.getStats()` already uses for the same three categories today (confirmed: `LeaveStatus.PENDING`, `TimesheetStatus.SUBMITTED`, `DocumentStatus.PENDING`) — the department breakdown must use the same statuses as the existing org-wide totals, or the two numbers (org total vs. sum of the per-department breakdown) would silently disagree.

### Security

```java
// SecurityConfig.java — /dashboard/** already has a blanket rule:
.requestMatchers("/dashboard/**").hasAnyRole("HR_ADMIN", "SUPER_ADMIN")
```
This already covers the new `/dashboard/department-breakdown` route (it's a prefix match on `/dashboard/**`, and the existing `/stats` endpoint relies on the same rule) — no new `SecurityConfig` matcher needed, unlike every other feature this session where a new sub-route needed its own explicit rule. Confirmed this is a genuine prefix match, not `/dashboard` exact, before relying on it — verify at implementation time by re-reading the current `SecurityConfig.java` matcher list in case anything changed since context-gathering.

### Frontend: `hrdashboard`

**Recharts setup:** add `recharts` (exact-pinned) to `hrdashboard/package.json` only. A grouped/stacked `BarChart` with `department` on the X-axis and three `Bar` series (leave/timesheets/documents), each a distinct color reusing the existing CSS custom properties (`var(--amber)`, `var(--red)`, `var(--brand)` or similar — match whatever the existing stat cards use for these three categories today, for visual consistency rather than picking new arbitrary chart colors).

**New API function** in `HrService.js`:
```js
/**
 * @returns {Promise<{data: {success?: boolean, message?: string, data?: Array<{departmentName: string, pendingLeave: number, pendingTimesheets: number, pendingDocuments: number}>}}>}
 */
export async function getDepartmentBreakdown() {
  return axios.get(`${BASE_URL}/dashboard/department-breakdown`, auth());
}
```

**Where it renders:** a new full-width card below the existing two-column pending-leave/pending-timesheets row on `hrdashboard`'s `DashboardPage.jsx`, titled "Pending Items by Department." Loaded in the same `Promise.allSettled` batch as the page's other dashboard calls (matches the existing pattern in that file) rather than a separate effect/request waterfall.

## Testing

- `DashboardAggregationServiceTest` (new, Mockito): mock all four repositories; verify a department with data in only one category still appears with `0`s in the other two (proves the "always list every department" merge logic); verify the returned list length equals the department count regardless of how many departments have zero activity.
- `DashboardControllerTest` — no test file exists for this controller today (confirmed gap, same class of gap as `DocumentController.upload` before the previous PR). Add one: `@WebMvcTest(DashboardController.class)`, role-gate test for `/department-breakdown` (EMPLOYEE forbidden, HR_ADMIN ok), mirroring every other controller test this session.
- New repository `@Query` methods each need a `*RepositoryIT` case per steering's "every custom query needs a test against real Postgres" rule — extend the existing `LeaveRequestRepositoryIT`/`TimesheetRepositoryIT`/`DocumentRepositoryIT` files (all three already exist from prior work) rather than creating new IT classes.
- Frontend: manual verification only, matching this project's existing convention (no RTL suite exists for either `DashboardPage.jsx` today; not introducing one unasked).

## Open questions for implementation time

1. Whether the JWT `sub` claim is the email or a numeric/UUID id — determines the exact `accountKey` used for the `localStorage` key. Check `JwtUtil`'s token-building code directly rather than re-deriving from the steering doc's summary.
2. Whether the two body cards fold into the same vertical stack as the stat cards when personalization is active, or keep their own 2-column row untouched and only the stat cards become reorderable (a smaller, safer version of R1 if the full merge feels like scope creep once in the code) — flagged in requirements as a design call; leaning toward the simpler full-stack version above, but confirm this doesn't look worse in practice before committing to it.
3. Exact color mapping for the three chart series — reuse existing CSS var lookups at implementation time rather than guessing hex values here.
