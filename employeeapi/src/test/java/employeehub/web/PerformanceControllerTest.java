package employeehub.web;

import employeehub.controller.PerformanceController;
import employeehub.domain.Employee;
import employeehub.service.EmployeeService;
import employeehub.service.PerformanceService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PerformanceController.class)
class PerformanceControllerTest extends WebMvcTestSupport {

    @MockitoBean
    PerformanceService performanceService;
    @MockitoBean
    EmployeeService employeeService;

    private void stubCaller() {
        Employee e = new Employee();
        e.setId("emp-1");
        when(employeeService.getByEmail(any())).thenReturn(e);
    }

    @Test
    @WithMockUser(username = "user@employeehub.com", roles = "EMPLOYEE")
    void getMyGoals_asEmployee_returnsOk() throws Exception {
        stubCaller();
        when(performanceService.getMyGoals(any())).thenReturn(List.of());

        mockMvc.perform(get("/performance/goals/my"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "user@employeehub.com", roles = "EMPLOYEE")
    void getMyReviews_asEmployee_returnsOk() throws Exception {
        stubCaller();
        when(performanceService.getMyReviews(any())).thenReturn(List.of());

        mockMvc.perform(get("/performance/reviews/my"))
                .andExpect(status().isOk());
    }

    @Test
    void getMyGoals_whenUnauthenticated_isForbidden() throws Exception {
        mockMvc.perform(get("/performance/goals/my"))
                .andExpect(status().isForbidden());
    }
}
