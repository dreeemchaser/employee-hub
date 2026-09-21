package employeehub.service;

import employeehub.domain.Document;
import employeehub.domain.Employee;
import employeehub.domain.enums.DocumentStatus;
import employeehub.domain.enums.DocumentType;
import employeehub.domain.enums.Role;
import employeehub.repository.DocumentRepository;
import employeehub.repository.EmployeeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentExpiryReminderServiceTest {

    // Fixed "now" so threshold math (today + 30/14/7 days) is deterministic.
    private static final LocalDate TODAY = LocalDate.of(2026, 6, 15);
    private static final Clock FIXED_CLOCK =
            Clock.fixed(TODAY.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC);

    @Mock DocumentRepository documentRepository;
    @Mock EmployeeRepository employeeRepository;
    @Mock NotificationService notificationService;
    @Mock EmailService emailService;

    DocumentExpiryReminderService reminderService;

    private Employee employee;
    private Document document;

    @BeforeEach
    void setUp() {
        employee = new Employee();
        employee.setId("emp-1");
        employee.setFirstName("Sandy");
        employee.setLastName("Brooks");
        employee.setEmail("sandy.brooks@employeehub.com");

        document = new Document();
        document.setId("doc-1");
        document.setEmployee(employee);
        document.setDocumentType(DocumentType.ID);
        document.setStatus(DocumentStatus.PENDING);

        reminderService = new DocumentExpiryReminderService(
                documentRepository, employeeRepository, notificationService, emailService, FIXED_CLOCK);
    }

    @Test
    void sendDueReminders_shouldNotifyAndMarkThreshold_forEachDueDocument() {
        document.setExpiryDate(TODAY.plusDays(30));
        when(documentRepository.findDueForReminder(30, TODAY.plusDays(30))).thenReturn(List.of(document));
        when(documentRepository.findDueForReminder(14, TODAY.plusDays(14))).thenReturn(List.of());
        when(documentRepository.findDueForReminder(7, TODAY.plusDays(7))).thenReturn(List.of());

        reminderService.sendDueReminders();

        verify(notificationService).send(eq(employee), anyString(), anyString(), any(), eq("Document"), eq("doc-1"));
        assertThat(document.getReminderSentAt30()).isNotNull();
        assertThat(document.getReminderSentAt14()).isNull();
        assertThat(document.getReminderSentAt7()).isNull();
        verify(documentRepository).save(document);
    }

    @Test
    void sendDueReminders_shouldQueryEachThresholdIndependently() {
        when(documentRepository.findDueForReminder(any(Integer.class), any())).thenReturn(List.of());

        reminderService.sendDueReminders();

        verify(documentRepository).findDueForReminder(30, TODAY.plusDays(30));
        verify(documentRepository).findDueForReminder(14, TODAY.plusDays(14));
        verify(documentRepository).findDueForReminder(7, TODAY.plusDays(7));
    }

    @Test
    void sendDueReminders_shouldAlwaysSendInAppNotification_regardlessOfEmailOutcome() {
        document.setExpiryDate(TODAY.plusDays(7));
        when(documentRepository.findDueForReminder(30, TODAY.plusDays(30))).thenReturn(List.of());
        when(documentRepository.findDueForReminder(14, TODAY.plusDays(14))).thenReturn(List.of());
        when(documentRepository.findDueForReminder(7, TODAY.plusDays(7))).thenReturn(List.of(document));

        reminderService.sendDueReminders();

        // EmailService.sendPlainText is safe to call unconditionally -- it no-ops internally when
        // mail is disabled -- so the reminder service always calls it; the in-app notification is
        // the channel that must never depend on mail being configured (per requirements R3).
        verify(notificationService).send(eq(employee), anyString(), anyString(), any(), anyString(), any());
        verify(emailService).sendPlainText(eq("sandy.brooks@employeehub.com"), anyString(), anyString());
    }

    @Test
    void notifyHrForNewlyExpiredVerifiedDocuments_shouldNotifyEveryHrAndAdminAccount() {
        document.setStatus(DocumentStatus.VERIFIED);
        document.setExpiryDate(TODAY);

        Employee hrAdmin = new Employee();
        hrAdmin.setId("hr-1");
        Employee superAdmin = new Employee();
        superAdmin.setId("admin-1");

        when(documentRepository.findNewlyExpiredVerified(DocumentStatus.VERIFIED, TODAY)).thenReturn(List.of(document));
        when(employeeRepository.findByRoleIn(List.of(Role.HR_ADMIN, Role.SUPER_ADMIN)))
                .thenReturn(List.of(hrAdmin, superAdmin));

        reminderService.notifyHrForNewlyExpiredVerifiedDocuments();

        verify(notificationService).send(eq(hrAdmin), anyString(), anyString(), any(), anyString(), any());
        verify(notificationService).send(eq(superAdmin), anyString(), anyString(), any(), anyString(), any());
        verify(notificationService, times(2)).send(any(), anyString(), anyString(), any(), anyString(), any());
        assertThat(document.getHrExpiryNotifiedAt()).isNotNull();
    }

    @Test
    void notifyHrForNewlyExpiredVerifiedDocuments_shouldDoNothing_whenNoneNewlyExpired() {
        when(documentRepository.findNewlyExpiredVerified(DocumentStatus.VERIFIED, TODAY)).thenReturn(List.of());

        reminderService.notifyHrForNewlyExpiredVerifiedDocuments();

        verify(employeeRepository, never()).findByRoleIn(any());
        verify(documentRepository, never()).save(any());
    }
}
