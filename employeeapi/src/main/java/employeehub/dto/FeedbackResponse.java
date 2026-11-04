package employeehub.dto;

import employeehub.domain.Feedback;
import employeehub.domain.enums.FeedbackVisibility;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class FeedbackResponse {
    private final String id;
    private final String subjectId;
    private final String subjectName;
    private final String authorName;
    private final String comment;
    private final Integer rating;
    private final FeedbackVisibility visibility;
    private final LocalDateTime createdAt;

    public FeedbackResponse(Feedback feedback, boolean hideAuthor) {
        this.id = feedback.getId();
        this.subjectId = feedback.getSubject().getId();
        this.subjectName = feedback.getSubject().getFirstName() + " " + feedback.getSubject().getLastName();
        this.authorName = hideAuthor ? "Anonymous" :
                feedback.getAuthor().getFirstName() + " " + feedback.getAuthor().getLastName();
        this.comment = feedback.getComment();
        this.rating = feedback.getRating();
        this.visibility = feedback.getVisibility();
        this.createdAt = feedback.getCreatedAt();
    }
}
