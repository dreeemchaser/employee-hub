package employeehub.service;

import employeehub.domain.Employee;
import employeehub.domain.Notification;
import employeehub.domain.NotificationPreference;
import employeehub.domain.enums.NotificationType;
import employeehub.exception.ResourceNotFoundException;
import employeehub.repository.EmployeeRepository;
import employeehub.repository.NotificationPreferenceRepository;
import employeehub.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Arrays;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationPreferenceRepository preferenceRepository;
    private final EmployeeRepository employeeRepository;
    private final EmailService emailService;

    public void send(Employee recipient, String title, String message, NotificationType type,
                     String relatedEntityType, String relatedEntityId) {
        Notification notification = new Notification();
        notification.setRecipient(recipient);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setType(type);
        notification.setRelatedEntityType(relatedEntityType);
        notification.setRelatedEntityId(relatedEntityId);
        notificationRepository.save(notification);
        preferenceRepository.findByEmployeeIdAndNotificationType(recipient.getId(), type)
                .filter(NotificationPreference::isEmailEnabled)
                .ifPresent(preference -> emailService.sendPlainText(
                        recipient.getEmail(), title, message));
    }

    public List<Notification> getMy(String recipientId) {
        return notificationRepository.findByRecipientIdOrderByCreatedAtDesc(recipientId);
    }

    @Transactional(readOnly = true)
    public List<NotificationPreference> getMyPreferences(String employeeId) {
        var existing = preferenceRepository.findByEmployeeIdOrderByNotificationType(employeeId);
        var byType = existing.stream().collect(Collectors.toMap(
                NotificationPreference::getNotificationType, preference -> preference));
        return Arrays.stream(NotificationType.values())
                .map(type -> byType.getOrDefault(type, defaultPreference(type)))
                .toList();
    }

    @Transactional
    public NotificationPreference updatePreference(String employeeId, NotificationType type,
                                                   boolean emailEnabled) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found: " + employeeId));
        NotificationPreference preference = preferenceRepository
                .findByEmployeeIdAndNotificationType(employeeId, type)
                .orElseGet(() -> {
                    NotificationPreference created = new NotificationPreference();
                    created.setEmployee(employee);
                    created.setNotificationType(type);
                    return created;
                });
        preference.setEmailEnabled(emailEnabled);
        return preferenceRepository.save(preference);
    }

    public Notification markAsRead(String notificationId, String recipientId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found: " + notificationId));
        if (!notification.getRecipient().getId().equals(recipientId)) {
            throw new IllegalArgumentException("You can only mark your own notifications as read");
        }
        notification.setIsRead(true);
        return notificationRepository.save(notification);
    }

    @Transactional
    public void markAllAsRead(String recipientId) {
        notificationRepository.markAllAsRead(recipientId);
    }

    private NotificationPreference defaultPreference(NotificationType type) {
        NotificationPreference preference = new NotificationPreference();
        preference.setNotificationType(type);
        preference.setEmailEnabled(false);
        return preference;
    }
}
