package employeehub.dto;

import employeehub.domain.PaySlip;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Safe projection of {@link PaySlip}. The lazy {@code employee} association
 * becomes a safe summary. All monetary values stay {@link BigDecimal}. No
 * {@code idNumber}/{@code password} or entity graph escapes the serializer.
 */
@Getter
public class PaySlipResponse {

    private final String id;
    private final EmployeeSummary employee;
    private final String month;
    private final BigDecimal basicSalary;
    private final BigDecimal grossSalary;
    private final BigDecimal uif;
    private final BigDecimal paye;
    private final BigDecimal medicalAid;
    private final BigDecimal pensionFund;
    private final BigDecimal otherDeductions;
    private final BigDecimal netSalary;
    private final Integer taxYear;
    private final LocalDateTime createdAt;

    public PaySlipResponse(PaySlip p) {
        this.id = p.getId();
        this.employee = EmployeeSummary.of(p.getEmployee());
        this.month = p.getMonth();
        this.basicSalary = p.getBasicSalary();
        this.grossSalary = p.getGrossSalary();
        this.uif = p.getUif();
        this.paye = p.getPaye();
        this.medicalAid = p.getMedicalAid();
        this.pensionFund = p.getPensionFund();
        this.otherDeductions = p.getOtherDeductions();
        this.netSalary = p.getNetSalary();
        this.taxYear = p.getTaxYear();
        this.createdAt = p.getCreatedAt();
    }

    public static List<PaySlipResponse> from(List<PaySlip> payslips) {
        return payslips.stream().map(PaySlipResponse::new).toList();
    }
}
