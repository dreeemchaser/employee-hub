package employeehub.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class LeavePolicyRequest {
    @NotNull
    private Long leaveTypeId;
    @NotBlank
    private String name;
    @NotNull
    private Boolean paid;
    @NotNull
    @Min(0)
    private Integer defaultEntitlement;
    @NotNull
    private Boolean requiresDocumentation;
    @NotNull
    @Min(0)
    private Integer noticePeriodDays;
    @NotNull
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
}
