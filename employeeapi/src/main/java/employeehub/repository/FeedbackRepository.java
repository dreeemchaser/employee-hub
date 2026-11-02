package employeehub.repository;

import employeehub.domain.Feedback;
import employeehub.domain.enums.FeedbackVisibility;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.List;

public interface FeedbackRepository extends JpaRepository<Feedback, String> {
    @EntityGraph(attributePaths = {"subject", "author"})
    List<Feedback> findBySubjectIdOrderByCreatedAtDesc(String subjectId);
    @EntityGraph(attributePaths = {"subject", "author"})
    List<Feedback> findByAuthorIdOrderByCreatedAtDesc(String authorId);
    boolean existsByAuthorIdAndSubjectIdAndCreatedAtAfter(String authorId, String subjectId,
                                                           java.time.LocalDateTime after);
}
