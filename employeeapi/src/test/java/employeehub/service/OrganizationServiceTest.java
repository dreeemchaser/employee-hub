package employeehub.service;

import employeehub.domain.Employee;
import employeehub.domain.enums.Role;
import employeehub.repository.EmployeeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrganizationServiceTest {

    @Mock
    EmployeeRepository employeeRepository;

    @InjectMocks
    OrganizationService organizationService;

    @Test
    void getNode_whenManagerRequestsDescendant_returnsNode() {
        Employee manager = employee("manager", Role.MANAGER);
        Employee report = employee("report", Role.EMPLOYEE);
        report.setManager(manager);

        when(employeeRepository.findById("report")).thenReturn(java.util.Optional.of(report));
        when(employeeRepository.existsInManagerScope("manager", "report")).thenReturn(true);
        when(employeeRepository.findByManager_IdOrderByLastNameAscFirstNameAsc("report"))
                .thenReturn(List.of());

        var result = organizationService.getNode(manager, "report");

        assertThat(result.getId()).isEqualTo("report");
        assertThat(result.getManager().getId()).isEqualTo("manager");
    }

    @Test
    void getNode_whenEmployeeRequestsUnrelatedNode_returnsNotFound() {
        Employee employee = employee("employee", Role.EMPLOYEE);
        Employee unrelated = employee("unrelated", Role.EMPLOYEE);

        when(employeeRepository.findById("unrelated")).thenReturn(java.util.Optional.of(unrelated));

        assertThatThrownBy(() -> organizationService.getNode(employee, "unrelated"))
                .isInstanceOf(employeehub.exception.ResourceNotFoundException.class);
    }

    @Test
    void search_whenHrAdmin_usesOrganizationWideQuery() {
        Employee hr = employee("hr", Role.HR_ADMIN);
        var page = new PageImpl<>(List.of(employee("employee", Role.EMPLOYEE)));
        when(employeeRepository.searchOrganization(any(), isNull(), isNull(), isNull(), any()))
                .thenReturn(page);

        var result = organizationService.search(hr, null, null, null, null, PageRequest.of(0, 20));

        assertThat(result.getContent()).hasSize(1);
    }

    private Employee employee(String id, Role role) {
        Employee employee = new Employee();
        employee.setId(id);
        employee.setFirstName(id);
        employee.setLastName("User");
        employee.setJobTitle("Role");
        employee.setRole(role);
        return employee;
    }
}
