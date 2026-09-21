package employeehub.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Pending leave/timesheet/document counts for a single department, as returned by
 * {@code GET /dashboard/department-breakdown}. Every department appears exactly once, with {@code 0}
 * in a category it has no pending items for.
 */
@Getter
@AllArgsConstructor
public class DepartmentBreakdownEntry {

    private final String departmentName;
    private final long pendingLeave;
    private final long pendingTimesheets;
    private final long pendingDocuments;
}
