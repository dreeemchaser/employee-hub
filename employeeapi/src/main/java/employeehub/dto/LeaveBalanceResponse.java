package employeehub.dto;

import employeehub.domain.LeaveBalance;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Safe projection of {@link LeaveBalance}. The lazy {@code employee} and
 * {@code leaveType} associations are replaced with explicit safe summaries. The
 * nested {@code leaveType} shape the frontend reads (id, name,
 * requiresDocumentation) is preserved.
 */
@Getter
public class LeaveBalanceResponse {

    private final Long id;
    private final EmployeeSummary employee;
    private final LeaveTypeSummary leaveType;
    private final BigDecimal totalDays;
    private final BigDecimal usedDays;
    private final BigDecimal remainingDays;
    private final LocalDate cycleStartDate;
    private final LocalDate cycleEndDate;

    public LeaveBalanceResponse(LeaveBalance b) {
        this.id = b.getId();
        this.employee = EmployeeSummary.of(b.getEmployee());
        this.leaveType = LeaveTypeSummary.of(b.getLeaveType());
        this.totalDays = b.getTotalDays();
        this.usedDays = b.getUsedDays();
        this.remainingDays = b.getRemainingDays();
        this.cycleStartDate = b.getCycleStartDate();
        this.cycleEndDate = b.getCycleEndDate();
    }

    public static List<LeaveBalanceResponse> from(List<LeaveBalance> balances) {
        return balances.stream().map(LeaveBalanceResponse::new).toList();
    }
}
