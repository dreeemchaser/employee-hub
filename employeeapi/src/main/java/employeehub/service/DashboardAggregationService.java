package employeehub.service;

import employeehub.domain.enums.DocumentStatus;
import employeehub.domain.enums.LeaveStatus;
import employeehub.domain.enums.TimesheetStatus;
import employeehub.dto.DepartmentBreakdownEntry;
import employeehub.repository.DepartmentRepository;
import employeehub.repository.DocumentRepository;
import employeehub.repository.LeaveRequestRepository;
import employeehub.repository.TimesheetRepository;
import employeehub.repository.support.DepartmentCountProjection;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Cross-entity aggregation for the HR dashboard. Kept separate from {@code DashboardController}
 * (unlike the existing {@code /stats} endpoint, which inlines its aggregation directly in the
 * controller) so this heavier merge logic is unit-testable without {@code @WebMvcTest}.
 */
@Service
@RequiredArgsConstructor
public class DashboardAggregationService {

    private final DepartmentRepository departmentRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final TimesheetRepository timesheetRepository;
    private final DocumentRepository documentRepository;

    /**
     * Pending leave/timesheet/document counts grouped by department. Every department is included
     * exactly once, defaulting to 0 in a category it has no pending rows for — a plain GROUP BY only
     * returns departments with at least one matching row, so the full department list is the source
     * of truth here, not the query results.
     */
    public List<DepartmentBreakdownEntry> getDepartmentBreakdown() {
        Map<String, Long> pendingLeaveByDept =
                toMap(leaveRequestRepository.countByDepartmentAndStatus(LeaveStatus.PENDING));
        Map<String, Long> pendingTimesheetsByDept =
                toMap(timesheetRepository.countByDepartmentAndStatus(TimesheetStatus.SUBMITTED));
        Map<String, Long> pendingDocumentsByDept =
                toMap(documentRepository.countByDepartmentAndStatus(DocumentStatus.PENDING));

        return departmentRepository.findAll().stream()
                .map(dept -> new DepartmentBreakdownEntry(
                        dept.getName(),
                        pendingLeaveByDept.getOrDefault(dept.getName(), 0L),
                        pendingTimesheetsByDept.getOrDefault(dept.getName(), 0L),
                        pendingDocumentsByDept.getOrDefault(dept.getName(), 0L)))
                .toList();
    }

    private Map<String, Long> toMap(List<DepartmentCountProjection> rows) {
        return rows.stream().collect(Collectors.toMap(
                DepartmentCountProjection::getDepartmentName, DepartmentCountProjection::getCount));
    }
}
