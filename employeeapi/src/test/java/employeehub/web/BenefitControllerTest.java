package employeehub.web;

import employeehub.controller.BenefitController;
import employeehub.domain.Employee;
import employeehub.service.BenefitService;
import employeehub.service.EmployeeService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BenefitController.class)
class BenefitControllerTest extends WebMvcTestSupport {

    @MockitoBean
    BenefitService benefitService;
    @MockitoBean
    EmployeeService employeeService;

    private void stubCaller() {
        Employee e = new Employee();
        e.setId("emp-1");
        when(employeeService.getByEmail(any())).thenReturn(e);
    }

    @Test
    @WithMockUser(username = "user@employeehub.com", roles = "EMPLOYEE")
    void getAllTypes_asAuthenticated_returnsOk() throws Exception {
        when(benefitService.getAllTypes()).thenReturn(List.of());

        mockMvc.perform(get("/benefits"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void getAllTypes_whenUnauthenticated_isForbidden() throws Exception {
        mockMvc.perform(get("/benefits"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "user@employeehub.com", roles = "EMPLOYEE")
    void getMy_asEmployee_returnsOk() throws Exception {
        stubCaller();
        when(benefitService.getMyBenefits(any())).thenReturn(List.of());

        mockMvc.perform(get("/benefits/my"))
                .andExpect(status().isOk());
    }
}
