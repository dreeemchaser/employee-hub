package employeehub.repository;

import employeehub.domain.Department;
import employeehub.domain.Document;
import employeehub.domain.Employee;
import employeehub.domain.Team;
import employeehub.domain.enums.DocumentStatus;
import employeehub.domain.enums.DocumentType;
import employeehub.domain.enums.Role;
import employeehub.repository.support.EntityFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentRepositoryIT extends AbstractRepositoryIT {

    @Autowired
    TestEntityManager em;

    @Autowired
    DocumentRepository documentRepository;

    private Employee employee;

    @BeforeEach
    void setUp() {
        Department dept = em.persist(EntityFactory.department("Dept"));
        Team team = em.persist(EntityFactory.team("Team", dept));
        employee = EntityFactory.employee("EMP-001", "doc-repo-it@x.com", dept, team);
        em.persist(employee);
        em.flush();
    }

    @Test
    void findDueForReminder_returnsDocumentAtExactThreshold_whenNotYetReminded() {
        LocalDate targetDate = LocalDate.of(2026, 7, 15);
        Document due = EntityFactory.document(employee, DocumentType.ID);
        due.setExpiryDate(targetDate);
        em.persist(due);
        em.flush();

        List<Document> result = documentRepository.findDueForReminder(30, targetDate);

        assertThat(result).extracting(Document::getId).containsExactly(due.getId());
    }

    @Test
    void findDueForReminder_excludesDocument_whenThresholdAlreadySent() {
        LocalDate targetDate = LocalDate.of(2026, 7, 15);
        Document alreadyReminded = EntityFactory.document(employee, DocumentType.ID);
        alreadyReminded.setExpiryDate(targetDate);
        alreadyReminded.setReminderSentAt30(LocalDateTime.of(2026, 6, 15, 6, 0));
        em.persist(alreadyReminded);
        em.flush();

        List<Document> result = documentRepository.findDueForReminder(30, targetDate);

        assertThat(result).isEmpty();
    }

    @Test
    void findDueForReminder_excludesDocument_whenExpiryDateDoesNotMatchTargetExactly() {
        Document notYetDue = EntityFactory.document(employee, DocumentType.ID);
        notYetDue.setExpiryDate(LocalDate.of(2026, 7, 16)); // one day off the target
        em.persist(notYetDue);
        em.flush();

        List<Document> result = documentRepository.findDueForReminder(30, LocalDate.of(2026, 7, 15));

        assertThat(result).isEmpty();
    }

    @Test
    void findDueForReminder_matchesOnlyItsOwnTargetDate_notADifferentThresholdsDate() {
        // Mirrors how DocumentExpiryReminderService actually calls this: each threshold computes its
        // own target date (today + thresholdDays), so a document sitting at the 14-day target date
        // must not also satisfy a query built for the 30-day target date, even though the reminder-
        // sent-column check for "30" would itself be true (never yet sent) -- the date has to match.
        LocalDate the14DayTarget = LocalDate.of(2026, 7, 15);
        LocalDate the30DayTarget = LocalDate.of(2026, 7, 31);
        Document due14 = EntityFactory.document(employee, DocumentType.CERTIFICATE);
        due14.setExpiryDate(the14DayTarget);
        em.persist(due14);
        em.flush();

        assertThat(documentRepository.findDueForReminder(14, the14DayTarget))
                .extracting(Document::getId).containsExactly(due14.getId());
        assertThat(documentRepository.findDueForReminder(30, the30DayTarget)).isEmpty();
        assertThat(documentRepository.findDueForReminder(7, LocalDate.of(2026, 7, 8))).isEmpty();
    }

    @Test
    void findNewlyExpiredVerified_returnsVerifiedDocumentExpiringToday_whenHrNotAlreadyNotified() {
        LocalDate today = LocalDate.of(2026, 7, 15);
        Document expired = EntityFactory.document(employee, DocumentType.ID);
        expired.setStatus(DocumentStatus.VERIFIED);
        expired.setExpiryDate(today);
        em.persist(expired);
        em.flush();

        List<Document> result = documentRepository.findNewlyExpiredVerified(DocumentStatus.VERIFIED, today);

        assertThat(result).extracting(Document::getId).containsExactly(expired.getId());
    }

    @Test
    void findNewlyExpiredVerified_excludesDocument_whenHrAlreadyNotified() {
        LocalDate today = LocalDate.of(2026, 7, 15);
        Document alreadyNotified = EntityFactory.document(employee, DocumentType.ID);
        alreadyNotified.setStatus(DocumentStatus.VERIFIED);
        alreadyNotified.setExpiryDate(today);
        alreadyNotified.setHrExpiryNotifiedAt(LocalDateTime.of(2026, 7, 15, 6, 0));
        em.persist(alreadyNotified);
        em.flush();

        List<Document> result = documentRepository.findNewlyExpiredVerified(DocumentStatus.VERIFIED, today);

        assertThat(result).isEmpty();
    }

    @Test
    void findNewlyExpiredVerified_excludesDocument_whenStatusIsNotVerified() {
        LocalDate today = LocalDate.of(2026, 7, 15);
        Document expiredButPending = EntityFactory.document(employee, DocumentType.ID);
        expiredButPending.setStatus(DocumentStatus.PENDING);
        expiredButPending.setExpiryDate(today);
        em.persist(expiredButPending);
        em.flush();

        List<Document> result = documentRepository.findNewlyExpiredVerified(DocumentStatus.VERIFIED, today);

        assertThat(result).isEmpty();
    }
}
