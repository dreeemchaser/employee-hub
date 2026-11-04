package employeehub.service;

import employeehub.domain.Employee;
import employeehub.domain.Feedback;
import employeehub.domain.enums.FeedbackVisibility;
import employeehub.dto.FeedbackRequest;
import employeehub.exception.ResourceNotFoundException;
import employeehub.repository.EmployeeRepository;
import employeehub.repository.FeedbackRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FeedbackService {
    private final FeedbackRepository feedbackRepository;
    private final EmployeeRepository employeeRepository;
    private final AuditService auditService;
    private final NotificationService notificationService;

    @Transactional
    public Feedback create(Employee author, FeedbackRequest request) {
        Employee subject = employeeRepository.findById(request.getSubjectId())
                .orElseThrow(() -> new ResourceNotFoundException("Feedback subject not found"));
        if (author.getId().equals(subject.getId())) {
            throw new IllegalArgumentException("You cannot give feedback to yourself");
        }
        if (feedbackRepository.existsByAuthorIdAndSubjectIdAndCreatedAtAfter(
                author.getId(), subject.getId(), LocalDateTime.now().minusDays(30))) {
            throw new IllegalStateException("You already submitted feedback for this employee recently");
        }
        Feedback feedback = new Feedback();
        feedback.setAuthor(author);
        feedback.setSubject(subject);
        feedback.setComment(request.getComment().trim());
        feedback.setRating(request.getRating());
        feedback.setVisibility(request.getVisibility());
        Feedback saved = feedbackRepository.save(feedback);
        auditService.log(author, "CREATE", "Feedback", saved.getId(), null, "Feedback submitted");
        notificationService.send(subject, "New feedback received",
                "You have received new feedback", employeehub.domain.enums.NotificationType.GENERAL,
                "Feedback", saved.getId());
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Feedback> received(Employee employee) {
        return feedbackRepository.findBySubjectIdOrderByCreatedAtDesc(employee.getId());
    }

    @Transactional(readOnly = true)
    public List<Feedback> authored(Employee employee) {
        return feedbackRepository.findByAuthorIdOrderByCreatedAtDesc(employee.getId());
    }

    public boolean hideAuthor(Feedback feedback, Employee viewer) {
        return feedback.getVisibility() == FeedbackVisibility.SUBJECT
                && !feedback.getSubject().getId().equals(viewer.getId());
    }
}
