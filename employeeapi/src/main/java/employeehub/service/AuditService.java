package employeehub.service;

import employeehub.domain.AuditLog;
import employeehub.domain.Employee;
import employeehub.dto.RetentionDryRunResponse;
import employeehub.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public void log(Employee performedBy, String action, String entityType, String entityId,
                    String oldValue, String newValue) {
        AuditLog log = new AuditLog();
        log.setPerformedBy(performedBy);
        log.setAction(action);
        log.setEntityType(entityType);
        log.setEntityId(entityId);
        log.setOldValue(oldValue);
        log.setNewValue(newValue);
        auditLogRepository.save(log);
    }

    public Page<AuditLog> getAll(String entityType, String employeeId,
                                  LocalDateTime from, LocalDateTime to, Pageable pageable) {
        return auditLogRepository.findAllFiltered(entityType, employeeId, from, to, pageable);
    }

    public Page<AuditLog> getAll(String entityType, String action, String actorId, String entityId,
                                 LocalDateTime from, LocalDateTime to, Pageable pageable, Employee actor) {
        validateRange(from, to);
        log(actor, "AUDIT_READ", "AuditLog", null, null, null);
        return auditLogRepository.findAllFiltered(entityType, action, actorId, entityId, from, to,
                deterministic(pageable));
    }

    public byte[] export(String entityType, String action, String actorId, String entityId,
                         LocalDateTime from, LocalDateTime to, int limit, Employee actor) {
        if (limit < 1 || limit > 5_000) {
            throw new IllegalArgumentException("Export limit must be between 1 and 5000");
        }
        validateRange(from, to);
        log(actor, "AUDIT_EXPORT", "AuditLog", null, null, null);
        return auditLogRepository.findAllFiltered(entityType, action, actorId, entityId, from, to,
                        deterministic(org.springframework.data.domain.PageRequest.of(0, limit)))
                .map(this::csvRow)
                .stream()
                .collect(java.util.stream.Collectors.joining("\n"))
                .getBytes(StandardCharsets.UTF_8);
    }

    public RetentionDryRunResponse retentionDryRun(int retentionDays, Employee actor) {
        if (retentionDays < 30 || retentionDays > 3_650) {
            throw new IllegalArgumentException("Retention days must be between 30 and 3650");
        }
        LocalDateTime cutoff = LocalDateTime.now().minus(retentionDays, ChronoUnit.DAYS);
        log(actor, "AUDIT_RETENTION_DRY_RUN", "AuditLog", null, null, String.valueOf(retentionDays));
        return new RetentionDryRunResponse(retentionDays, cutoff,
                auditLogRepository.countByTimestampBefore(cutoff));
    }

    private org.springframework.data.domain.Pageable deterministic(Pageable pageable) {
        if (pageable.getSort().isSorted()) {
            return pageable;
        }
        return org.springframework.data.domain.PageRequest.of(
                pageable.getPageNumber(), pageable.getPageSize(),
                org.springframework.data.domain.Sort.by(
                        org.springframework.data.domain.Sort.Order.desc("timestamp"),
                        org.springframework.data.domain.Sort.Order.desc("id")));
    }

    private String csvRow(AuditLog log) {
        return String.join(",",
                csv(log.getId()),
                csv(log.getTimestamp() != null ? log.getTimestamp().toString() : ""),
                csv(log.getPerformedBy() != null ? log.getPerformedBy().getFirstName() + " "
                        + log.getPerformedBy().getLastName() : ""),
                csv(log.getAction()), csv(log.getEntityType()), csv(log.getEntityId()),
                csv(log.getOldValue()), csv(log.getNewValue()));
    }

    private String csv(String value) {
        if (value == null) {
            return "";
        }
        String safe = value.replace("\"", "\"\"");
        if (safe.startsWith("=") || safe.startsWith("+") || safe.startsWith("-")
                || safe.startsWith("@")) {
            safe = "'" + safe;
        }
        return "\"" + safe + "\"";
    }

    private void validateRange(LocalDateTime from, LocalDateTime to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("'from' must not be after 'to'");
        }
    }
}
