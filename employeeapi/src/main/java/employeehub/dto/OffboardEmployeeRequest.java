package employeehub.dto;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDate;

@Data
public class OffboardEmployeeRequest {

    @NotBlank(message = "Reason is required")
    private String reason;

    // Defaults to today in the service when omitted; must not be backdated
    // (that would retroactively cancel leave/benefits that were already valid).
    @FutureOrPresent(message = "Last working day cannot be in the past")
    private LocalDate lastWorkingDay;
}
