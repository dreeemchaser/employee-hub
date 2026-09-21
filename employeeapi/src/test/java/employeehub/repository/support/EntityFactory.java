package employeehub.repository.support;

import employeehub.domain.Department;
import employeehub.domain.Document;
import employeehub.domain.Employee;
import employeehub.domain.LeaveRequest;
import employeehub.domain.LeaveType;
import employeehub.domain.Team;
import employeehub.domain.enums.DocumentStatus;
import employeehub.domain.enums.DocumentType;
import employeehub.domain.enums.EmploymentStatus;
import employeehub.domain.enums.EmploymentType;
import employeehub.domain.enums.LeaveStatus;
import employeehub.domain.enums.Role;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Minimal, valid domain fixtures for repository integration tests. Populates the NOT NULL columns
 * so entities persist against the real schema, and exposes {@code with...}-style overrides via the
 * returned mutable entities. Keeps per-test setup short (Testing steering X1).
 */
public final class EntityFactory {

    private EntityFactory() {
    }

    public static Department department(String name) {
        Department d = new Department();
        d.setName(name);
        d.setDescription(name + " department");
        return d;
    }

    public static Team team(String name, Department department) {
        Team t = new Team();
        t.setName(name);
        t.setDepartment(department);
        return t;
    }

    /**
     * A valid ACTIVE employee with all NOT NULL fields populated. Caller sets the employeeNumber and
     * email to unique values and may override department/team/manager/role.
     */
    public static Employee employee(String employeeNumber, String email, Department department, Team team) {
        Employee e = new Employee();
        e.setEmployeeNumber(employeeNumber);
        e.setFirstName("First");
        e.setLastName("Last");
        e.setEmail(email);
        e.setPassword("$2a$10$notarealhash");
        e.setJobTitle("Tester");
        e.setEmploymentType(EmploymentType.FULL_TIME);
        e.setEmploymentStatus(EmploymentStatus.ACTIVE);
        e.setStartDate(LocalDate.of(2024, 1, 1));
        e.setRole(Role.EMPLOYEE);
        e.setDepartment(department);
        e.setTeam(team);
        return e;
    }

    public static LeaveType leaveType(String name) {
        LeaveType lt = new LeaveType();
        lt.setName(name);
        lt.setDefaultDays(21);
        lt.setCycleYears(1);
        lt.setRequiresDocumentation(false);
        lt.setIsPaid(true);
        return lt;
    }

    /**
     * A PENDING leave request for the given employee/type over [start, end]. Caller may override
     * status/approvedBy for approval-path tests.
     */
    public static LeaveRequest leaveRequest(Employee employee, LeaveType type,
                                            LocalDate start, LocalDate end) {
        LeaveRequest lr = new LeaveRequest();
        lr.setEmployee(employee);
        lr.setLeaveType(type);
        lr.setStartDate(start);
        lr.setEndDate(end);
        lr.setTotalDays(BigDecimal.valueOf(java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1));
        lr.setStatus(LeaveStatus.PENDING);
        return lr;
    }

    /**
     * A PENDING document, uploaded by and belonging to the same employee, with no expiry date set.
     * Caller overrides status/expiryDate/reminder-sent columns as needed for the case under test.
     */
    public static Document document(Employee employee, DocumentType type) {
        Document d = new Document();
        d.setEmployee(employee);
        d.setUploadedBy(employee);
        d.setDocumentType(type);
        d.setFileName(type + ".pdf");
        d.setFileUrl("stored-" + type + ".pdf");
        d.setStatus(DocumentStatus.PENDING);
        return d;
    }
}
