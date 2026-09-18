package employeehub.dto;

import employeehub.domain.EmployeeBenefit;
import employeehub.domain.enums.BenefitStatus;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Safe projection of {@link EmployeeBenefit}. Flattens the lazy {@code employee}
 * and {@code benefitType} associations to ids + display names so no entity graph
 * (and no password/idNumber) escapes to the serializer.
 */
@Getter
public class EmployeeBenefitResponse {

    private final String id;
    private final String employeeId;
    private final String employeeName;
    private final Long benefitTypeId;
    private final String benefitTypeName;
    private final BigDecimal employeeContribution;
    private final BigDecimal employerContribution;
    private final LocalDate startDate;
    private final LocalDate endDate;
    private final BenefitStatus status;

    public EmployeeBenefitResponse(EmployeeBenefit b) {
        this.id = b.getId();
        this.employeeId = b.getEmployee() != null ? b.getEmployee().getId() : null;
        this.employeeName = fullName(b.getEmployee());
        var type = b.getBenefitType();
        this.benefitTypeId = type != null ? type.getId() : null;
        this.benefitTypeName = type != null ? type.getName() : null;
        this.employeeContribution = type != null ? type.getEmployeeContribution() : null;
        this.employerContribution = type != null ? type.getEmployerContribution() : null;
        this.startDate = b.getStartDate();
        this.endDate = b.getEndDate();
        this.status = b.getStatus();
    }

    private static String fullName(employeehub.domain.Employee e) {
        return e != null ? (e.getFirstName() + " " + e.getLastName()).trim() : null;
    }

    public static List<EmployeeBenefitResponse> from(List<EmployeeBenefit> benefits) {
        return benefits.stream().map(EmployeeBenefitResponse::new).toList();
    }
}
