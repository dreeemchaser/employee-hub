# Design

## API

Extend the existing audit endpoint rather than creating a second audit store:

- `GET /audit-logs` — paged filters for entity type, action, actor, employee, and date range.
- `GET /audit-logs/export` — bounded CSV export using the same filter DTO and an explicit maximum.
- `GET /audit-logs/retention/dry-run` — report eligible records without mutation.
- A later protected admin endpoint may activate retention after legal-hold requirements are defined.

Use a validated filter request/specification and deterministic ordering. All controller responses
use DTOs; CSV generation belongs in a service/streaming utility, not a controller.

## Retention and safety

Keep retention policy separate from audit records. Use a scheduled service with a dry-run mode,
batch deletion/archival, transaction boundaries per batch, and metrics/logging. Do not implement
destructive deletion until legal holds, backup behavior, and recovery expectations are approved.

## Authorization and recursion

Route rules restrict audit access to HR/Admin. The service repeats authorization for every
filter/export operation. Export access may be logged as `AUDIT_EXPORT`; the audit writer must
avoid logging its own audit-read event recursively.

## Testing

Add repository query tests for every optional filter, WebMvc authorization and validation tests,
CSV escaping/column tests, pagination tests, export-limit tests, and retention dry-run tests.
