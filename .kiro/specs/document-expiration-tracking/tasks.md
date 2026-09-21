# Document Expiration Tracking — Tasks

One PR, one branch: `feature/document-expiry-tracking`, off green `master`. Verify with `mvnw verify` (backend) and `npm run build` (both frontends) before opening the PR, matching the pattern used for Attendance/Offboarding/API-types this session. Do not commit/push unless asked.

> Status: **PLANNING COMPLETE — implementation not started.** No code written yet.

## Pre-flight (confirm before coding)
- [ ] 0a. Confirm whether a `Clock` bean already exists anywhere in the codebase (grep `Clock` across `employeeapi/src/main/java`) before adding a new one in `Config.java` — avoid a duplicate-bean conflict.
- [ ] 0b. Confirm the `best-practices-testing` spec's Testcontainers tier (`AbstractRepositoryIT` or equivalent) actually landed and is usable in this environment before assuming it in the design's testing section — if it didn't land, the new repository queries still need *a* test, just via whatever tier currently exists (grep for `AbstractRepositoryIT`).
- [ ] 0c. Decide the exact HR-notification query method name (`findByStatusAndExpiryDateAndExpiryNotifiedFalse` vs `findNewlyExpiredVerified`) — either is fine per the design doc, just pick one and move on.

## Backend

### Migration + entity
- [ ] B1. `V3__add_document_expiry_tracking.sql`: `expiry_date`, `reminder_30_sent_at`, `reminder_14_sent_at`, `reminder_7_sent_at`, `hr_expiry_notified_at` — all nullable, no backfill. Index on `expiry_date`.
- [ ] B2. Add the five corresponding fields to `Document.java` with getters/setters (Lombok `@Getter`/`@Setter` already on the class — just add fields).

### DTOs
- [ ] B3. `DocumentResponse`: add `expiryDate` and computed `isExpired`.
- [ ] B4. `DocumentController`: add `@Validated` at class level; `upload` endpoint gains `@RequestParam(required = false) @FutureOrPresent LocalDate expiryDate`.

### Service
- [ ] B5. `DocumentService.upload(...)`: accept and set `expiryDate` on the new `Document`.
- [ ] B6. `DocumentRepository`: add `findDueForReminder(thresholdDays, targetDate)` and the HR-notification query (name per 0c).
- [ ] B7. New `DocumentExpiryReminderService`: `sendDueReminders()` (employee reminders, all three thresholds) + `notifyHrForNewlyExpiredVerifiedDocuments(today)`. Constructor-inject a `Clock` (per 0a).
- [ ] B8. `Application.java`: add `@EnableScheduling`.
- [ ] B9. Wire the `@Scheduled` entry point (directly on the service or a thin wrapper, per design's open question 2) with `app.document-expiry-reminder.cron` defaulting to daily 06:00.

### Security
- [ ] B10. Confirm no new `SecurityConfig` matcher is needed for the `upload` param change (it isn't — same endpoint, same rule). If R5's hrdashboard visibility ends up needing a new endpoint instead of a client-side filter (see design's frontend section — default plan is client-side, no new endpoint), add its explicit role matcher then; do not let it fall through to the catch-all.

### Backend tests
- [ ] B11. `DocumentExpiryReminderServiceTest` (new): fixed `Clock`, one test per threshold firing exactly once, one test proving a document with a non-null reminder column for that threshold is excluded (mock repository call params/return), one test for the HR-newly-expired path, one test proving in-app notification fires regardless of `app.mail.enabled`, one test proving email is attempted only when mail is enabled (mock `EmailService`, verify called vs not-called).
- [ ] B12. `DocumentServiceTest`: extend `upload` tests for `expiryDate` present/absent.
- [ ] B13. `DocumentRepositoryIT` (or equivalent per 0b): `findDueForReminder` and the HR query, against real Postgres.
- [ ] B14. `DocumentControllerTest`: extend `upload` test for the new param + the `@FutureOrPresent` 400 case (backfills a pre-existing gap — no test existed for `upload` before this spec).
- [ ] B15. `mvnw verify` green.

## Frontend — employeehub
- [ ] F1. `EmployeeService.js`: `uploadDocument` gains optional `expiryDate` param.
- [ ] F2. `DocumentsPage.jsx`: upload form gains optional expiry date input; "My Documents" table gains "Expires" column + "Expired" badge.
- [ ] F3. `DashboardPage.jsx`: new conditional stat-card, "Documents Expiring Soon" (own documents only), hidden when count is zero, links to `/documents`.
- [ ] F4. `npm run build` clean.

## Frontend — hrdashboard
- [ ] F5. `DocumentsPage.jsx`: "Expires" column + "Expired" badge (reuse the same visual treatment as F2).
- [ ] F6. Add a client-side "expiring/expired only" filter toggle over the existing `getAllDocuments()` data (no new backend endpoint per the design's default plan).
- [ ] F7. `npm run build` clean.

## Verify + wrap-up
- [ ] V1. `mvnw verify` (backend) + both frontend builds green.
- [ ] V2. Manual smoke: upload a document with an expiry date 5 days out, manually invoke the reminder job (or temporarily lower the cron / call the service method directly in a throwaway test/endpoint — decide at implementation time), confirm exactly one 7-day-threshold notification appears, confirm re-running does not duplicate it.
- [ ] V3. Manual smoke: mark a document `VERIFIED`, set its `expiryDate` to today, run the job, confirm HR/Admin accounts receive the "verified document expired" notification.
- [ ] V4. Open PR (title under 70 chars, e.g. "Document expiration tracking + renewal reminders"); PR description states what was tested, matching the format used for PRs #19–22 this session.
- [ ] V5. On merge: update `docs/next-gen-features.md` — mark Feature 7 sub-item 1 done (leave sub-items 2-4 as not-started, since they're explicitly out of scope here), add a Progress Log row.

## Definition of Done
- Documents can optionally carry an expiry date, validated not-in-the-past at upload.
- A daily job sends exactly one notification (+ optional email) per document per threshold (30/14/7 days), never duplicating, surviving restarts (state is in the DB, not memory).
- HR/Admin are notified when a previously-verified document crosses its expiry date.
- Both frontends show expiry state and an "Expired" badge; employee dashboard flags the employee's own soon-to-expire documents; HR can filter to expiring/expired documents org-wide.
- No new object-level-authorization gap introduced (R6); the pre-existing gaps noted in requirements are left alone.
- Full test suite green; new queries and the new service have dedicated tests.

## Out of scope (unchanged from requirements.md)
Digital signatures, OCR classification, document templates, access-control levels/secure sharing, and every "Related pre-existing gap" listed in requirements.md.
