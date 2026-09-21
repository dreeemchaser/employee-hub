package employeehub.web;

import employeehub.controller.DocumentController;
import employeehub.domain.Document;
import employeehub.domain.Employee;
import employeehub.domain.enums.DocumentStatus;
import employeehub.domain.enums.DocumentType;
import employeehub.service.DocumentService;
import employeehub.service.EmployeeService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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

    @Test
    @WithMockUser(username = "user@employeehub.com", roles = "EMPLOYEE")
    void upload_withoutExpiryDate_returnsCreated() throws Exception {
        stubCaller();
        MockMultipartFile file = new MockMultipartFile("file", "id.pdf", "application/pdf", "content".getBytes());
        Document saved = new Document();
        saved.setId("doc-1");
        saved.setEmployee(new Employee());
        saved.setDocumentType(DocumentType.ID);
        saved.setStatus(DocumentStatus.PENDING);
        when(documentService.upload(any(), any(), any(), isNull())).thenReturn(saved);

        mockMvc.perform(multipart("/documents/upload")
                        .file(file)
                        .param("type", "ID")
                        .with(csrf()))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(username = "user@employeehub.com", roles = "EMPLOYEE")
    void upload_withFutureExpiryDate_returnsCreated() throws Exception {
        stubCaller();
        MockMultipartFile file = new MockMultipartFile("file", "id.pdf", "application/pdf", "content".getBytes());
        Document saved = new Document();
        saved.setId("doc-1");
        saved.setEmployee(new Employee());
        saved.setDocumentType(DocumentType.ID);
        saved.setStatus(DocumentStatus.PENDING);
        saved.setExpiryDate(LocalDate.now().plusYears(1));
        when(documentService.upload(any(), any(), any(), any())).thenReturn(saved);

        mockMvc.perform(multipart("/documents/upload")
                        .file(file)
                        .param("type", "ID")
                        .param("expiryDate", LocalDate.now().plusYears(1).toString())
                        .with(csrf()))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(username = "user@employeehub.com", roles = "EMPLOYEE")
    void upload_withPastExpiryDate_isBadRequest() throws Exception {
        stubCaller();
        MockMultipartFile file = new MockMultipartFile("file", "id.pdf", "application/pdf", "content".getBytes());

        mockMvc.perform(multipart("/documents/upload")
                        .file(file)
                        .param("type", "ID")
                        .param("expiryDate", LocalDate.now().minusDays(1).toString())
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }
}
