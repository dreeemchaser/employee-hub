package employeehub.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class TaxBracketRequest {

    @NotNull
    @Min(2000)
    private Integer taxYear;

    @NotNull
    @DecimalMin("0.00")
    private BigDecimal minIncome;

    @DecimalMin("0.00")
    private BigDecimal maxIncome;

    @NotNull
    @DecimalMin("0.00")
    private BigDecimal baseTax;

    @NotNull
    @DecimalMin("0.00")
    @DecimalMax("100.00")
    private BigDecimal marginalRate;

    @NotNull
    @DecimalMin("0.00")
    private BigDecimal rebate;
}
