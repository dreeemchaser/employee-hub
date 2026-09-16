package employeehub.dto;

import employeehub.domain.Employee;
import employeehub.domain.enums.EmploymentStatus;
import employeehub.domain.enums.EmploymentType;
import employeehub.domain.enums.Role;
import lombok.Getter;

/**
 * Safe projection of {@link Employee} for list/detail responses.
 *
 * <p>Returning the raw {@code Employee} entity from list endpoints caused a
 * mid-stream Jackson serialization failure: outside an open Hibernate session,
 * lazy {@code department}/{@code team}/{@code manager} proxies triggered a
 * LazyInitializationException after the HTTP body had already started writing,
 * producing malformed (doubled) JSON. This DTO fully materializes the fields it
 * needs at construction time — no lazy proxies escape to the serializer — and
 * omits the password hash entirely.
 */
@Getter
public class EmployeeResponse {

    private final String id;
    private final String employeeNumber;
    private final String firstName;
    private final String lastName;
    private final String email;
    private final String phone;
    private final String jobTitle;
    private final Role role;
    private final EmploymentStatus employmentStatus;
    private final EmploymentType employmentType;
    private final String profilePhoto;
    private final String startDate;
    private final String endDate;

    // Flattened associations (names only) — safe to touch here because the
    // service method that builds this DTO runs while the session is open.
    private final Long departmentId;
    private final String department;
    private final Long teamId;
    private final String team;
    private final String managerId;
    private final String manager;

    public EmployeeResponse(Employee e) {
        this.id               = e.getId();
        this.employeeNumber   = e.getEmployeeNumber();
        this.firstName        = e.getFirstName();
        this.lastName         = e.getLastName();
        this.email            = e.getEmail();
        this.phone            = e.getPhone();
        this.jobTitle         = e.getJobTitle();
        this.role             = e.getRole();
        this.employmentStatus = e.getEmploymentStatus();
        this.employmentType   = e.getEmploymentType();
        this.profilePhoto     = e.getProfilePhoto();
        this.startDate        = e.getStartDate() != null ? e.getStartDate().toString() : null;
        this.endDate          = e.getEndDate() != null ? e.getEndDate().toString() : null;

        this.departmentId = e.getDepartment() != null ? e.getDepartment().getId() : null;
        this.department   = e.getDepartment() != null ? e.getDepartment().getName() : null;
        this.teamId       = e.getTeam() != null ? e.getTeam().getId() : null;
        this.team         = e.getTeam() != null ? e.getTeam().getName() : null;

        Employee mgr = e.getManager();
        this.managerId = mgr != null ? mgr.getId() : null;
        this.manager   = mgr != null ? mgr.getFirstName() + " " + mgr.getLastName() : null;
    }
}
