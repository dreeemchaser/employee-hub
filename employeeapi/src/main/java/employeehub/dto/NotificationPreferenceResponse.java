package employeehub.dto;

import employeehub.domain.NotificationPreference;
import employeehub.domain.enums.NotificationType;
import lombok.Getter;

import java.util.List;

@Getter
public class NotificationPreferenceResponse {

    private final NotificationType notificationType;
    private final boolean emailEnabled;

    public NotificationPreferenceResponse(NotificationType notificationType, boolean emailEnabled) {
        this.notificationType = notificationType;
        this.emailEnabled = emailEnabled;
    }

    public static NotificationPreferenceResponse from(NotificationPreference preference) {
        return new NotificationPreferenceResponse(preference.getNotificationType(), preference.isEmailEnabled());
    }

    public static List<NotificationPreferenceResponse> from(List<NotificationPreference> preferences) {
        return preferences.stream().map(NotificationPreferenceResponse::from).toList();
    }
}
