package employeehub.dto;

import employeehub.domain.Notification;
import employeehub.domain.enums.NotificationType;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Safe projection of {@link Notification}. The lazy {@code recipient}
 * association is replaced with just the recipient id (a notification is always
 * fetched for the current user, so no name is needed) — no entity graph,
 * {@code idNumber}, or {@code password} escapes the serializer.
 */
@Getter
public class NotificationResponse {

    private final String id;
    private final String recipientId;
    private final String title;
    private final String message;
    private final NotificationType type;
    private final Boolean isRead;
    private final String relatedEntityType;
    private final String relatedEntityId;
    private final LocalDateTime createdAt;

    public NotificationResponse(Notification n) {
        this.id = n.getId();
        this.recipientId = n.getRecipient() != null ? n.getRecipient().getId() : null;
        this.title = n.getTitle();
        this.message = n.getMessage();
        this.type = n.getType();
        this.isRead = n.getIsRead();
        this.relatedEntityType = n.getRelatedEntityType();
        this.relatedEntityId = n.getRelatedEntityId();
        this.createdAt = n.getCreatedAt();
    }

    public static List<NotificationResponse> from(List<Notification> notifications) {
        return notifications.stream().map(NotificationResponse::new).toList();
    }
}
