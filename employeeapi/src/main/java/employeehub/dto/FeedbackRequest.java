package employeehub.dto;

import employeehub.domain.enums.FeedbackVisibility;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class FeedbackRequest {
    @NotNull
    private String subjectId;
    @NotBlank
    private String comment;
    @Min(1)
    @Max(5)
    private Integer rating;
    @NotNull
    private FeedbackVisibility visibility = FeedbackVisibility.SUBJECT;
}
