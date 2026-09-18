package employeehub.dto;

import employeehub.domain.Employee;
import employeehub.domain.SalaryIncreaseRequest;
import employeehub.domain.enums.SalaryIncreaseStatus;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Safe projection of {@link SalaryIncreaseRequest}. Flattens the lazy
 * {@code employee}, {@code requestedBy}, and {@code reviewedBy} associations to
 * ids + display names — no entity graph escapes to the serializer.
 */
@Getter
public class SalaryIncreaseRequestResponse {

    private final String id;
    private final String employeeId;
    private final String employeeName;
    private final String requestedById;
    private final String requestedByName;
    private final BigDecimal currentSalary;
    private final BigDecimal proposedSalary;
    private final BigDecimal increasePercentage;
    private final String justification;
    private final SalaryIncreaseStatus status;
    private final String reviewedById;
    private final String reviewedByName;
    private final LocalDateTime reviewedAt;
    private final String rejectionReason;
    private final LocalDateTime createdAt;

    public SalaryIncreaseRequestResponse(SalaryIncreaseRequest r) {
        this.id = r.getId();
        this.employeeId = r.getEmployee() != null ? r.getEmployee().getId() : null;
        this.employeeName = fullName(r.getEmployee());
        this.requestedById = r.getRequestedBy() != null ? r.getRequestedBy().getId() : null;
        this.requestedByName = fullName(r.getRequestedBy());
        this.currentSalary = r.getCurrentSalary();
        this.proposedSalary = r.getProposedSalary();
        this.increasePercentage = r.getIncreasePercentage();
        this.justification = r.getJustification();
        this.status = r.getStatus();
        this.reviewedById = r.getReviewedBy() != null ? r.getReviewedBy().getId() : null;
        this.reviewedByName = fullName(r.getReviewedBy());
        this.reviewedAt = r.getReviewedAt();
        this.rejectionReason = r.getRejectionReason();
        this.createdAt = r.getCreatedAt();
    }

    private static String fullName(Employee e) {
        return e != null ? (e.getFirstName() + " " + e.getLastName()).trim() : null;
    }

    public static List<SalaryIncreaseRequestResponse> from(List<SalaryIncreaseRequest> requests) {
        return requests.stream().map(SalaryIncreaseRequestResponse::new).toList();
    }
}
