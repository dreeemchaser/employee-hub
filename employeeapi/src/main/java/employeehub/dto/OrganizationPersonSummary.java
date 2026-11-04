package employeehub.dto;

import employeehub.domain.Employee;
import lombok.Getter;

@Getter
public class OrganizationPersonSummary {

    private final String id;
    private final String displayName;
    private final String jobTitle;

    public OrganizationPersonSummary(Employee employee) {
        this.id = employee.getId();
        this.displayName = employee.getFirstName() + " " + employee.getLastName();
        this.jobTitle = employee.getJobTitle();
    }
}
