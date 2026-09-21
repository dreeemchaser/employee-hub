package employeehub.service;

import employeehub.domain.Document;
import employeehub.domain.Employee;
import employeehub.domain.enums.DocumentStatus;
import employeehub.domain.enums.DocumentType;
import employeehub.exception.ResourceNotFoundException;
import employeehub.repository.DocumentRepository;
import employeehub.repository.EmployeeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    @Mock DocumentRepository documentRepository;
    @Mock EmployeeRepository employeeRepository;
    @Mock PhotoService photoService;
    @Mock NotificationService notificationService;
    @Mock AuditService auditService;

    @InjectMocks DocumentService documentService;

    private Employee employee;
    private Employee hrAdmin;
    private Document document;

    @BeforeEach
    void setUp() {
        employee = new Employee();
        employee.setId("emp-1");

        hrAdmin = new Employee();
        hrAdmin.setId("hr-1");

        document = new Document();
        document.setId("doc-1");
        document.setEmployee(employee);
        document.setFileName("contract.pdf");
        document.setFileUrl("stored-uuid.pdf");
        document.setStatus(DocumentStatus.PENDING);
    }

    @Test
    void upload_shouldSaveDocumentWithPendingStatus() {
        MockMultipartFile file = new MockMultipartFile("file", "contract.pdf",
                "application/pdf", "content".getBytes());

        when(employeeRepository.findById("emp-1")).thenReturn(Optional.of(employee));
        when(photoService.save(any())).thenReturn("stored-uuid.pdf");
        when(documentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Document result = documentService.upload("emp-1", DocumentType.CONTRACT, file, null);

        assertThat(result.getStatus()).isEqualTo(DocumentStatus.PENDING);
        assertThat(result.getDocumentType()).isEqualTo(DocumentType.CONTRACT);
        assertThat(result.getFileUrl()).isEqualTo("stored-uuid.pdf");
        assertThat(result.getExpiryDate()).isNull();
    }

    @Test
    void upload_shouldSetExpiryDate_whenProvided() {
        MockMultipartFile file = new MockMultipartFile("file", "id.pdf",
                "application/pdf", "content".getBytes());
        LocalDate expiry = LocalDate.now().plusYears(5);

        when(employeeRepository.findById("emp-1")).thenReturn(Optional.of(employee));
        when(photoService.save(any())).thenReturn("stored-uuid.pdf");
        when(documentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Document result = documentService.upload("emp-1", DocumentType.ID, file, expiry);

        assertThat(result.getExpiryDate()).isEqualTo(expiry);
    }

    @Test
    void verify_shouldSetVerifiedAndNotify() {
        when(documentRepository.findById("doc-1")).thenReturn(Optional.of(document));
        when(employeeRepository.findById("hr-1")).thenReturn(Optional.of(hrAdmin));
        when(documentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Document result = documentService.verify("doc-1", "hr-1");

        assertThat(result.getStatus()).isEqualTo(DocumentStatus.VERIFIED);
        assertThat(result.getVerifiedBy()).isEqualTo(hrAdmin);
        verify(notificationService).send(eq(employee), anyString(), anyString(), any(), anyString(), any());
        verify(auditService).log(eq(hrAdmin), eq("VERIFY"), eq("Document"), any(), any(), any());
    }

    @Test
    void reject_shouldSetRejectedAndNotify() {
        when(documentRepository.findById("doc-1")).thenReturn(Optional.of(document));
        when(employeeRepository.findById("hr-1")).thenReturn(Optional.of(hrAdmin));
        when(documentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Document result = documentService.reject("doc-1", "hr-1");

        assertThat(result.getStatus()).isEqualTo(DocumentStatus.REJECTED);
        verify(notificationService).send(eq(employee), anyString(), anyString(), any(), anyString(), any());
        verify(auditService).log(eq(hrAdmin), eq("REJECT"), eq("Document"), any(), any(), any());
    }

    @Test
    void download_shouldThrow_whenDocumentNotFound() {
        when(documentRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> documentService.download("missing"))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
