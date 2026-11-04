package employeehub.controller;

import employeehub.dto.ApiResponse;
import employeehub.dto.AuditLogResponse;
import employeehub.dto.RetentionDryRunResponse;
import employeehub.service.EmployeeService;
import employeehub.service.AuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/audit-logs")
@RequiredArgsConstructor
@Tag(name = "Audit Logs")
public class AuditLogController {

    private final AuditService auditService;
    private final EmployeeService employeeService;

    @GetMapping
    @Operation(summary = "Get audit logs (HR_ADMIN/SUPER_ADMIN), filterable by entity type, employee, date range")
    public ResponseEntity<ApiResponse<Page<AuditLogResponse>>> getAll(
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String actorId,
            @RequestParam(required = false) String employeeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            Pageable pageable,
            @org.springframework.security.core.annotation.AuthenticationPrincipal
            org.springframework.security.core.userdetails.UserDetails userDetails) {
        var actor = employeeService.getByEmail(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.ok(
                auditService.getAll(entityType, action, actorId, employeeId, from, to, pageable, actor)
                        .map(AuditLogResponse::new)));
    }

    @GetMapping(value = "/export", produces = "text/csv")
    @Operation(summary = "Export a bounded audit log CSV")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String actorId,
            @RequestParam(required = false) String employeeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "1000") int limit,
            @org.springframework.security.core.annotation.AuthenticationPrincipal
            org.springframework.security.core.userdetails.UserDetails userDetails) {
        var actor = employeeService.getByEmail(userDetails.getUsername());
        String header = "id,timestamp,actor,action,entityType,entityId,oldValue,newValue\n";
        byte[] rows = auditService.export(entityType, action, actorId, employeeId, from, to, limit, actor);
        byte[] body = (header + new String(rows, java.nio.charset.StandardCharsets.UTF_8)
                + (rows.length == 0 ? "" : "\n")).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"audit-logs.csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(body);
    }

    @GetMapping("/retention/dry-run")
    @Operation(summary = "Report audit records eligible for retention without deleting them")
    public ResponseEntity<ApiResponse<RetentionDryRunResponse>> retentionDryRun(
            @RequestParam(defaultValue = "365") int retentionDays,
            @org.springframework.security.core.annotation.AuthenticationPrincipal
            org.springframework.security.core.userdetails.UserDetails userDetails) {
        var actor = employeeService.getByEmail(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.ok(auditService.retentionDryRun(retentionDays, actor)));
    }
}
