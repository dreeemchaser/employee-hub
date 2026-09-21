package employeehub.dto;

import employeehub.domain.AttendanceRecord;
import employeehub.domain.enums.AttendanceStatus;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Safe projection of {@link AttendanceRecord}. Mirrors the {@code TimesheetResponse}
 * pattern: the lazy {@code employee} association becomes a flattened {@link EmployeeSummary}
 * so no Hibernate proxy or PII (idNumber, password) reaches the serializer.
 */
@Getter
public class AttendanceResponse {

    private final String id;
    private final EmployeeSummary employee;
    private final LocalDate workDate;
    private final LocalDateTime clockInAt;
    private final LocalDateTime clockOutAt;
    private final AttendanceStatus status;
    private final String notes;
    private final LocalDateTime createdAt;

    public AttendanceResponse(AttendanceRecord a) {
        this.id = a.getId();
        this.employee = EmployeeSummary.of(a.getEmployee());
        this.workDate = a.getWorkDate();
        this.clockInAt = a.getClockInAt();
        this.clockOutAt = a.getClockOutAt();
        this.status = a.getStatus();
        this.notes = a.getNotes();
        this.createdAt = a.getCreatedAt();
    }
}
