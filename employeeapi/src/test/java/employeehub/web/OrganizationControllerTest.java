package employeehub.web;

import employeehub.controller.OrganizationController;
import employeehub.domain.Employee;
import employeehub.domain.enums.Role;
import employeehub.dto.OrganizationNodeResponse;
import employeehub.service.EmployeeService;
import employeehub.service.OrganizationService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrganizationController.class)
class OrganizationControllerTest extends WebMvcTestSupport {

    @MockitoBean
    OrganizationService organizationService;

    @MockitoBean
    EmployeeService employeeService;

    @Test
    @WithMockUser(username = "employee@employeehub.com", roles = "EMPLOYEE")
    void getTree_asAuthenticatedEmployee_returnsOk() throws Exception {
        when(employeeService.getByEmail("employee@employeehub.com")).thenReturn(employee("employee", Role.EMPLOYEE));
        when(organizationService.getTree(any(), isNull(), isNull(), isNull(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/organization/tree"))
                .andExpect(status().isOk());
    }

    @Test
    void getTree_whenUnauthenticated_isForbidden() throws Exception {
        mockMvc.perform(get("/organization/tree"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "employee@employeehub.com", roles = "EMPLOYEE")
    void getNode_delegatesCallerScopeToService() throws Exception {
        when(employeeService.getByEmail("employee@employeehub.com")).thenReturn(employee("employee", Role.EMPLOYEE));
        when(organizationService.getNode(any(), eq("node-id")))
                .thenReturn(new OrganizationNodeResponse(employee("node-id", Role.EMPLOYEE), List.of()));

        mockMvc.perform(get("/organization/nodes/node-id"))
                .andExpect(status().isOk());
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
