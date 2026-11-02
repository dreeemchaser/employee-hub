package employeehub.dto;

import employeehub.domain.LeavePolicyVersion;
import employeehub.domain.enums.PolicyStatus;
import lombok.Getter;

import java.time.LocalDate;

@Getter
public class LeavePolicyResponse {
    private final String id;
    private final Long leaveTypeId;
    private final String name;
    private final Integer versionNumber;
    private final Boolean paid;
    private final Integer defaultEntitlement;
    private final Boolean requiresDocumentation;
    private final Integer noticePeriodDays;
    private final LocalDate effectiveFrom;
    private final LocalDate effectiveTo;
    private final PolicyStatus status;

    public LeavePolicyResponse(LeavePolicyVersion policy) {
        this.id = policy.getId();
        this.leaveTypeId = policy.getLeaveType().getId();
        this.name = policy.getName();
        this.versionNumber = policy.getVersionNumber();
        this.paid = policy.getPaid();
        this.defaultEntitlement = policy.getDefaultEntitlement();
        this.requiresDocumentation = policy.getRequiresDocumentation();
        this.noticePeriodDays = policy.getNoticePeriodDays();
        this.effectiveFrom = policy.getEffectiveFrom();
        this.effectiveTo = policy.getEffectiveTo();
        this.status = policy.getStatus();
    }
}
