# Dashboard Personalization + Department Chart — Requirements

**Source:** `docs/next-gen-features.md` Feature 1 ("Intelligent Dashboard With Custom Widgets") and the adjacent Feature 2 ("Real-Time Analytics Dashboard (HR)"), scoped down. See the scoping discussion in this session: the roadmap's full version calls for a drag-and-drop widget framework, Redux, an HR performance heatmap, and predictive analytics. None of that is in scope here — see "Out of scope."
**Status:** Planning.

## Facts verified in code this session

1. **`employeehub`'s `DashboardPage.jsx`** already has: fixed stat cards (employee count for HR, pending leave, pending timesheets, annual leave remaining, documents expiring soon), a clock-in/out widget, leave balance bars, a recent-activity list, and a notifications feed. All hard-coded — no per-user visibility/order preference of any kind.
2. **`hrdashboard`'s `DashboardPage.jsx`** already has: fixed stat cards (total employees, pending leave/timesheets/documents, approved leave), and two recent-items lists (pending leave, pending timesheets). No chart, no department breakdown.
3. **No charting library exists in either frontend.** `package.json` in both apps lists only `axios`, `react`, `react-dom`, `react-router-dom`, `react-scripts`, `web-vitals` as runtime dependencies.
4. **Three independent implementations of the same HR stats aggregation already exist**, none calling each other: `employeeapi`'s `GET /dashboard/stats` (`DashboardController`, an explicitly-documented known exception to the "controllers don't call repositories" rule — read-only aggregation shortcut), `hrdashboard/src/api/HrService.js`'s `getDashboardStats()` (client-side re-aggregation from raw list endpoints), and `hrdashboard/src/pages/DashboardPage.jsx`'s own inline `load()` (a third, slightly different re-aggregation). This spec adds a fourth aggregation (department breakdown) as a new backend endpoint rather than a fourth client-side re-implementation — seed of a case for later consolidating the other three, but that consolidation is not part of this spec (see "Out of scope").
5. **No response DTO in the system carries an employee's department alongside a leave/timesheet/document record.** `LeaveRequestResponse`/`TimesheetResponse`/`DocumentResponse` nest `EmployeeSummary` (`id`, `firstName`, `lastName`, `employeeNumber` only — no department). A department-level breakdown chart cannot be built by re-aggregating existing list endpoints client-side without an N+1-shaped join against `GET /employees`. This is why the chart needs a new backend aggregation endpoint, not a client-side computation over data already being fetched.
6. **`DashboardController` is the correct, already-sanctioned place to add read-only cross-entity aggregation** — it's listed by name in `coding-best-practices.md`'s "Known exceptions" as allowed to call repositories directly. A new department-breakdown endpoint belongs here, not in a new controller.

## Requirements

### R1 — Widget visibility and order (employeehub)
- The employee can hide/show and reorder the dashboard's stat cards (the `STAT_CARDS` array in `DashboardPage.jsx`) and the two body cards ("Leave Balances", "Recent Activity"). The clock-in/out widget and the Notifications card stay fixed at their current positions — they are operational controls and a live feed, not "widgets" in the roadmap's sense, and moving/hiding them adds risk (an employee hiding their own clock-in control) for no real benefit.
- Preference is stored in the browser (`localStorage`), keyed per logged-in user (so switching accounts on a shared machine doesn't leak one user's layout to another) — not synced to the backend. This is deliberately lightweight personal UI state, not business data; see the design doc for the reasoning.
- A card that would render with no data (e.g. "Annual Leave Left" when the employee has no annual leave balance row) is never shown regardless of the user's preference — preference only controls order/visibility among cards that would otherwise render; it cannot force a card to appear with nothing to show.
- Default order/visibility (a first-time user, or `localStorage` cleared) is exactly today's fixed order — this feature must not change what a user with no saved preference sees.

### R2 — A settings control, not drag-and-drop
- The reordering UI is a simple panel: a checkbox per card to show/hide, and up/down controls (or an equivalent ordering control) to move a card earlier/later. No drag-and-drop library.

### R3 — HR department-level breakdown chart (hrdashboard)
- A new bar chart on `hrdashboard`'s dashboard showing, per department, the count of pending leave requests, pending timesheets, and pending documents (the same three "pending" categories the existing stat cards already summarize org-wide — this chart is the per-department breakdown of numbers already shown in aggregate).
- Backed by a new `GET /dashboard/department-breakdown` endpoint (extending the existing, already-sanctioned `DashboardController`), not a client-side join — see fact 5 above for why.
- Departments with zero pending items in all three categories are still listed (so the chart communicates "this department has nothing pending," not just omitted) unless that makes the chart unreadably sparse with many departments — decide the exact display rule in design.

### R4 — New dependency: a charting library
- Introduce Recharts (the library the roadmap itself names) as a runtime dependency in `hrdashboard` only — `employeehub` does not need a chart for this slice (R1/R2 are personalization, not visualization). Pin to an exact version, no open range, per steering.

### R5 — No regression to existing dashboard behavior
- Every stat card, list, and existing endpoint currently on either dashboard keeps working exactly as it does today. This spec adds a personalization layer and one new chart; it does not rewrite the existing aggregation logic (see fact 4 — consolidating the three duplicate aggregations is explicitly out of scope, tempting as it is).

## Acceptance criteria

1. An `employeehub` user can hide the "Recent Activity" card, reload the page, and it stays hidden.
2. An `employeehub` user can reorder the stat cards; the new order persists across reloads and across `npm start` restarts (i.e. it's real `localStorage`, not component state).
3. Two different logged-in users on the same browser see their own independent layouts.
4. A user who has never touched settings sees exactly today's default layout.
5. `GET /dashboard/department-breakdown` (HR_ADMIN/SUPER_ADMIN only, explicit `SecurityConfig` matcher) returns per-department pending counts that match what manually filtering `GET /leave/requests`, `GET /timesheets`, `GET /documents` by department would show.
6. The `hrdashboard` bar chart renders those counts grouped by department, with a legend distinguishing leave/timesheet/document.
7. `mvnw verify` green; new endpoint has service-layer-equivalent test coverage (see design for exactly where the aggregation logic lives) and a `@WebMvcTest` role-gate test.
8. Both frontends build cleanly; `hrdashboard`'s bundle size increase from adding Recharts is reported (not hidden) in the PR.

## Out of scope

- Drag-and-drop reordering.
- Redux or any global state library — `localStorage` + local component state is sufficient for this scope.
- Syncing widget preferences to the backend / across devices.
- An HR performance-ratings heatmap (Feature 1's third sub-item) — no performance-by-department data pipeline exists; this is closer to Feature 9 territory.
- Predictive analytics / ML (Feature 2's third sub-item) — no ML infrastructure exists; wrong scope entirely.
- Consolidating the three existing duplicate stats-aggregation implementations (fact 4) into one. Real cleanup opportunity, but unrelated to shipping this feature and risks regressing working code for no user-facing benefit in this PR.
- Employee-facing charts (R3/R4 are `hrdashboard`-only in this slice).
- Any change to the clock-in/out widget or Notifications card's position/visibility.
