# Secure Leave Calendar

**Source:** `docs/next-gen-features.md`, Feature 3.  
**Scope:** secure and filter the existing monthly team calendar. Forecasting, automated approvals,
iCalendar export, and scheduling integrations are deferred.

## Requirements

- Approved leave calendar results are scoped to the authenticated caller:
  - `EMPLOYEE`: own approved leave only.
  - `MANAGER`: approved leave for direct reports.
  - `HR_ADMIN`/`SUPER_ADMIN`: all approved leave.
- Calendar results remain limited to leave requests overlapping the requested month.
- Callers may filter results by leave type.
- The employee portal retains month navigation, color legend, and displays the leave-type filter.
- Days with multiple visible absences are visibly marked as busy.
- Unauthorized callers cannot widen visibility with query parameters.

## Acceptance checks

- `mvnw test` passes.
- The custom repository query has an integration test.
- The employee frontend builds successfully.
