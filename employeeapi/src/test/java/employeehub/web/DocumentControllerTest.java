package employeehub.web;

import employeehub.controller.DocumentController;
import employeehub.domain.Employee;
import employeehub.service.DocumentService;
import employeehub.service.EmployeeService;
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

@WebMvcTest(DocumentController.class)
class DocumentControllerTest extends WebMvcTestSupport {

    @MockitoBean
    DocumentService documentService;
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
        when(documentService.getMy(any())).thenReturn(List.of());

        mockMvc.perform(get("/documents/my"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "user@employeehub.com", roles = "EMPLOYEE")
    void getAll_asEmployee_isForbidden() throws Exception {
        // GET /documents is HR_ADMIN/SUPER_ADMIN only.
        mockMvc.perform(get("/documents"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "hr@employeehub.com", roles = "HR_ADMIN")
    void getAll_asHrAdmin_returnsOk() throws Exception {
        when(documentService.getAll()).thenReturn(List.of());

        mockMvc.perform(get("/documents"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "user@employeehub.com", roles = "EMPLOYEE")
    void verify_asEmployee_isForbidden() throws Exception {
        // PATCH /documents/{id}/verify is HR_ADMIN/SUPER_ADMIN only.
        mockMvc.perform(patch("/documents/doc-1/verify").with(csrf()))
                .andExpect(status().isForbidden());
    }
}
