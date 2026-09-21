package employeehub.service;

import employeehub.domain.Document;
import employeehub.domain.Employee;
import employeehub.domain.enums.DocumentStatus;
import employeehub.domain.enums.NotificationType;
import employeehub.domain.enums.Role;
import employeehub.repository.DocumentRepository;
import employeehub.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Sends renewal reminders for documents approaching their {@code expiryDate},
 * and notifies HR/Admin when a {@code VERIFIED} document's expiry date has
 * passed. Intended to run once a day (see the {@code @Scheduled} entry point
 * on {@link Application}) but every method here is independently callable and
 * idempotent — running {@link #sendDueReminders()} twice in the same day sends
 * no duplicate notifications, because the "already sent" state lives in the
 * database ({@code Document.reminderSentAt30} etc.), not in memory.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentExpiryReminderService {

    private static final int[] THRESHOLD_DAYS = {30, 14, 7};

    private final DocumentRepository documentRepository;
    private final EmployeeRepository employeeRepository;
    private final NotificationService notificationService;
    private final EmailService emailService;
    private final Clock clock;

    /**
     * Scheduled entry point, daily at 06:00 server time by default (override
     * via {@code app.document-expiry-reminder.cron}). Delegates to the two
     * independently-testable methods below rather than containing any logic
     * itself, so tests never need Spring's scheduler infrastructure.
     */
    @Scheduled(cron = "${app.document-expiry-reminder.cron:0 0 6 * * *}")
    public void runScheduledReminders() {
        log.info("Running document expiry reminder job");
        sendDueReminders();
        notifyHrForNewlyExpiredVerifiedDocuments();
    }

    @Transactional
    public void sendDueReminders() {
        LocalDate today = LocalDate.now(clock);
        for (int thresholdDays : THRESHOLD_DAYS) {
            LocalDate targetDate = today.plusDays(thresholdDays);
            List<Document> due = documentRepository.findDueForReminder(thresholdDays, targetDate);
            for (Document doc : due) {
                sendReminder(doc, thresholdDays);
            }
        }
    }

    @Transactional
    public void notifyHrForNewlyExpiredVerifiedDocuments() {
        LocalDate today = LocalDate.now(clock);
        List<Document> newlyExpired = documentRepository.findNewlyExpiredVerified(DocumentStatus.VERIFIED, today);
        if (newlyExpired.isEmpty()) {
            return;
        }
        List<Employee> hrRecipients = employeeRepository.findByRoleIn(List.of(Role.HR_ADMIN, Role.SUPER_ADMIN));
        for (Document doc : newlyExpired) {
            for (Employee hr : hrRecipients) {
                notificationService.send(hr,
                        "Verified Document Expired",
                        doc.getEmployee().getFirstName() + " " + doc.getEmployee().getLastName()
                                + "'s " + doc.getDocumentType() + " expired on " + doc.getExpiryDate() + ".",
                        NotificationType.DOCUMENT, "Document", doc.getId());
            }
            doc.setHrExpiryNotifiedAt(LocalDateTime.now(clock));
            documentRepository.save(doc);
        }
    }

    private void sendReminder(Document doc, int thresholdDays) {
        Employee employee = doc.getEmployee();

        notificationService.send(employee,
                "Document Expiring Soon",
                "Your " + doc.getDocumentType() + " expires on " + doc.getExpiryDate()
                        + " (" + thresholdDays + " days from now).",
                NotificationType.DOCUMENT, "Document", doc.getId());

        emailService.sendPlainText(employee.getEmail(),
                "Document Expiring Soon",
                "Your " + doc.getDocumentType() + " expires on " + doc.getExpiryDate()
                        + ". Please arrange renewal.");

        markThresholdSent(doc, thresholdDays);
        documentRepository.save(doc);
    }

    private void markThresholdSent(Document doc, int thresholdDays) {
        LocalDateTime now = LocalDateTime.now(clock);
        switch (thresholdDays) {
            case 30 -> doc.setReminderSentAt30(now);
            case 14 -> doc.setReminderSentAt14(now);
            case 7 -> doc.setReminderSentAt7(now);
            default -> throw new IllegalStateException("Unknown reminder threshold: " + thresholdDays);
        }
    }
}
