package employeehub.web;

import employeehub.controller.DepartmentController;
import employeehub.domain.Department;
import employeehub.exception.BusinessRuleException;
import employeehub.service.DepartmentService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proof-of-concept web-layer slice: proves the shared {@link WebMvcTestSupport} wiring loads the real
 * {@code SecurityConfig} and that its route rules are actually enforced against the {@code @WithMockUser}
 * principal. Departments: any authenticated user may GET; only HR_ADMIN / SUPER_ADMIN may write.
 */
@WebMvcTest(DepartmentController.class)
class DepartmentControllerTest extends WebMvcTestSupport {

    @MockitoBean
    DepartmentService departmentService;

    private Department department(Long id, String name) {
        Department d = new Department();
        d.setId(id);
        d.setName(name);
        return d;
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void getAll_asAuthenticatedEmployee_returnsOkWithEnvelope() throws Exception {
        when(departmentService.getAll()).thenReturn(List.of(department(1L, "Technology")));

        mockMvc.perform(get("/departments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].name").value("Technology"));
    }

    @Test
    void getAll_whenUnauthenticated_isForbidden() throws Exception {
        // No AuthenticationEntryPoint is configured, so Spring Security answers unauthenticated
        // requests to a protected route with 403 rather than 401. Pinning the app's actual behavior.
        mockMvc.perform(get("/departments"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "HR_ADMIN")
    void create_asHrAdmin_returnsCreated() throws Exception {
        Department saved = department(2L, "Finance");
        when(departmentService.create(any())).thenReturn(saved);

        mockMvc.perform(post("/departments").with(csrf())
                        .contentType("application/json")
                        .content("{\"name\":\"Finance\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Finance"));
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void create_asEmployee_isForbidden() throws Exception {
        mockMvc.perform(post("/departments").with(csrf())
                        .contentType("application/json")
                        .content("{\"name\":\"Finance\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "HR_ADMIN")
    void create_whenDuplicate_mapsToConflict() throws Exception {
        when(departmentService.create(any()))
                .thenThrow(new BusinessRuleException("Department already exists: Finance"));

        mockMvc.perform(post("/departments").with(csrf())
                        .contentType("application/json")
                        .content("{\"name\":\"Finance\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Department already exists: Finance"));
    }
}
