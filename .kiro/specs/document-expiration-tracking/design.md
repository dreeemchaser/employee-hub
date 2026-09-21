# Document Expiration Tracking — Design

## Overview

Add an optional `expiryDate` to `Document`, a daily `@Scheduled` job that finds documents crossing the 30/14/7-day-to-expiry thresholds (and documents that have gone from `VERIFIED` to expired) and fires notifications exactly once per threshold, and small UI additions on both frontends to surface expiry state. No new controller endpoints are strictly required for the reminder mechanism itself — the job is server-internal — but `DocumentResponse` and the upload request DTO change shape.

## Data model

### `Document` entity changes (`employeehub/domain/Document.java`)

Add four nullable columns:

```java
private LocalDate expiryDate;

// Set by the reminder job the first time each threshold fires for this
// document. Null = that threshold has not fired yet. Three separate columns
// (not a single "last reminder sent" timestamp) because a document could in
// theory sit at a threshold across multiple job runs before crossing to the
// next one, and because "which thresholds already fired" must survive a
// restart -- an in-memory set would not.
private LocalDateTime reminder30SentAt;
private LocalDateTime reminder14SentAt;
private LocalDateTime reminder7SentAt;
```

**Why four columns on `Document` rather than a separate `DocumentReminder` tracking table:** the cardinality is fixed (exactly 3 possible reminders per document, ever), the data is 1:1 with the document, and it is never queried independently of the document. A join table would be correct too, but is unjustified complexity for a fixed 3-slot flag set. Follows the same "flat field on the owning entity" style already used for `Employee.failedLoginAttempts`/`lockedUntil`.

