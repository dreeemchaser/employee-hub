package employeehub.dto;

import employeehub.domain.LeaveRequest;
import employeehub.domain.enums.LeaveStatus;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Safe projection of {@link LeaveRequest}. The lazy {@code employee},
 * {@code leaveType}, and {@code approvedBy} associations are replaced with
 * explicit safe summaries — no {@code idNumber}/{@code password} and no
 * Hibernate proxy escapes to the serializer. The nested {@code employee} /
 * {@code leaveType} shape the frontends read is preserved.
 */
@Getter
public class LeaveRequestResponse {

    private final String id;
    private final EmployeeSummary employee;
    private final LeaveTypeSummary leaveType;
    private final LocalDate startDate;
    private final LocalDate endDate;
    private final BigDecimal totalDays;
    private final String reason;
    private final LeaveStatus status;
    private final EmployeeSummary approvedBy;
    private final LocalDateTime approvedAt;
    private final String rejectionReason;
    private final LocalDateTime createdAt;

    public LeaveRequestResponse(LeaveRequest r) {
        this.id = r.getId();
        this.employee = EmployeeSummary.of(r.getEmployee());
        this.leaveType = LeaveTypeSummary.of(r.getLeaveType());
        this.startDate = r.getStartDate();
        this.endDate = r.getEndDate();
        this.totalDays = r.getTotalDays();
        this.reason = r.getReason();
        this.status = r.getStatus();
        this.approvedBy = EmployeeSummary.of(r.getApprovedBy());
        this.approvedAt = r.getApprovedAt();
        this.rejectionReason = r.getRejectionReason();
        this.createdAt = r.getCreatedAt();
    }

    public static List<LeaveRequestResponse> from(List<LeaveRequest> requests) {
        return requests.stream().map(LeaveRequestResponse::new).toList();
    }
}
