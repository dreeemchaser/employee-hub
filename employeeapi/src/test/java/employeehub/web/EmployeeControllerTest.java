package employeehub.web;

import employeehub.controller.EmployeeController;
import employeehub.domain.Employee;
import employeehub.domain.enums.EmploymentStatus;
import employeehub.domain.enums.Role;
import employeehub.exception.BusinessRuleException;
import employeehub.exception.ResourceNotFoundException;
import employeehub.service.EmployeeService;
import employeehub.service.PhotoService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EmployeeController.class)
class EmployeeControllerTest extends WebMvcTestSupport {

    @MockitoBean
    EmployeeService employeeService;

    @MockitoBean
    PhotoService photoService;

    private Employee sampleEmployee() {
        Employee e = new Employee();
        e.setId("emp-1");
        e.setEmployeeNumber("EMP-001");
        e.setFirstName("Thandi");
        e.setLastName("Mokoena");
        e.setEmail("thandi@employeehub.com");
        e.setPassword("$2a$bcrypt-hash");           // must never serialize
        e.setIdNumber("9001015800083");             // SA ID — PII, must never serialize
        e.setRole(Role.EMPLOYEE);
        e.setEmploymentStatus(EmploymentStatus.ACTIVE);
        return e;
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void getById_asAuthenticated_returnsDtoWithoutPasswordOrIdNumber() throws Exception {
        when(employeeService.getById("emp-1")).thenReturn(sampleEmployee());

        mockMvc.perform(get("/employees/emp-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value("thandi@employeehub.com"))
                // PII / secret absence — the whole reason EmployeeResponse exists.
                .andExpect(jsonPath("$..password").isEmpty())
                .andExpect(jsonPath("$..idNumber").isEmpty());
    }

    @Test
    void getById_whenUnauthenticated_isForbidden() throws Exception {
        mockMvc.perform(get("/employees/emp-1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void getById_whenNotFound_isNotFound() throws Exception {
        when(employeeService.getById("missing")).thenThrow(new ResourceNotFoundException("Employee not found: missing"));

        mockMvc.perform(get("/employees/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @WithMockUser(roles = "HR_ADMIN")
    void create_asHrAdmin_returnsCreated() throws Exception {
        when(employeeService.create(any())).thenReturn(sampleEmployee());

        mockMvc.perform(post("/employees").with(csrf())
                        .contentType("application/json")
                        .content("{\"firstName\":\"Thandi\",\"lastName\":\"Mokoena\",\"email\":\"thandi@employeehub.com\",\"password\":\"Secret@123\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.employeeNumber").value("EMP-001"))
                .andExpect(jsonPath("$..password").isEmpty());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void create_asEmployee_isForbidden() throws Exception {
        mockMvc.perform(post("/employees").with(csrf())
                        .contentType("application/json")
                        .content("{\"email\":\"x@y.com\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "hr@employeehub.com", roles = "HR_ADMIN")
    void offboard_asHrAdmin_returnsOk() throws Exception {
        Employee actor = sampleEmployee();
        actor.setId("hr-1");
        when(employeeService.getByEmail(any())).thenReturn(actor);
        Employee terminated = sampleEmployee();
        terminated.setEmploymentStatus(EmploymentStatus.TERMINATED);
        when(employeeService.offboard(any(), any(), any())).thenReturn(terminated);

        mockMvc.perform(post("/employees/emp-1/offboard").with(csrf())
                        .contentType("application/json")
                        .content("{\"reason\":\"Resignation\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.employmentStatus").value("TERMINATED"));
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void offboard_asEmployee_isForbidden() throws Exception {
        mockMvc.perform(post("/employees/emp-1/offboard").with(csrf())
                        .contentType("application/json")
                        .content("{\"reason\":\"Resignation\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "hr@employeehub.com", roles = "HR_ADMIN")
    void offboard_whenAlreadyTerminated_returnsConflict() throws Exception {
        when(employeeService.getByEmail(any())).thenReturn(sampleEmployee());
        when(employeeService.offboard(any(), any(), any()))
                .thenThrow(new BusinessRuleException("Employee is already terminated: emp-1"));

        mockMvc.perform(post("/employees/emp-1/offboard").with(csrf())
                        .contentType("application/json")
                        .content("{\"reason\":\"Resignation\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(username = "hr@employeehub.com", roles = "HR_ADMIN")
    void offboard_withBlankReason_isBadRequest() throws Exception {
        mockMvc.perform(post("/employees/emp-1/offboard").with(csrf())
                        .contentType("application/json")
                        .content("{\"reason\":\"\"}"))
                .andExpect(status().isBadRequest());
    }
}
