package employeehub.dto;

import employeehub.domain.Employee;
import employeehub.domain.PerformanceGoal;
import employeehub.domain.enums.PerformanceGoalStatus;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Safe projection of {@link PerformanceGoal}. Flattens the lazy {@code employee},
 * {@code createdBy}, and {@code cycle} associations to ids + display names — no
 * entity graph escapes to the serializer.
 */
@Getter
public class PerformanceGoalResponse {

    private final String id;
    private final String employeeId;
    private final String employeeName;
    private final String cycleId;
    private final String cycleName;
    private final String title;
    private final String description;
    private final LocalDate targetDate;
    private final PerformanceGoalStatus status;
    private final Integer rating;
    private final String createdById;
    private final String createdByName;
    private final LocalDateTime createdAt;

    public PerformanceGoalResponse(PerformanceGoal g) {
        this.id = g.getId();
        this.employeeId = g.getEmployee() != null ? g.getEmployee().getId() : null;
        this.employeeName = fullName(g.getEmployee());
        this.cycleId = g.getCycle() != null ? g.getCycle().getId() : null;
        this.cycleName = g.getCycle() != null ? g.getCycle().getName() : null;
        this.title = g.getTitle();
        this.description = g.getDescription();
        this.targetDate = g.getTargetDate();
        this.status = g.getStatus();
        this.rating = g.getRating();
        this.createdById = g.getCreatedBy() != null ? g.getCreatedBy().getId() : null;
        this.createdByName = fullName(g.getCreatedBy());
        this.createdAt = g.getCreatedAt();
    }

    private static String fullName(Employee e) {
        return e != null ? (e.getFirstName() + " " + e.getLastName()).trim() : null;
    }

    public static List<PerformanceGoalResponse> from(List<PerformanceGoal> goals) {
        return goals.stream().map(PerformanceGoalResponse::new).toList();
    }
}
