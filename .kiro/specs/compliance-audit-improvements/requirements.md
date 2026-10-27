# Compliance and Audit Improvements

**Status:** Design only — no implementation in this slice.  
**Source:** `docs/next-gen-features.md`, Feature 20 and the existing audit framework.  
**Purpose:** Improve discoverability, retention, export, and compliance reporting on top of the existing audit log.

## Use cases

- HR filters audit events by actor, entity type, action, employee, and date range.
- HR exports a bounded result set for an internal review.
- A compliance reviewer sees document access, tax-table changes, and workflow transitions in one report.
- An administrator configures a retention period without deleting records immediately or unexpectedly.
- The system archives or purges records only through an explicit, auditable retention job.
- A reviewer can distinguish event time, actor, affected employee, and before/after values.
- The system prevents ordinary users from discovering audit records or export URLs.

## Requirements

- Audit log reads remain restricted to `HR_ADMIN` and `SUPER_ADMIN` unless a narrower permission is explicitly designed.
- Filters must be validated and bounded; exports cannot request an unbounded data dump.
- List responses are paged and sorted deterministically by timestamp and id.
- Exported data must use a safe DTO/CSV projection and must not include passwords, tokens, or unnecessary PII.
- Retention configuration must have a documented default, minimum/maximum bounds, and an audit trail.
- Retention jobs must be idempotent, observable, and safe around legal holds or protected events.
- Audit records are append-only to normal application users; corrections require a separate administrative process.
- Access to audit logs and exports is itself audit logged without causing recursive unbounded logging.
- Failures in export or retention jobs must not partially delete records.

## MVP scope

- Better paged/filterable audit UI.
- CSV export with date and result-size limits.
- Document/tax-table access event filters.
- Retention configuration design and a dry-run/report mode before destructive enforcement.

## Out of scope

- Immutable external ledger or blockchain storage.
- SIEM/vendor integrations.
- Legal-hold automation before policy decisions are documented.
- Destructive retention enforcement in the first implementation slice.

## Acceptance checks

- Cross-employee audit access is denied to non-HR roles.
- Export limits and filters are enforced server-side.
- CSV output contains only approved columns and escapes spreadsheet values safely.
- Retention dry-runs produce counts without deleting data.
