package employeehub.web;

import employeehub.controller.TimesheetController;
import employeehub.domain.Employee;
import employeehub.service.EmployeeService;
import employeehub.service.TimesheetService;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TimesheetController.class)
class TimesheetControllerTest extends WebMvcTestSupport {

    @MockitoBean
    TimesheetService timesheetService;
    @MockitoBean
    EmployeeService employeeService;

    private void stubCaller() {
        Employee e = new Employee();
        e.setId("emp-1");
        when(employeeService.getByEmail(any())).thenReturn(e);
    }

    @Test
    @WithMockUser(username = "user@employeehub.com", roles = "EMPLOYEE")
    void getMy_asEmployee_returnsOk() throws Exception {
        stubCaller();
        when(timesheetService.getMy(any())).thenReturn(List.of());

        mockMvc.perform(get("/timesheets/my"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "user@employeehub.com", roles = "EMPLOYEE")
    void getAll_asEmployee_isForbidden() throws Exception {
        // GET /timesheets is HR_ADMIN/SUPER_ADMIN/MANAGER only.
        mockMvc.perform(get("/timesheets"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "mgr@employeehub.com", roles = "MANAGER")
    void approve_asManager_passesAuthorizationAndReachesService() throws Exception {
        stubCaller();
        // Route allows MANAGER. Throw from the service so we prove the request cleared authorization
        // and reached the handler (404), rather than being refused at the route (403).
        when(timesheetService.approve(any(), any()))
                .thenThrow(new employeehub.exception.ResourceNotFoundException("Timesheet not found"));

        mockMvc.perform(patch("/timesheets/ts-1/approve").with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "user@employeehub.com", roles = "EMPLOYEE")
    void approve_asEmployee_isForbidden() throws Exception {
        mockMvc.perform(patch("/timesheets/ts-1/approve").with(csrf()))
                .andExpect(status().isForbidden());
    }
}
