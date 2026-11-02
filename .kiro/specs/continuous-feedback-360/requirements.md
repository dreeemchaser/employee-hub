# Continuous Feedback and 360 Reviews

**Status:** Design only — no implementation in this slice.  
**Source:** `docs/next-gen-features.md`, Feature 9.  
**Purpose:** Extend cycle-based performance reviews with lightweight feedback and structured 360 rounds.

## Use cases

- An employee requests or receives concise feedback tied to a goal or competency.
- A manager gives private, actionable feedback to a direct report.
- A participant sees feedback assigned to them without seeing confidential reviewer identity.
- HR creates a 360 round, selects eligible reviewers, and sets open/close dates.
- A reviewer completes one assigned review once; later edits follow the round state rules.
- An employee acknowledges a completed review and tracks agreed development actions.
- HR views aggregate trends without exposing raw confidential comments unnecessarily.

## Requirements

- Feedback and reviews must have explicit lifecycle states and deadlines.
- Reviewer eligibility must be checked server-side against manager/team/participant rules.
- Employees may view their own received feedback and assigned reviews only.
- Managers may view feedback for permitted direct reports; HR/Admin may administer cycles and aggregates.
- Anonymous feedback must not expose reviewer identity through DTOs, logs, ordering, or error messages.
- Raw comments and ratings require stricter access than aggregate statistics.
- A closed or acknowledged review cannot be silently edited; corrections require an auditable workflow.
- Notifications must be emitted through the existing notification service and respect notification preferences.
- Every create, submit, acknowledge, reopen, and administrative change is audit logged.
- Performance data must not be exposed through organization-chart or general employee endpoints.

## MVP scope

- Lightweight feedback records tied to an employee and optional goal.
- Manager-to-report and peer feedback with configurable visibility.
- A basic 360 round with nominated reviewers, open/close dates, submission, and acknowledgement.
- Aggregate ratings visible to HR and the subject after the round closes.

## Out of scope

- AI-generated summaries.
- External performance integrations.
- Compensation decisions based automatically on ratings.
- Anonymous free-form surveys without an accountable lifecycle.
- Development-plan automation beyond recording agreed actions.

## Acceptance checks

- Unauthorized users cannot read or mutate another employee's feedback.
- Closed-round and duplicate-submission rules are enforced transactionally.
- Anonymous reviewer identity is protected in API responses and audit records.
- Aggregate results cannot be reverse-engineered from small groups without an explicit policy.
