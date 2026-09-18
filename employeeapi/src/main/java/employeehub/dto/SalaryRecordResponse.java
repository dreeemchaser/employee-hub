package employeehub.dto;

import employeehub.domain.SalaryRecord;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Safe projection of {@link SalaryRecord}. The lazy {@code employee} and
 * {@code createdBy} associations become safe summaries. Monetary values stay
 * {@link BigDecimal}. No {@code idNumber}/{@code password} or entity graph
 * escapes the serializer.
 */
@Getter
public class SalaryRecordResponse {

    private final String id;
    private final EmployeeSummary employee;
    private final BigDecimal basicSalary;
    private final LocalDate effectiveDate;
    private final LocalDate endDate;
    private final EmployeeSummary createdBy;
    private final LocalDateTime createdAt;

    public SalaryRecordResponse(SalaryRecord s) {
        this.id = s.getId();
        this.employee = EmployeeSummary.of(s.getEmployee());
        this.basicSalary = s.getBasicSalary();
        this.effectiveDate = s.getEffectiveDate();
        this.endDate = s.getEndDate();
        this.createdBy = EmployeeSummary.of(s.getCreatedBy());
        this.createdAt = s.getCreatedAt();
    }

    public static List<SalaryRecordResponse> from(List<SalaryRecord> records) {
        return records.stream().map(SalaryRecordResponse::new).toList();
    }
}
