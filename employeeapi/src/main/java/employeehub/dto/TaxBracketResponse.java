package employeehub.dto;

import employeehub.domain.TaxBracket;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
public class TaxBracketResponse {

    private final Long id;
    private final Integer taxYear;
    private final BigDecimal minIncome;
    private final BigDecimal maxIncome;
    private final BigDecimal baseTax;
    private final BigDecimal marginalRate;
    private final BigDecimal rebate;

    public TaxBracketResponse(TaxBracket bracket) {
        this.id = bracket.getId();
        this.taxYear = bracket.getTaxYear();
        this.minIncome = bracket.getMinIncome();
        this.maxIncome = bracket.getMaxIncome();
        this.baseTax = bracket.getBaseTax();
        this.marginalRate = bracket.getMarginalRate();
        this.rebate = bracket.getRebate();
    }
}
