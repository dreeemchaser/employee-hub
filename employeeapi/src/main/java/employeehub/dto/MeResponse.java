package employeehub.dto;

import employeehub.domain.Employee;
import employeehub.domain.enums.EmploymentStatus;
import employeehub.domain.enums.EmploymentType;
import employeehub.domain.enums.Role;
import lombok.Getter;

@Getter
public class MeResponse {

    private final String id;
    private final String employeeNumber;
    private final String firstName;
    private final String lastName;
    private final String email;
    private final String phone;
    private final String jobTitle;
    private final String address;
    private final String nationality;
    private final String gender;
    private final Role role;
    private final EmploymentStatus employmentStatus;
    private final EmploymentType employmentType;
    private final String profilePhoto;
    private final String department;
    private final String team;
    private final String startDate;

    public MeResponse(Employee employee) {
        this.id               = employee.getId();
        this.employeeNumber   = employee.getEmployeeNumber();
        this.firstName        = employee.getFirstName();
        this.lastName         = employee.getLastName();
        this.email            = employee.getEmail();
        this.phone            = employee.getPhone();
        this.jobTitle         = employee.getJobTitle();
        this.address          = employee.getAddress();
        this.nationality      = employee.getNationality();
        this.gender           = employee.getGender();
        this.role             = employee.getRole();
        this.employmentStatus = employee.getEmploymentStatus();
        this.employmentType   = employee.getEmploymentType();
        this.profilePhoto     = employee.getProfilePhoto() != null ? employee.getProfilePhoto() : "";
        this.department       = employee.getDepartment() != null ? employee.getDepartment().getName() : null;
        this.team             = employee.getTeam() != null ? employee.getTeam().getName() : null;
        this.startDate        = employee.getStartDate() != null ? employee.getStartDate().toString() : null;
    }
}
