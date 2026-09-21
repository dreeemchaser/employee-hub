package employeehub.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import employeehub.domain.enums.DocumentStatus;
import employeehub.domain.enums.DocumentType;
import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@EqualsAndHashCode(of = "id")
@NoArgsConstructor
@Table(name = "documents")
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "password", "manager"})
    private Employee employee;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DocumentType documentType;

    @Column(nullable = false)
    private String fileName;

    @Column(nullable = false)
    private String fileUrl;

    private BigDecimal fileSize;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "password", "manager"})
    private Employee uploadedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "verified_by_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "password", "manager"})
    private Employee verifiedBy;

    private LocalDateTime verifiedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DocumentStatus status = DocumentStatus.PENDING;

    // Not every document type expires (a payslip has no expiry; an ID document
    // or work permit does) so this is optional for every DocumentType, not
    // restricted to a subset.
    private LocalDate expiryDate;

    // Set the first time each reminder threshold fires for this document. Null
    // means that threshold has not fired yet — this is the idempotency guard
    // for DocumentExpiryReminderService, not just a log of when it happened.
    // Column names given explicitly (rather than relying on Hibernate's
    // default CamelCaseToUnderscoresNamingStrategy) because a trailing digit
    // run adjacent to a field name does not reliably get its own underscore —
    // see Hibernate issue HHH-17310. Explicit @Column avoids depending on
    // that strategy's undocumented edge-case behavior.
    @Column(name = "reminder_30_sent_at")
    private LocalDateTime reminderSentAt30;
    @Column(name = "reminder_14_sent_at")
    private LocalDateTime reminderSentAt14;
    @Column(name = "reminder_7_sent_at")
    private LocalDateTime reminderSentAt7;

    // Distinct from the three reminder columns above: those are employee-facing
    // "expiring soon" warnings sent before expiry; this is the one-time
    // HR/Admin notification sent when a VERIFIED document's expiry date has
    // already passed.
    private LocalDateTime hrExpiryNotifiedAt;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
