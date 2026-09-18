package employeehub.web;

import employeehub.controller.AuditLogController;
import employeehub.dto.AuditLogResponse;
import employeehub.service.AuditService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuditLogController.class)
class AuditLogControllerTest extends WebMvcTestSupport {

    @MockitoBean
    AuditService auditService;

    @Test
    @WithMockUser(username = "hr@employeehub.com", roles = "HR_ADMIN")
    void getAll_asHrAdmin_returnsOk() throws Exception {
        Page<employeehub.domain.AuditLog> empty = new PageImpl<>(List.of());
        when(auditService.getAll(any(), any(), any(), any(), any())).thenReturn(empty);

        mockMvc.perform(get("/audit-logs"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "user@employeehub.com", roles = "EMPLOYEE")
    void getAll_asEmployee_isForbidden() throws Exception {
        // /audit-logs/** is HR_ADMIN/SUPER_ADMIN only.
        mockMvc.perform(get("/audit-logs"))
                .andExpect(status().isForbidden());
    }
}
