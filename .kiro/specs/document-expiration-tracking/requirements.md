# Document Expiration Tracking — Requirements

**Source:** `docs/next-gen-features.md` Feature 7 ("Document Lifecycle Management"), sub-item 1 only ("Document Expiration Tracking"). Priority HIGH in that doc.
**Status:** Planning.
**Scope note:** Feature 7 as written in the roadmap also proposes digital signatures (DocuSign), OCR classification, and document template generation. None of that is in scope here — those are separate integrations/ML work, not a natural extension of this slice, and are listed explicitly under "Out of scope" below. This spec is the expiration/reminder sub-item only, sized the same way as the Attendance and Offboarding slices that already shipped.

## Facts verified in code this session

Grounding so the spec matches reality, not the aspirational roadmap doc:

1. **`Document` entity today** (`employeehub/domain/Document.java`) has no expiry-related field at all: `id`, `employee`, `documentType`, `fileName`, `fileUrl`, `fileSize`, `uploadedBy`, `verifiedBy`, `verifiedAt`, `status`, `createdAt`. No `updatedAt`, no `@Version`.
2. **`DocumentStatus`** enum is `PENDING, VERIFIED, REJECTED` — no `EXPIRED` value.
3. **`DocumentType`** enum is `ID, CONTRACT, CERTIFICATE, PAYSLIP, OTHER`.
4. **`DocumentService`** has no state-conflict guards today (no `BusinessRuleException` anywhere in it) — `verify`/`reject` can be called on an already-`VERIFIED`/`REJECTED` document with no error. `upload`/`getMy`/`getAll`/`download`/`getDocument` are plain reads or single-entity writes; only `verify`/`reject` are `@Transactional`.
5. **`DocumentRepository`** has exactly one query: `findByEmployeeId`. No status filter, no date filter, no pagination.
6. **`GET /documents`** (list-all, HR/Admin) returns a bare `List<DocumentResponse>`, not paginated — a pre-existing deviation from the "every collection endpoint accepts `Pageable`" rule. New/changed list endpoints in this spec must not repeat that mistake, but fixing the existing `GET /documents` is a separate call — see "Related pre-existing gaps" below.
7. **`GET /documents/{id}/download`** has no explicit role rule in `SecurityConfig` (falls through to the authenticated-catch-all) and **no object-level ownership check** in the service — any authenticated employee who knows another employee's document id can download it today. Pre-existing, out of scope to fix silently, but flagged because this spec's reminder emails will link back to a document (see R5) — that link must not make the gap worse.
8. **`EmailService.sendPlainText(to, subject, body)`** exists, is config-gated (`app.mail.enabled`, defaults `false`) and no-ops safely with no `JavaMailSender` configured — never throws to the caller.
9. **`NotificationType.DOCUMENT`** already exists and is what `verify`/`reject` notifications use today.
10. **No `@Scheduled` job and no `@EnableScheduling` exist anywhere in the codebase.** This spec introduces both from scratch — there is no existing pattern to mirror; the design must establish the convention.
11. **Latest migration is `V2__add_attendance_records.sql`.** This spec's migration is `V3__...`.
12. **Test coverage today:** `DocumentServiceTest` covers `upload`/`verify`/`reject`/`download`-not-found. `DocumentControllerTest` covers `getMy`, `getAll` (role-gated), `verify` (role-gated). No test for `upload`, `download`, or `reject` today — this spec's new/changed methods need full coverage; backfilling the two pre-existing gaps is not required but may be picked up opportunistically if touching those exact methods.
13. **Frontend today:** `employeehub/src/pages/DocumentsPage.jsx` shows "My Documents" (type, size, uploaded date, status) and an upload form. `hrdashboard/src/pages/DocumentsPage.jsx` shows a verify-only table (no reject button wired, even though the backend endpoint exists — pre-existing gap, not this spec's job to fix).
14. **Known pre-existing bug, not part of this spec:** `employeehub`'s `DocumentsPage.jsx` `DOC_TYPES` includes `'ID_DOCUMENT'`, but the backend enum value is `ID` — uploading with that type fails enum deserialization. Flagged for a separate one-line fix; do not silently bundle into this spec unless asked.

## Requirements

### R1 — Track an expiration date per document
- Add an `expiryDate` field to `Document` (nullable — not every document type expires; e.g. a payslip has no expiry, an ID document or work permit does).
- `DocumentType` values that typically expire (`ID`, `CERTIFICATE`) should allow the uploader to optionally supply an expiry date at upload time; `CONTRACT`/`PAYSLIP`/`OTHER` may also carry one if the uploader chooses to set it — do not hard-code which types can vs cannot have an expiry, just make the field optional for all types. (South African ID documents/permits are the concrete case driving this; smart-ID cards and passports have real expiry dates.)
- Expiry date, when present, must be in the future at upload time (Bean Validation `@FutureOrPresent` on the request DTO) — do not allow uploading an already-expired document as a fresh submission.

### R2 — Surface expiry state on read
- `DocumentResponse` includes `expiryDate` (nullable) and a derived `isExpired` boolean (computed from `expiryDate` vs "now", not persisted) so the frontend doesn't need to do date math.
- Documents past their `expiryDate` are visually distinguishable in both `employeehub`'s "My Documents" list and `hrdashboard`'s HR document list (e.g. an "Expired" badge), independent of `status` (a document can be `VERIFIED` and also expired — those are orthogonal: `status` is about HR's verification decision, expiry is about the document's real-world validity).

### R3 — Renewal reminder notifications
- A scheduled job identifies documents whose `expiryDate` falls within 30, 14, or 7 days from "now" (three distinct reminder points, matching the roadmap doc's wording) and have not already been reminded at that threshold.
- Each matching document triggers exactly one in-app notification (`NotificationService.send`, reusing `NotificationType.DOCUMENT`) to the document's owning employee, once per threshold (i.e. a document 10 days from expiry that already got its "14 days" reminder does not get reminded again until the "7 days" threshold, and never re-sends the same threshold twice).
- If `app.mail.enabled` is on, the same reminder also sends an email via `EmailService.sendPlainText` to the employee's email address. If mail is disabled (the default), the in-app notification alone still fires — this feature must not depend on mail being configured to have any effect.
- The job also notifies HR (send to... TBD in design — see Design open question) when a document with `status = VERIFIED` crosses its expiry date, since a verified-but-now-expired credential (e.g. a work permit) is an HR/compliance concern, not just the employee's.

### R4 — Track which reminders have already fired
- The design must prevent duplicate reminders at the same threshold across repeated job runs (the job may run daily; a document sitting at "12 days to expiry" must not get a fresh "14-day" reminder every day until it crosses to the 7-day threshold).
- This requires persisting which threshold(s) have already been sent per document — decide the exact mechanism in design (e.g. new columns on `Document`, or a small separate tracking table); do not invent an in-memory-only solution that resets on restart.

### R5 — Dashboard visibility
- `employeehub`'s dashboard (`DashboardPage.jsx`) gains a small "Expiring Documents" indicator for the current employee (mirroring the existing stat-card pattern already on that page) when the employee has any document expiring within 30 days or already expired.
- `hrdashboard` gains a way for HR to see all documents expiring soon / already expired across the organization (a filter on the existing Documents page, or a new tab — decide in design), since HR is the party responsible for compliance follow-up on expired credentials.

### R6 — Object-level authorization must not regress
- Any new endpoint or query this spec adds that takes a document id or lists documents must enforce the same ownership rule the rest of the Documents feature is supposed to have: an `EMPLOYEE` may only ever see their own documents; `HR_ADMIN`/`SUPER_ADMIN` may see all. Do not add a new endpoint that is *more* permissive than existing ones. (This does not require fixing the pre-existing `GET /documents/{id}/download` gap — see "Related pre-existing gaps" — but it must not add a second instance of the same class of bug.)

## Acceptance criteria

1. Uploading a document with an `expiryDate` in the past is rejected with a 400 (Bean Validation).
2. `GET /documents/my` and `GET /documents` (HR) both return `expiryDate` and a correctly-computed `isExpired` for every document.
3. A document with `expiryDate` 25 days from now, when the reminder job runs, produces exactly one notification tagged for the "30-day" threshold; running the job again the same day does not produce a second one.
4. A document that crosses from the 30-day threshold to the 14-day threshold between job runs produces a second, distinct notification for the 14-day threshold.
5. With `app.mail.enabled=false` (default), reminders still produce in-app notifications with no error; with it `true` and a mail sender configured, an email is also sent.
6. A `VERIFIED` document that passes its expiry date produces an HR-directed notification distinct from the employee-directed one (exact recipient decided in design).
7. An `EMPLOYEE` cannot trigger, query, or see another employee's expiry/reminder state through any new endpoint.
8. Full backend test suite (`mvnw verify`) green; new service methods and the scheduled job's core logic have unit tests; new endpoints (if any) have `@WebMvcTest` coverage.
9. Both frontends build cleanly; the new UI elements render correctly for at least one expired and one non-expired document in manual verification.

## Related pre-existing gaps (found, not fixed by this spec unless explicitly asked)

- `GET /documents/{id}/download` has no object-level ownership check and no explicit `SecurityConfig` role rule.
- `GET /documents` (HR list-all) is not paginated.
- `hrdashboard` has no reject button wired despite the backend endpoint existing.
- `employeehub`'s upload form sends `ID_DOCUMENT` but the backend enum is `ID`.
- `DocumentService.verify`/`reject` have no state-conflict guard (can re-verify/re-reject).

These are listed so they're not confused with new work, and so a reviewer knows they were seen and deliberately left alone. Flag to the user before fixing any of them as part of this spec — each is a small, separate, askable change.

## Out of scope

- Digital signature integration (DocuSign or similar) — Feature 7 sub-item 2.
- OCR-based document classification / data extraction — Feature 7 sub-item 3.
- Document templates and batch generation for new hires — Feature 7 sub-item 4.
- Document access control levels / secure sharing links — that's Feature 8, a separate roadmap item.
- Fixing any of the "Related pre-existing gaps" above.
