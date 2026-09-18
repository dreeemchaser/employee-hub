package employeehub.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import employeehub.domain.enums.EmploymentStatus;
import employeehub.domain.enums.EmploymentType;
import employeehub.domain.enums.Role;
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
@Table(name = "employees")
public class Employee {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(unique = true, nullable = false)
    private String employeeNumber;

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    @Column(unique = true, nullable = false)
    private String email;

    @JsonIgnore
    private String password;

    private String phone;
    private LocalDate dateOfBirth;
    private String gender;
    private String nationality;

    // South African ID number — sensitive PII. Never serialize it in API
    // responses; expose it only through a dedicated, access-controlled DTO if a
    // screen ever requires it.
    @JsonIgnore
    private String idNumber;

    private String address;
    private String profilePhoto;

    @Column(nullable = false)
    private String jobTitle;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EmploymentType employmentType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EmploymentStatus employmentStatus = EmploymentStatus.ACTIVE;

    @Column(nullable = false)
    private LocalDate startDate;

    private LocalDate endDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Department department;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Team team;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manager_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "password", "manager"})
    private Employee manager;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role = Role.EMPLOYEE;

    // Account lockout: number of consecutive failed login attempts; reset to 0 on
    // a successful login. When it reaches the configured threshold the account is
    // locked until {@link #lockedUntil}.
    // columnDefinition supplies a DB default so the NOT NULL column can be added
    // to an already-populated employees table under ddl-auto=update.
    @Column(nullable = false, columnDefinition = "integer default 0")
    private int failedLoginAttempts = 0;

    // When set and in the future, the account is temporarily locked and login is
    // refused regardless of credentials. Null (or in the past) means not locked.
    private LocalDateTime lockedUntil;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
