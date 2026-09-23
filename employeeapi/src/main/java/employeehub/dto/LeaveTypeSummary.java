package employeehub.dto;

import employeehub.domain.LeaveType;
import lombok.Getter;

import java.util.List;

/**
 * Safe projection of {@link LeaveType} for embedding inside leave response
 * DTOs. Preserves the nested {@code {id, name, requiresDocumentation}} shape the
 * frontends consume. {@code LeaveType} carries no sensitive data or lazy
 * associations, so all fields are safe to expose.
 */
@Getter
public class LeaveTypeSummary {

    private final Long id;
    private final String name;
    private final Integer defaultDays;
    private final Boolean requiresDocumentation;
    private final Boolean isPaid;

    public LeaveTypeSummary(LeaveType t) {
        this.id = t.getId();
        this.name = t.getName();
        this.defaultDays = t.getDefaultDays();
        this.requiresDocumentation = t.getRequiresDocumentation();
        this.isPaid = t.getIsPaid();
    }

    public static LeaveTypeSummary of(LeaveType t) {
        return t != null ? new LeaveTypeSummary(t) : null;
    }

    public static List<LeaveTypeSummary> from(List<LeaveType> types) {
        return types.stream().map(LeaveTypeSummary::new).toList();
    }
}
