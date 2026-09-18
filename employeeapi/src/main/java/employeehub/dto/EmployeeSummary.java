package employeehub.dto;

import employeehub.domain.Employee;
import lombok.Getter;

/**
 * Minimal, safe projection of an {@link Employee} for embedding inside other
 * response DTOs. Carries only identity + display fields — never {@code idNumber}
 * (SA ID, PII), {@code password}, salary, or the full entity graph. Preserves
 * the nested {@code {id, firstName, lastName}} shape the frontends consume.
 */
@Getter
public class EmployeeSummary {

    private final String id;
    private final String firstName;
    private final String lastName;
    private final String employeeNumber;

    public EmployeeSummary(Employee e) {
        this.id = e.getId();
        this.firstName = e.getFirstName();
        this.lastName = e.getLastName();
        this.employeeNumber = e.getEmployeeNumber();
    }

    public static EmployeeSummary of(Employee e) {
        return e != null ? new EmployeeSummary(e) : null;
    }
}
