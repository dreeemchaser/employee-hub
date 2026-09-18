package employeehub.dto;

import employeehub.domain.Timesheet;
import employeehub.domain.enums.TimesheetStatus;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Safe projection of {@link Timesheet}. The lazy {@code employee} and
 * {@code approvedBy} associations become safe summaries, and the {@code entries}
 * collection is projected to nested {@link TimesheetEntryResponse} objects
 * (preserving the shape the frontends read). No entity graph, {@code idNumber},
 * {@code password}, or Hibernate proxy reaches the serializer.
 *
 * <p>Construction touches the lazy {@code entries} collection, so it must run
 * inside an open Hibernate session — the caller resolves it while the request's
 * transaction / OSIV scope is active, matching the existing DTO pattern.
 */
@Getter
public class TimesheetResponse {

    private final String id;
    private final EmployeeSummary employee;
    private final LocalDate weekStartDate;
    private final LocalDate weekEndDate;
    private final BigDecimal totalHours;
    private final TimesheetStatus status;
    private final EmployeeSummary approvedBy;
    private final LocalDateTime approvedAt;
    private final String rejectionReason;
    private final LocalDateTime createdAt;
    private final List<TimesheetEntryResponse> entries;

    public TimesheetResponse(Timesheet t) {
        this.id = t.getId();
        this.employee = EmployeeSummary.of(t.getEmployee());
        this.weekStartDate = t.getWeekStartDate();
        this.weekEndDate = t.getWeekEndDate();
        this.totalHours = t.getTotalHours();
        this.status = t.getStatus();
        this.approvedBy = EmployeeSummary.of(t.getApprovedBy());
        this.approvedAt = t.getApprovedAt();
        this.rejectionReason = t.getRejectionReason();
        this.createdAt = t.getCreatedAt();
        this.entries = t.getEntries() != null
                ? t.getEntries().stream().map(TimesheetEntryResponse::new).toList()
                : List.of();
    }

    public static List<TimesheetResponse> from(List<Timesheet> timesheets) {
        return timesheets.stream().map(TimesheetResponse::new).toList();
    }
}
