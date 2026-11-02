package employeehub.domain;

import employeehub.domain.enums.FeedbackVisibility;
import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
@Table(name = "feedback")
public class Feedback {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_id", nullable = false)
    private Employee subject;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false)
    private Employee author;

    @Column(nullable = false)
    private String comment;

    private Integer rating;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FeedbackVisibility visibility = FeedbackVisibility.SUBJECT;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
