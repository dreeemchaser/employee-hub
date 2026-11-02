# Design

## Data model

Use separate records for `Feedback`, `ReviewRound`, `ReviewAssignment`, and `ReviewResponse`.
Assignments should record reviewer and subject relationships at assignment time, while response
visibility and anonymity are explicit policy fields. Do not embed unbounded comments in employee
or review-cycle entities.

Lifecycle enums should distinguish draft, open, submitted, closed, acknowledged, and withdrawn
states. Add optimistic locking to assignments/responses that multiple users can edit.

## API and authorization

- `GET/POST /feedback` — subject-scoped reads and permitted submissions.
- `GET/POST /performance/rounds` — HR/Admin round administration.
- `GET /performance/rounds/{id}/assignments/my` — current reviewer's assignments.
- `POST /performance/assignments/{id}/submit` — submit once within the open window.
- `POST /performance/reviews/{id}/acknowledge` — subject acknowledgement.
- `GET /performance/rounds/{id}/summary` — role and lifecycle restricted aggregate.

Controllers return DTOs and resolve the current employee from `UserDetails`. Services enforce
subject, reviewer, manager, and HR scopes independently of route security. Aggregation queries
should return grouped projections, not raw response rows.

## Privacy and notifications

Anonymous assignment responses must use a neutral reviewer label and must never serialize the
reviewer relation. Audit events record the policy/action, not confidential comment text. Existing
`NotificationService` is the delivery boundary.

## Testing

Cover state transitions, duplicate submissions, date windows, object-level authorization,
anonymity, aggregation thresholds, optimistic locking, notifications, and DTO serialization.
