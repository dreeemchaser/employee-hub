package employeehub.dto;

import employeehub.domain.TimesheetEntry;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Safe projection of a {@link TimesheetEntry}. Omits the {@code timesheet}
 * back-reference (which would create a serialization cycle) and exposes only
 * the entry's own fields. Nested inside {@link TimesheetResponse}.
 */
@Getter
public class TimesheetEntryResponse {

    private final Long id;
    private final LocalDate date;
    private final BigDecimal hoursWorked;
    private final String description;
    private final String projectOrTask;

    public TimesheetEntryResponse(TimesheetEntry e) {
        this.id = e.getId();
        this.date = e.getDate();
        this.hoursWorked = e.getHoursWorked();
        this.description = e.getDescription();
        this.projectOrTask = e.getProjectOrTask();
    }
}
