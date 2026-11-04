package employeehub.domain;

import employeehub.domain.enums.PolicyStatus;
import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
@Table(name = "leave_policy_versions")
public class LeavePolicyVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "leave_type_id", nullable = false)
    private LeaveType leaveType;

    @Column(nullable = false)
    private Integer versionNumber;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private Boolean paid = true;

    @Column(nullable = false)
    private Integer defaultEntitlement;

    @Column(nullable = false)
    private Boolean requiresDocumentation = false;

    @Column(nullable = false)
    private Integer noticePeriodDays = 0;

    @Column(nullable = false)
    private LocalDate effectiveFrom;

    private LocalDate effectiveTo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PolicyStatus status = PolicyStatus.DRAFT;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
