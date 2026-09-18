package employeehub.web;

import employeehub.controller.SalaryController;
import employeehub.domain.Employee;
import employeehub.service.EmployeeService;
import employeehub.service.SalaryService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SalaryController.class)
class SalaryControllerTest extends WebMvcTestSupport {

    @MockitoBean
    SalaryService salaryService;
    @MockitoBean
    EmployeeService employeeService;

    private void stubCaller() {
        Employee e = new Employee();
        e.setId("emp-1");
        when(employeeService.getByEmail(any())).thenReturn(e);
    }

    @Test
    @WithMockUser(username = "user@employeehub.com", roles = "EMPLOYEE")
    void getMyPayslips_asEmployee_returnsOk() throws Exception {
        stubCaller();
        when(salaryService.getMyPaySlips(any())).thenReturn(List.of());

        mockMvc.perform(get("/salary/payslips/my"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "user@employeehub.com", roles = "EMPLOYEE")
    void getAllIncreaseRequests_asEmployee_isForbidden() throws Exception {
        // /salary/** (except the explicitly-authenticated my/increase-request endpoints)
        // is PAYROLL_ADMIN/HR_ADMIN/SUPER_ADMIN only.
        mockMvc.perform(get("/salary/increase-requests"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "payroll@employeehub.com", roles = "PAYROLL_ADMIN")
    void getAllIncreaseRequests_asPayrollAdmin_returnsOk() throws Exception {
        when(salaryService.getAllIncreaseRequests()).thenReturn(List.of());

        mockMvc.perform(get("/salary/increase-requests"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "user@employeehub.com", roles = "EMPLOYEE")
    void createRecord_asEmployee_isForbidden() throws Exception {
        mockMvc.perform(post("/salary/records").with(csrf())
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isForbidden());
    }
}
