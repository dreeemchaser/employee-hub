package employeehub.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Getter
@Setter
@EqualsAndHashCode(of = "id")
@NoArgsConstructor
@Table(name = "leave_balances")
public class LeaveBalance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "password", "manager"})
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "leave_type_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private LeaveType leaveType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "policy_version_id")
    private LeavePolicyVersion policyVersion;

    @Column(nullable = false)
    private BigDecimal totalDays;

    @Column(nullable = false)
    private BigDecimal usedDays = BigDecimal.ZERO;

    @Column(nullable = false)
    private BigDecimal remainingDays;

    @Column(nullable = false)
    private LocalDate cycleStartDate;

    @Column(nullable = false)
    private LocalDate cycleEndDate;
}
