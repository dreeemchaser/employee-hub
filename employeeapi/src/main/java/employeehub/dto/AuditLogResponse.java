package employeehub.dto;

import employeehub.domain.AuditLog;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * Safe projection of {@link AuditLog}. The lazy {@code performedBy} association
 * becomes a safe summary — no {@code idNumber}/{@code password} or entity graph
 * escapes the serializer. {@code oldValue}/{@code newValue} are passed through
 * unchanged (their contents are the responsibility of the writers in
 * {@code AuditService}, which are instructed never to log sensitive values).
 */
@Getter
public class AuditLogResponse {

    private final String id;
    private final EmployeeSummary performedBy;
    private final String action;
    private final String entityType;
    private final String entityId;
    private final String oldValue;
    private final String newValue;
    private final LocalDateTime timestamp;
    private final String ipAddress;

    public AuditLogResponse(AuditLog log) {
        this.id = log.getId();
        this.performedBy = EmployeeSummary.of(log.getPerformedBy());
        this.action = log.getAction();
        this.entityType = log.getEntityType();
        this.entityId = log.getEntityId();
        this.oldValue = log.getOldValue();
        this.newValue = log.getNewValue();
        this.timestamp = log.getTimestamp();
        this.ipAddress = log.getIpAddress();
    }
}
