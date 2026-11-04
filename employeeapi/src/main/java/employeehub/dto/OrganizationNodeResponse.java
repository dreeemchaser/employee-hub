package employeehub.dto;

import employeehub.domain.Employee;
import employeehub.domain.enums.EmploymentStatus;
import employeehub.domain.enums.Role;
import lombok.Getter;

import java.util.List;

@Getter
public class OrganizationNodeResponse {

    private final String id;
    private final String displayName;
    private final String jobTitle;
    private final Long departmentId;
    private final String departmentName;
    private final Long teamId;
    private final String teamName;
    private final OrganizationPersonSummary manager;
    private final EmploymentStatus employmentStatus;
    private final int childCount;
    private final boolean missingHierarchy;
    private final List<OrganizationNodeResponse> children;

    public OrganizationNodeResponse(Employee employee, List<OrganizationNodeResponse> children) {
        this.id = employee.getId();
        this.displayName = employee.getFirstName() + " " + employee.getLastName();
        this.jobTitle = employee.getJobTitle();
        this.departmentId = employee.getDepartment() != null ? employee.getDepartment().getId() : null;
        this.departmentName = employee.getDepartment() != null ? employee.getDepartment().getName() : null;
        this.teamId = employee.getTeam() != null ? employee.getTeam().getId() : null;
        this.teamName = employee.getTeam() != null ? employee.getTeam().getName() : null;
        this.manager = employee.getManager() != null ? new OrganizationPersonSummary(employee.getManager()) : null;
        this.employmentStatus = employee.getEmploymentStatus();
        this.children = children;
        this.childCount = children.size();
        this.missingHierarchy = employee.getDepartment() == null
                || employee.getTeam() == null
                || (employee.getManager() == null && employee.getEmploymentStatus() == EmploymentStatus.ACTIVE
                    && employee.getRole() != Role.SUPER_ADMIN);
    }
}