**Not adding an `EXPIRED` value to `DocumentStatus`.** Per requirements R2, expiry is orthogonal to verification status — a document can be `VERIFIED` and expired at the same time (that is in fact the HR-relevant case in R3's last bullet). Overloading `status` with `EXPIRED` would make "is this expired" and "did HR verify it" mutually exclusive, which is factually wrong. `isExpired` stays a derived/computed value, never persisted (it changes automatically as time passes; persisting it would require its own scheduled job just to keep it in sync, which is exactly the kind of redundant state the "don't invent an in-memory-only solution" note in R4 was warning against in spirit — same failure mode, different direction).

### Migration — `V3__add_document_expiry_tracking.sql`

```sql
-- Document expiration tracking. expiry_date is optional (not every document
-- type expires); the three reminder_*_sent_at columns record the first time
-- each threshold's reminder fired for a document, so the daily job never
-- re-sends the same threshold (see DocumentExpiryReminderJob).
ALTER TABLE documents ADD COLUMN expiry_date date;
ALTER TABLE documents ADD COLUMN reminder_30_sent_at timestamp(6) without time zone;
ALTER TABLE documents ADD COLUMN reminder_14_sent_at timestamp(6) without time zone;
ALTER TABLE documents ADD COLUMN reminder_7_sent_at timestamp(6) without time zone;

-- The reminder job's query filters on expiry_date range and NULL-ness of the
-- reminder columns; this table will grow with every upload, so index the
-- column actually used for the WHERE clause range scan.
CREATE INDEX idx_documents_expiry_date ON documents (expiry_date);
```

All four new columns are nullable with no backfill needed — existing rows simply have no expiry date, which is correct (retroactively guessing an expiry date for already-uploaded documents is not this spec's job).

## Backend

### Request/response DTO changes

`DocumentUploadRequest` does not exist today (the controller takes `@RequestParam DocumentType type` + `MultipartFile` directly, no DTO). Introduce one so `expiryDate` has somewhere to live and gets Bean Validation:

```java
// employeehub/dto/DocumentUploadRequest.java
@Data
public class DocumentUploadRequest {
    @NotNull(message = "Document type is required")
    private DocumentType documentType;

    @FutureOrPresent(message = "Expiry date cannot be in the past")
    private LocalDate expiryDate; // nullable -- not every document expires
}
```

The controller's `@RequestParam` multipart pattern stays (file uploads with a JSON body in the same request need `multipart/form-data` either way); `expiryDate` and `documentType` become additional `@RequestParam`s validated manually in the service (the existing upload endpoint already takes `type` as a bare `@RequestParam`, not a `@RequestBody`, so introducing a `@Valid @RequestBody` DTO would change the request shape the frontend sends — out of scope to change that transport shape here). Concretely:

```java
@PostMapping("/upload")
public ResponseEntity<ApiResponse<DocumentResponse>> upload(
        @RequestParam DocumentType type,
        @RequestParam(required = false) @FutureOrPresent LocalDate expiryDate,
        @RequestParam("file") MultipartFile file,
        @AuthenticationPrincipal UserDetails userDetails) { ... }
```

`@FutureOrPresent` on a bare `@RequestParam` is validated by adding `@Validated` at the class level on `DocumentController` (Spring's method-parameter validation), not by introducing an unused DTO — simpler than fighting the existing multipart transport shape for one optional field.

`DocumentResponse` gains:
```java
private final LocalDate expiryDate;
private final boolean isExpired; // computed in the constructor, not stored

// in the constructor:
this.expiryDate = d.getExpiryDate();
this.isExpired = d.getExpiryDate() != null && d.getExpiryDate().isBefore(LocalDate.now());
```

### `DocumentService` changes

`upload(...)` gains an `expiryDate` parameter, sets it on the new `Document` if present. No other existing method changes.

### The reminder job

New class, `employeehub/service/DocumentExpiryReminderService.java` (a `@Service`, not tucked inside `DocumentService` — it has its own dependencies (`EmailService`, a new repository query, a `Clock`) and a distinct, schedulable entry point; keeping it separate matches the "one area per service" convention rather than growing `DocumentService` into two concerns).

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentExpiryReminderService {

    private final DocumentRepository documentRepository;
    private final NotificationService notificationService;
    private final EmailService emailService;
    private final EmployeeRepository employeeRepository; // to resolve HR recipients, see below
    private final Clock clock;

    private static final int[] THRESHOLDS_DAYS = {30, 14, 7};

    @Transactional
    public void sendDueReminders() {
        LocalDate today = LocalDate.now(clock);
        for (int thresholdDays : THRESHOLDS_DAYS) {
            LocalDate targetDate = today.plusDays(thresholdDays);
            List<Document> due = documentRepository.findDueForReminder(thresholdDays, targetDate);
            for (Document doc : due) {
                sendReminder(doc, thresholdDays);
            }
        }
        notifyHrForNewlyExpiredVerifiedDocuments(today);
    }

    private void sendReminder(Document doc, int thresholdDays) {
        notificationService.send(doc.getEmployee(), "Document Expiring Soon",
                String.format("Your %s expires on %s (%d days from now).",
                        doc.getDocumentType(), doc.getExpiryDate(), thresholdDays),
                NotificationType.DOCUMENT, "Document", doc.getId());

        emailService.sendPlainText(doc.getEmployee().getEmail(), "Document Expiring Soon",
                String.format("Your %s expires on %s. Please arrange renewal.",
                        doc.getDocumentType(), doc.getExpiryDate()));

        markThresholdSent(doc, thresholdDays);
        documentRepository.save(doc);
    }

    private void markThresholdSent(Document doc, int thresholdDays) {
        LocalDateTime now = LocalDateTime.now(clock);
        switch (thresholdDays) {
            case 30 -> doc.setReminder30SentAt(now);
            case 14 -> doc.setReminder14SentAt(now);
            case 7  -> doc.setReminder7SentAt(now);
            default -> throw new IllegalStateException("Unknown threshold: " + thresholdDays);
        }
    }
    // notifyHrForNewlyExpiredVerifiedDocuments(...) below, after the HR-recipient decision
}
```

**Why a `Clock` bean is injected rather than calling `LocalDate.now()` directly:** the coding-best-practices testing conventions explicitly require this ("inject a `Clock` where time matters") so the reminder-threshold logic is testable without waiting for real dates to pass. Add a single `@Bean Clock systemClock() { return Clock.systemDefaultZone(); }` in `Config.java` (the existing general-purpose config class) if one doesn't already exist — confirm during implementation; if some other service already defines a `Clock` bean, reuse it rather than declaring a duplicate.

**Idempotency:** `findDueForReminder(thresholdDays, targetDate)` (new repository query, below) only returns documents whose corresponding `reminder{N}SentAt` column is still null, so a document already reminded at a threshold is excluded from every subsequent run automatically — the persisted timestamp *is* the guard, not a separate "already sent" boolean.

### `DocumentRepository` addition

```java
// One query per threshold column, since each checks a different reminder-sent
// column being null. A single parameterized query would need dynamic column
// selection (not expressible in JPQL without three separate query strings
// anyway), so three explicit named queries are clearer than one cleverer one.
@Query("SELECT d FROM Document d WHERE d.expiryDate = :targetDate AND " +
       "((:thresholdDays = 30 AND d.reminder30SentAt IS NULL) OR " +
       " (:thresholdDays = 14 AND d.reminder14SentAt IS NULL) OR " +
       " (:thresholdDays = 7  AND d.reminder7SentAt  IS NULL))")
List<Document> findDueForReminder(@Param("thresholdDays") int thresholdDays, @Param("targetDate") LocalDate targetDate);
```

This matches on `expiryDate = targetDate` exactly (today + N days), not a range — the job is expected to run daily, so a document expiring in exactly 30 days today will be exactly 29 days away tomorrow; matching the exact date per threshold, run daily, catches every document exactly once per threshold without needing a range query. **Design tradeoff, stated explicitly:** if the job fails to run on a given day (deploy downtime, etc.), a document that would have matched that day's exact-date query is never caught retroactively at that threshold — it simply gets caught at the *next* threshold instead (e.g. missed the 14-day reminder, still gets the 7-day one), or not at all if it was the 7-day threshold that was missed. This is judged acceptable: the alternative (a `<=` range query) would need the reminder-sent check to also become a range/"has this threshold fired yet regardless of exact day" check, which the null-column check already does correctly for the exact-match case and would need to change to a "was thresholdDays >= days-remaining and reminder null" comparison for the range case. If missed-run recovery becomes a real problem, that's a follow-up change to the query's date comparison (`<=` instead of `=`), not a reason to block this spec.

### HR notification for newly-expired verified documents (R3, last bullet)

**Design decision:** notify every `HR_ADMIN` and `SUPER_ADMIN` employee, not a single "HR mailbox" concept — there is no such single recipient in this system today (no distribution-list entity, no "HR team" grouping beyond the role itself), and `NotificationService.send` takes one `Employee` recipient per call, so this means one notification per HR/Admin user per newly-expired document. Given the expected small number of HR/Admin accounts (per the seed data: 2 — `alexa.morgan`, plus `admin`), this is not a fan-out problem worth engineering around; if the organization's HR/Admin headcount grows enough that this matters, that's a "add a digest" follow-up, not a blocker now.

```java
private void notifyHrForNewlyExpiredVerifiedDocuments(LocalDate today) {
    List<Document> newlyExpired = documentRepository
            .findByStatusAndExpiryDateAndExpiryNotifiedFalse(DocumentStatus.VERIFIED, today);
    // ^ see note below -- this needs its own "already notified HR" guard,
    // reusing the existing reminder columns would be wrong (different
    // recipient, different meaning). Add one more nullable column,
    // hr_expiry_notified_at, for this specific case.
    List<Employee> hrRecipients = employeeRepository.findByRoleIn(List.of(Role.HR_ADMIN, Role.SUPER_ADMIN));
    for (Document doc : newlyExpired) {
        for (Employee hr : hrRecipients) {
            notificationService.send(hr, "Verified Document Expired",
                    String.format("%s %s's %s expired on %s.",
                            doc.getEmployee().getFirstName(), doc.getEmployee().getLastName(),
                            doc.getDocumentType(), doc.getExpiryDate()),
                    NotificationType.DOCUMENT, "Document", doc.getId());
        }
        doc.setHrExpiryNotifiedAt(LocalDateTime.now(clock));
        documentRepository.save(doc);
    }
}
```

This needs a fifth new column, `hrExpiryNotifiedAt` (nullable timestamp), distinct from the three employee-facing reminder columns — it tracks a different event (HR being told a *verified* document *has* expired, past tense) from the employee reminders (which fire *before* expiry). Add it to the same migration.

Repository query for this:
```java
@Query("SELECT d FROM Document d WHERE d.status = :status AND d.expiryDate = :today AND d.hrExpiryNotifiedAt IS NULL")
List<Document> findByStatusAndExpiryDateAndExpiryNotifiedFalse(@Param("status") DocumentStatus status, @Param("today") LocalDate today);
```
(Method name kept descriptive-but-long per the existing derived-query convention seen elsewhere in the codebase; alternatively name it `findNewlyExpiredVerified` — decide at implementation time, either is fine, prefer whichever reads better next to the other query.)

### Scheduling wiring

```java
// Application.java
@SpringBootApplication
@EnableSpringDataWebSupport(...)
@EnableScheduling   // new
public class Application { ... }
```

```java
// DocumentExpiryReminderService.java, or a thin separate @Component wrapper —
// decide at implementation time; a wrapper keeps the @Scheduled cron config
// out of the service's own testable method signature, which matters because
// sendDueReminders() itself must stay directly unit-testable without Spring's
// scheduler infrastructure involved.
@Scheduled(cron = "${app.document-expiry-reminder.cron:0 0 6 * * *}")
public void run() {
    sendDueReminders();
}
```

Runs daily at 06:00 server time by default, overridable via `app.document-expiry-reminder.cron` (following the existing `@Value("${app...")` config-property convention used elsewhere, e.g. `app.mail.enabled`). No new dependency needed — `@Scheduled`/`@EnableScheduling` are part of `spring-context`, already on the classpath transitively via `spring-boot-starter`.

### Security

No new endpoint is introduced by the reminder mechanism itself (it's a scheduled job, not HTTP-triggered). The `upload` endpoint's request shape changes (new optional `expiryDate` param) but its existing `SecurityConfig` rule (any authenticated user may upload their own document) is unchanged — no new matcher needed.

If design/implementation later decides to add an explicit "documents expiring soon" list endpoint (for R5's HR-side dashboard visibility) rather than reusing `GET /documents` with a new filter param, that new endpoint needs its own explicit `SecurityConfig` matcher (`HR_ADMIN`/`SUPER_ADMIN`) — do not let it fall through to the authenticated-catch-all, repeating the exact mistake already flagged as a pre-existing gap on `/documents/{id}/download`. See "Frontend — hrdashboard" below for the concrete choice.

## Frontend

### `employeehub`

- `DocumentsPage.jsx`: upload form gains an optional date input for `expiryDate`, only shown/relevant for document types that plausibly expire (`ID`, `CERTIFICATE`) — per requirements this is a UI nudge, not a backend restriction; the field stays optional for any type. "My Documents" table gains an "Expires" column showing the date, with an "Expired" badge (reusing the existing `badge--rejected`-style red badge class) when `isExpired` is true.
- `DashboardPage.jsx`: one new small stat/indicator, following the existing `stat-card` pattern already on that page (see the Attendance clock-in widget added earlier this session for the most recent example of extending this page), showing a count of the employee's own documents expiring within 30 days or already expired. Clicking it navigates to `/documents`. If the count is zero, do not show the card at all (matches the existing "hrAdmin && ..." conditional-card pattern already in `STAT_CARDS`).
- `EmployeeService.js`: `uploadDocument(type, file)` gains an optional third parameter, `expiryDate`, appended as a query param only when present (mirrors the existing `getEmployees` optional-filter pattern of only appending params that are actually set).

### `hrdashboard`

- `DocumentsPage.jsx`: table gains an "Expires" column + "Expired" badge, same visual treatment as `employeehub`.
- For R5's "HR sees all documents expiring soon across the org": add a simple client-side filter/toggle ("Show expiring/expired only") over the data `GET /documents` already returns, rather than a new backend endpoint — `GET /documents` already returns every document's (new) `expiryDate`/`isExpired` fields to an HR caller, so no new query is needed for this specific requirement. This keeps R5 a frontend-only change. (This is distinct from the reminder job's own repository queries, which run entirely server-side and are not exposed via any endpoint.)

## Testing

- `DocumentExpiryReminderServiceTest` (new, Mockito, mirrors `AttendanceServiceTest`'s style from this session): inject a fixed `Clock`, verify each threshold fires exactly once, verify a document already reminded at a threshold is excluded (mock the repository to prove the query params, and separately prove `markThresholdSent`+idempotency via a two-call test: call `sendDueReminders()` twice with the repository mock returning the same due-list both times only if the "already sent" flag wasn't actually saved — i.e. assert `documentRepository.save` was called with a `Document` whose reminder column is now non-null, and that a *second* invocation querying with the real (non-mocked) null-check semantics would not return it — this second part is closer to an integration-style assertion; keep the pure-unit test focused on "one call sends the notification and marks the column", and prove true end-to-end idempotency via a `@DataJpaTest` if the Testcontainers tier from `best-practices-testing` is available in this environment).
- `DocumentServiceTest`: extend the existing `upload` test(s) to cover the new optional `expiryDate` parameter (present and absent cases).
- `DocumentRepositoryIT` (new, if the Testcontainers tier is set up — reuse `AbstractRepositoryIT` if it exists from the `best-practices-testing` spec): the new `findDueForReminder` and HR-notification queries, per the "every custom `@Query` needs a test against real Postgres" rule.
- `DocumentControllerTest`: extend the `upload` test to cover the new `expiryDate` request param, including the `@FutureOrPresent` validation failure (400) case — this also backfills the pre-existing gap noted in requirements ("no test for `upload` today").
- Frontend: manual verification only, per this project's existing frontend-testing conventions (no RTL test suite convention exists yet for `DocumentsPage.jsx` in either app) — do not introduce a new one unasked.

## Open questions for implementation time (not blocking design sign-off)

1. Exact cron expression / whether 06:00 server time is actually desired — placeholder default given, trivially changed via the `app.document-expiry-reminder.cron` property.
2. Whether the `@Scheduled` method lives directly on `DocumentExpiryReminderService` or a thin wrapper `@Component` — noted as a decide-at-implementation-time detail above, does not change the testable core logic either way.
3. Whether `findDueForReminder`'s three-way `OR` JPQL is clearer as three separate repository methods (`findDueFor30DayReminder`, etc.) instead of one parameterized method — functionally identical, pick whichever reads better once written out; flagged here only so it isn't relitigated as a "why did you do it this way" review comment without context.
