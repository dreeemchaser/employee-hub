package employeehub.dto;

import employeehub.domain.BenefitApplication;
import employeehub.domain.Employee;
import employeehub.domain.enums.BenefitStatus;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Safe projection of {@link BenefitApplication}. Flattens the lazy
 * {@code employee}, {@code reviewedBy}, and {@code benefitType} associations to
 * ids + display names — no entity graph escapes to the serializer.
 */
@Getter
public class BenefitApplicationResponse {

    private final String id;
    private final String employeeId;
    private final String employeeName;
    private final Long benefitTypeId;
    private final String benefitTypeName;
    private final BenefitStatus status;
    private final String reviewedById;
    private final String reviewedByName;
    private final LocalDateTime reviewedAt;
    private final LocalDateTime createdAt;

    public BenefitApplicationResponse(BenefitApplication a) {
        this.id = a.getId();
        this.employeeId = a.getEmployee() != null ? a.getEmployee().getId() : null;
        this.employeeName = fullName(a.getEmployee());
        this.benefitTypeId = a.getBenefitType() != null ? a.getBenefitType().getId() : null;
        this.benefitTypeName = a.getBenefitType() != null ? a.getBenefitType().getName() : null;
        this.status = a.getStatus();
        this.reviewedById = a.getReviewedBy() != null ? a.getReviewedBy().getId() : null;
        this.reviewedByName = fullName(a.getReviewedBy());
        this.reviewedAt = a.getReviewedAt();
        this.createdAt = a.getCreatedAt();
    }

    private static String fullName(Employee e) {
        return e != null ? (e.getFirstName() + " " + e.getLastName()).trim() : null;
    }

    public static List<BenefitApplicationResponse> from(List<BenefitApplication> apps) {
        return apps.stream().map(BenefitApplicationResponse::new).toList();
    }
}
