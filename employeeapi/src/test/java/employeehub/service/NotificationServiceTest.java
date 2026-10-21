package employeehub.service;

import employeehub.domain.Employee;
import employeehub.domain.Notification;
import employeehub.domain.NotificationPreference;
import employeehub.domain.enums.NotificationType;
import employeehub.repository.EmployeeRepository;
import employeehub.repository.NotificationPreferenceRepository;
import employeehub.exception.ResourceNotFoundException;
import employeehub.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock NotificationRepository notificationRepository;
    @Mock NotificationPreferenceRepository preferenceRepository;
    @Mock EmployeeRepository employeeRepository;
    @Mock EmailService emailService;

    @InjectMocks NotificationService notificationService;

    private Employee recipient;
    private Notification notification;

    @BeforeEach
    void setUp() {
        recipient = new Employee();
        recipient.setId("emp-1");

        notification = new Notification();
        notification.setId("notif-1");
        notification.setRecipient(recipient);
        notification.setTitle("Test");
        notification.setMessage("Test message");
        notification.setIsRead(false);
    }

    @Test
    void send_shouldSaveNotification() {
        notificationService.send(recipient, "Title", "Message",
                NotificationType.LEAVE, "LeaveRequest", "req-1");

        verify(notificationRepository).save(argThat(n ->
                n.getRecipient().equals(recipient) &&
                n.getTitle().equals("Title") &&
                !n.getIsRead()
        ));
        verifyNoInteractions(emailService);
    }

    @Test
    void getMy_shouldReturnNotificationsForRecipient() {
        when(notificationRepository.findByRecipientIdOrderByCreatedAtDesc("emp-1"))
                .thenReturn(List.of(notification));

        List<Notification> result = notificationService.getMy("emp-1");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTitle()).isEqualTo("Test");
    }

    @Test
    void markAsRead_shouldSetIsReadTrue() {
        when(notificationRepository.findById("notif-1")).thenReturn(Optional.of(notification));
        when(notificationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Notification result = notificationService.markAsRead("notif-1", "emp-1");

        assertThat(result.getIsRead()).isTrue();
    }

    @Test
    void markAsRead_shouldThrow_whenNotOwner() {
        when(notificationRepository.findById("notif-1")).thenReturn(Optional.of(notification));

        assertThatThrownBy(() -> notificationService.markAsRead("notif-1", "other-emp"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("your own notifications");
    }

    @Test
    void markAsRead_shouldThrow_whenNotFound() {
        when(notificationRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.markAsRead("missing", "emp-1"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void markAllAsRead_shouldCallRepository() {
        notificationService.markAllAsRead("emp-1");

        verify(notificationRepository).markAllAsRead("emp-1");
    }

    @Test
    void send_shouldEmailWhenPreferenceEnabled() {
        NotificationPreference preference = new NotificationPreference();
        preference.setEmailEnabled(true);
        when(preferenceRepository.findByEmployeeIdAndNotificationType("emp-1", NotificationType.LEAVE))
                .thenReturn(Optional.of(preference));
        recipient.setEmail("employee@example.com");

        notificationService.send(recipient, "Title", "Message",
                NotificationType.LEAVE, "LeaveRequest", "req-1");

        verify(emailService).sendPlainText("employee@example.com", "Title", "Message");
    }

    @Test
    void getMyPreferences_shouldReturnDefaultsForEveryType() {
        when(preferenceRepository.findByEmployeeIdOrderByNotificationType("emp-1"))
                .thenReturn(List.of());

        List<NotificationPreference> result = notificationService.getMyPreferences("emp-1");

        assertThat(result).extracting(NotificationPreference::getNotificationType)
                .containsExactly(NotificationType.values());
        assertThat(result).allMatch(preference -> !preference.isEmailEnabled());
    }

    @Test
    void updatePreference_shouldPersistForCurrentEmployee() {
        when(employeeRepository.findById("emp-1")).thenReturn(Optional.of(recipient));
        when(preferenceRepository.findByEmployeeIdAndNotificationType("emp-1", NotificationType.LEAVE))
                .thenReturn(Optional.empty());
        when(preferenceRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        NotificationPreference result = notificationService.updatePreference(
                "emp-1", NotificationType.LEAVE, true);

        assertThat(result.isEmailEnabled()).isTrue();
        assertThat(result.getEmployee()).isEqualTo(recipient);
    }
}
