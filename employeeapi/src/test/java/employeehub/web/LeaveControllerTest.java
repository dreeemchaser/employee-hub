package employeehub.web;

import employeehub.controller.LeaveController;
import employeehub.domain.Employee;
import employeehub.service.EmployeeService;
import employeehub.service.LeaveService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LeaveController.class)
class LeaveControllerTest extends WebMvcTestSupport {

    @MockitoBean
    LeaveService leaveService;
    @MockitoBean
    EmployeeService employeeService;

    private void stubCaller() {
        Employee e = new Employee();
        e.setId("emp-1");
        e.setEmail("user@employeehub.com");
        when(employeeService.getByEmail(any())).thenReturn(e);
    }

    @Test
    @WithMockUser(username = "hr@employeehub.com", roles = "HR_ADMIN")
    void getAll_asHrAdmin_returnsOk() throws Exception {
        stubCaller();
        when(leaveService.getAllRequests(any())).thenReturn(List.of());

        mockMvc.perform(get("/leave/requests"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(username = "user@employeehub.com", roles = "EMPLOYEE")
    void getAll_asEmployee_isForbidden() throws Exception {
        // GET /leave/requests is HR_ADMIN/SUPER_ADMIN/MANAGER only.
        mockMvc.perform(get("/leave/requests"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "user@employeehub.com", roles = "EMPLOYEE")
    void getMy_asEmployee_returnsOk() throws Exception {
        stubCaller();
        when(leaveService.getMyRequests(any())).thenReturn(List.of());

        mockMvc.perform(get("/leave/requests/my"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "user@employeehub.com", roles = "EMPLOYEE")
    void approve_asEmployee_isForbidden() throws Exception {
        mockMvc.perform(patch("/leave/requests/req-1/approve").with(csrf()))
                .andExpect(status().isForbidden());
    }
}
