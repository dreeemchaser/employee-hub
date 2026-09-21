package employeehub.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import employeehub.domain.enums.AttendanceStatus;
import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One clock-in/clock-out session for a single employee on a single day.
 * Mirrors {@link Timesheet}'s association pattern: a lazy {@code employee}
 * reference masked the same way, since this DTO-free entity never leaves the
 * service layer (controllers only ever see {@code AttendanceResponse}).
 */
@Entity
@Getter
@Setter
@EqualsAndHashCode(of = "id")
@NoArgsConstructor
@Table(name = "attendance_records")
public class AttendanceRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "password", "manager"})
    private Employee employee;

    @Column(nullable = false)
    private LocalDate workDate;

    @Column(nullable = false)
    private LocalDateTime clockInAt;

    private LocalDateTime clockOutAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AttendanceStatus status = AttendanceStatus.OPEN;

    private String notes;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
