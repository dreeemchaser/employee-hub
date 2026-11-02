# Leave Policy Customization

**Status:** Design only — no implementation in this slice.  
**Source:** `docs/next-gen-features.md`, Feature 4.  
**Purpose:** Replace hard-coded leave rules with HR-managed policy data while preserving existing balances and historical requests.

## Use cases

- HR creates a leave type for a new policy and defines whether it is paid, requires documentation, and how much notice is required.
- HR edits a policy for a future cycle without changing the meaning of past requests.
- HR retires a leave type so it cannot be selected for new requests while historical records remain readable.
- An employee sees only currently active leave types available to their employment context.
- The leave service validates a submission against the policy that was active when the request was made.
- A manager/HR approver can understand which policy rules were applied to a request.

## Requirements

- Leave types must have an effective period or version; historical requests must not change meaning when a policy is edited.
- A policy version defines name, paid status, default entitlement, documentation requirement, notice period, and active/retired state.
- New policies require unique names within the relevant effective period.
- Notice periods and entitlements must be non-negative; effective ranges may not overlap for one logical leave type.
- Retiring a policy prevents new submissions but does not delete balances, requests, or audit history.
- Existing leave requests and balances remain valid after a policy change.
- Employees may submit only against an active policy available to them.
- Policy administration is restricted to `HR_ADMIN` and `SUPER_ADMIN`; employees and managers have read access only to policies applicable to them.
- Every policy create, update, activate, and retire operation is audit logged.
- Leave submission and approval behavior must remain transactional and must not silently rewrite balances.

## MVP scope

- Versioned leave-type policies.
- HR CRUD, activation, and retirement.
- Configurable documentation and notice-period rules.
- Policy-aware employee leave-type selection and submission validation.
- Migration of current seeded leave types into an initial version.

## Out of scope

- Multi-level approval chains.
- Automatic payroll export.
- A general-purpose rules engine.
- Forecasting and carry-over automation beyond fields needed for the policy model.
- Department-specific policies until the base versioned model is stable.

## Acceptance checks

- Historical requests retain their original policy reference.
- An inactive policy cannot be used to create a new request.
- Policy overlap and invalid-value validation is covered by service and repository tests.
- Authorization, audit, and migration behavior are covered before implementation is considered complete.
