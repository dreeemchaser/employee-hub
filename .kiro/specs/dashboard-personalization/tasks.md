# Dashboard Personalization + Department Chart — Tasks

One PR, one branch: `feature/dashboard-personalization`, off green `master`. Verify with `mvnw verify` (backend) and `npm run build` (both frontends) before opening the PR. Do not commit/push unless asked.

> Status: **PLANNING COMPLETE — implementation not started.** No code written yet.

## Pre-flight (confirm before coding)
- [ ] 0a. Read `JwtUtil`'s token-building code to confirm whether `sub` is the employee's email or an id — determines the exact `accountKey` for the `localStorage` key (design open question 1).
- [ ] 0b. Re-read `SecurityConfig.java`'s current `/dashboard/**` matcher to confirm it's still a genuine prefix match before relying on it to cover the new route without an explicit new rule.

## Backend

### Aggregation
- [ ] B1. `DepartmentCountProjection` interface (`employeehub/repository/support/DepartmentCountProjection.java` — production code, not test support, despite living in a `support` sub-package for consistency; reconsider the package location if that reads oddly once written).
- [ ] B2. Add `countByDepartmentAndStatus` to `LeaveRequestRepository`, `TimesheetRepository`, `DocumentRepository`.
- [ ] B3. `DepartmentBreakdownEntry` DTO (`employeehub/dto/`).
- [ ] B4. `DashboardAggregationService.getDepartmentBreakdown()` — starts from the full department list, merges in each status-count map, defaults missing departments to 0 per category.
- [ ] B5. `DashboardController`: new `GET /dashboard/department-breakdown` endpoint delegating to the service.

### Backend tests
- [ ] B6. `DashboardAggregationServiceTest` (Mockito): department with partial data still gets 0s elsewhere; full department list length always returned.
- [ ] B7. `DashboardControllerTest` (new file — none exists today): role-gate test for `/department-breakdown` (EMPLOYEE forbidden, HR_ADMIN ok) and for the pre-existing `/stats` endpoint while the file is being created (backfills that pre-existing gap since the controller is being touched anyway).
- [ ] B8. Extend `LeaveRequestRepositoryIT`, `TimesheetRepositoryIT`, `DocumentRepositoryIT` with a case each for `countByDepartmentAndStatus` against real Postgres.
- [ ] B9. `mvnw verify` green.

## Frontend — employeehub (widget personalization)

- [ ] F1. `src/hooks/useDashboardLayout.js` — new hook: loads/saves `{order, hidden}` from `localStorage` keyed by account, merges stale/partial saved order against the current full card-id list (unknown ids from an old save are dropped; new ids not in an old save are appended at the end), defaults to today's fixed order when nothing is saved.
- [ ] F2. Add a stable id to every currently-hard-coded card per the table in design.md (`employeeCount`, `pendingLeave`, `pendingTimesheets`, `annualLeaveLeft`, `expiringDocs`, `leaveBalances`, `recentActivity`).
- [ ] F3. `DashboardPage.jsx`: wire `useDashboardLayout` into the existing card-building logic — filter by `hidden`, sort by `order`, leaving the *eligibility* logic (whether a card has data to show at all) exactly as it is today.
- [ ] F4. Add the "Customize Dashboard" settings panel: checkbox per card (show/hide) + up/down reorder controls. Decide placement (collapsible section on the page vs. a `TopBar` action) at implementation time.
- [ ] F5. Decide and implement: body cards ("Leave Balances", "Recent Activity") join the same reorderable/hideable list (single vertical stack) vs. keep their fixed 2-column row untouched (design's open question 2 — default to the single-stack version unless it looks worse once built).
- [ ] F6. Confirm the clock-in widget and Notifications card are untouched — not in the card-id list, always rendered at their current fixed positions.
- [ ] F7. `npm run build` clean.

## Frontend — hrdashboard (department chart)

- [ ] F8. `npm install recharts@<exact-version>` (check latest stable at implementation time, pin it, no `^`/`~`).
- [ ] F9. `HrService.js`: add `getDepartmentBreakdown()`.
- [ ] F10. `DashboardPage.jsx`: add the "Pending Items by Department" card with a Recharts grouped `BarChart` (department on X-axis, three `Bar` series for leave/timesheets/documents), loaded in the existing `Promise.allSettled` batch. Match series colors to the existing stat-card color scheme for the same three categories.
- [ ] F11. `npm run build` clean — note the bundle size delta from adding Recharts in the PR description (per requirements R4/acceptance criterion 8, report it, don't hide it).

## Verify + wrap-up
- [ ] V1. `mvnw verify` + both frontend builds green.
- [ ] V2. Manual smoke (employeehub): hide a card, reload, confirm it stays hidden; reorder cards, reload, confirm order persists; log in as a second seeded account in the same browser, confirm it has its own independent layout (not the first account's).
- [ ] V3. Manual smoke (hrdashboard): confirm the department chart's numbers match manually filtering the existing list pages by department; confirm a department with zero pending items in every category still appears in the chart (not silently dropped).
- [ ] V4. Live docker-compose smoke test against real Postgres for the new endpoint (matches the verification depth of every other feature this session) — `GET /dashboard/department-breakdown` as HR_ADMIN returns 200 with all departments listed; as EMPLOYEE returns 403.
- [ ] V5. Open PR (title under 70 chars); description states what was tested, notes the Recharts bundle-size delta, and calls out any of the design's "open questions" that got resolved differently than the design doc's lean (e.g. if F5 ended up keeping the 2-column body-card row instead of a single stack).
- [ ] V6. On merge: update `docs/next-gen-features.md` — mark Feature 1 sub-items 1-2 done (drag-and-drop explicitly not done, heatmap not done — this is a partial-feature completion, log it precisely rather than marking the whole Feature 1 line done), add a Progress Log row.

## Definition of Done
- `employeehub` users can hide/show and reorder dashboard cards (excluding the clock-in widget and Notifications); preference persists per-account in `localStorage`; default (no saved preference) is unchanged from today.
- `hrdashboard` shows a department-level bar chart of pending leave/timesheet/document counts, backed by a new, tested backend aggregation endpoint; every department appears even with zero pending items.
- No regression to any existing dashboard stat, list, or endpoint on either app.
- Full test suite green; new endpoint has service + controller + repository-IT coverage.
- Recharts added to `hrdashboard` only, exact-pinned; bundle size delta reported.

## Out of scope (unchanged from requirements.md)
Drag-and-drop, Redux, backend-synced preferences, the HR performance heatmap, predictive analytics/ML, consolidating the three pre-existing duplicate stats aggregations, employee-facing charts.
