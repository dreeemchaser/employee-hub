package employeehub.dto;

import employeehub.domain.Employee;
import employeehub.domain.PerformanceReview;
import employeehub.domain.enums.PerformanceReviewStatus;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Safe projection of {@link PerformanceReview}. Flattens the lazy
 * {@code employee}, {@code reviewer}, and {@code cycle} associations to ids +
 * display names — no entity graph escapes to the serializer.
 */
@Getter
public class PerformanceReviewResponse {

    private final String id;
    private final String employeeId;
    private final String employeeName;
    private final String reviewerId;
    private final String reviewerName;
    private final String cycleId;
    private final String cycleName;
    private final Integer overallRating;
    private final String strengths;
    private final String areasForImprovement;
    private final String comments;
    private final PerformanceReviewStatus status;
    private final LocalDateTime acknowledgedAt;
    private final LocalDateTime createdAt;

    public PerformanceReviewResponse(PerformanceReview r) {
        this.id = r.getId();
        this.employeeId = r.getEmployee() != null ? r.getEmployee().getId() : null;
        this.employeeName = fullName(r.getEmployee());
        this.reviewerId = r.getReviewer() != null ? r.getReviewer().getId() : null;
        this.reviewerName = fullName(r.getReviewer());
        this.cycleId = r.getCycle() != null ? r.getCycle().getId() : null;
        this.cycleName = r.getCycle() != null ? r.getCycle().getName() : null;
        this.overallRating = r.getOverallRating();
        this.strengths = r.getStrengths();
        this.areasForImprovement = r.getAreasForImprovement();
        this.comments = r.getComments();
        this.status = r.getStatus();
        this.acknowledgedAt = r.getAcknowledgedAt();
        this.createdAt = r.getCreatedAt();
    }

    private static String fullName(Employee e) {
        return e != null ? (e.getFirstName() + " " + e.getLastName()).trim() : null;
    }

    public static List<PerformanceReviewResponse> from(List<PerformanceReview> reviews) {
        return reviews.stream().map(PerformanceReviewResponse::new).toList();
    }
}
