# Design

## Data model

Prefer a versioned `LeavePolicy`/`LeavePolicyVersion` model over mutating the existing
`LeaveType` row in place. Existing `LeaveBalance` and `LeaveRequest` records should gain a
stable policy-version reference (or an immutable snapshot reference) so historical calculations
remain reproducible.

The migration must first create initial versions from the seeded leave types, backfill current
balances and requests, then make the reference non-null in a later expand/contract step if
required. Never use `ddl-auto=update`; every entity change requires a Flyway migration.

## API and authorization

- `GET /leave/policies` — scoped active policies for employee use; paged where appropriate.
- `GET /leave/policies/admin` — HR/admin policy management view.
- `POST /leave/policies`, `PUT /leave/policies/{id}` — create/update draft versions.
- `PATCH /leave/policies/{id}/activate` and `/retire` — explicit lifecycle transitions.

Controllers resolve the authenticated employee and call services only. Responses use DTOs, and
request DTOs use Bean Validation. Service methods enforce object-level authorization and state
transitions independently of route rules.

## Service rules

`LeavePolicyService` owns lifecycle validation. `LeaveService` asks the policy service for the
effective policy during submission and applies the configured notice/documentation rules. Policy
activation and leave submission must have clear transaction boundaries.

## Testing

- Repository tests for effective-date/version queries and overlap constraints.
- Service tests for lifecycle transitions, historical references, and invalid policy values.
- WebMvc tests for HR-only mutations and employee-scoped reads.
- Regression tests for existing leave submission, approval, cancellation, and balance deduction.
