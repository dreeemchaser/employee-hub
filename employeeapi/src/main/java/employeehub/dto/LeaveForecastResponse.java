package employeehub.dto;

import employeehub.domain.LeaveBalance;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Year-end leave-balance projection for one leave type. {@code remainingDays}
 * already excludes approved usage; pending requests are subtracted to produce
 * {@code projectedYearEndDays}. Mapping lives here so the service does not
 * grow DTO construction helpers.
 */
@Getter
public class LeaveForecastResponse {

    private static final int UNUSED_WARN_DAYS = 5;
    private static final int UNUSED_WARN_WINDOW_DAYS = 60;

    private final LeaveTypeSummary leaveType;
    private final BigDecimal remainingDays;
    private final BigDecimal pendingDays;
    private final BigDecimal projectedYearEndDays;
    private final LocalDate cycleEndDate;
    private final long daysUntilCycleEnd;
    private final boolean unusedLeaveAtRisk;
    private final String suggestion;

    public LeaveForecastResponse(LeaveBalance balance, BigDecimal pendingDays, LocalDate today) {
        this.leaveType = LeaveTypeSummary.of(balance.getLeaveType());
        this.remainingDays = balance.getRemainingDays();
        this.pendingDays = pendingDays;
        this.projectedYearEndDays = balance.getRemainingDays().subtract(pendingDays);
        this.cycleEndDate = balance.getCycleEndDate();
        this.daysUntilCycleEnd = ChronoUnit.DAYS.between(today, balance.getCycleEndDate());
        this.unusedLeaveAtRisk = projectedYearEndDays.compareTo(BigDecimal.valueOf(UNUSED_WARN_DAYS)) >= 0
                && daysUntilCycleEnd >= 0
                && daysUntilCycleEnd <= UNUSED_WARN_WINDOW_DAYS;
        this.suggestion = buildSuggestion();
    }

    private String buildSuggestion() {
        String typeName = leaveType != null ? leaveType.getName() : "leave";
        if (projectedYearEndDays.signum() <= 0) {
            return "No " + typeName + " remaining after pending requests.";
        }
        if (unusedLeaveAtRisk) {
            return projectedYearEndDays.stripTrailingZeros().toPlainString()
                    + " day(s) of " + typeName
                    + " are still unused and this cycle ends on " + cycleEndDate
                    + ". Plan time off before then so it is not left unused.";
        }
        if (daysUntilCycleEnd > UNUSED_WARN_WINDOW_DAYS) {
            return "Projected cycle-end balance is "
                    + projectedYearEndDays.stripTrailingZeros().toPlainString()
                    + " day(s). Spreading leave across quieter weeks avoids team clashes.";
        }
        return "Projected cycle-end balance is "
                + projectedYearEndDays.stripTrailingZeros().toPlainString()
                + " day(s) of " + typeName + ".";
    }
}
