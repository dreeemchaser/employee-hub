package employeehub.service;

import employeehub.domain.Document;
import employeehub.domain.Employee;
import employeehub.domain.enums.DocumentStatus;
import employeehub.domain.enums.DocumentType;
import employeehub.domain.enums.NotificationType;
import employeehub.exception.ResourceNotFoundException;
import employeehub.repository.DocumentRepository;
import employeehub.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final EmployeeRepository employeeRepository;
    private final PhotoService photoService;
    private final NotificationService notificationService;
    private final AuditService auditService;

    public Document upload(String employeeId, DocumentType type, MultipartFile file) {
        Employee employee = findEmployee(employeeId);
        String filename = photoService.save(file);

        Document doc = new Document();
        doc.setEmployee(employee);
        doc.setUploadedBy(employee);
        doc.setDocumentType(type);
        doc.setFileName(file.getOriginalFilename());
        doc.setFileUrl(filename);
        doc.setFileSize(BigDecimal.valueOf(file.getSize()));
        return documentRepository.save(doc);
    }

    public List<Document> getMy(String employeeId) {
        return documentRepository.findByEmployeeId(employeeId);
    }

    public List<Document> getAll() {
        return documentRepository.findAll();
    }

    public byte[] download(String documentId) {
        Document doc = findDocument(documentId);
        return photoService.load(doc.getFileUrl());
    }

    public Document getDocument(String documentId) {
        return findDocument(documentId);
    }

    @Transactional
    public Document verify(String documentId, String verifierId) {
        Document doc = findDocument(documentId);
        Employee verifier = findEmployee(verifierId);
        doc.setStatus(DocumentStatus.VERIFIED);
        doc.setVerifiedBy(verifier);
        doc.setVerifiedAt(LocalDateTime.now());
        Document saved = documentRepository.save(doc);

        notificationService.send(doc.getEmployee(),
                "Document Verified",
                "Your document '" + doc.getFileName() + "' has been verified",
                NotificationType.DOCUMENT, "Document", saved.getId());
        auditService.log(verifier, "VERIFY", "Document", saved.getId(), "PENDING", "VERIFIED");
        return saved;
    }

    @Transactional
    public Document reject(String documentId, String verifierId) {
        Document doc = findDocument(documentId);
        Employee verifier = findEmployee(verifierId);
        doc.setStatus(DocumentStatus.REJECTED);
        doc.setVerifiedBy(verifier);
        doc.setVerifiedAt(LocalDateTime.now());
        Document saved = documentRepository.save(doc);

        notificationService.send(doc.getEmployee(),
                "Document Rejected",
                "Your document '" + doc.getFileName() + "' has been rejected",
                NotificationType.DOCUMENT, "Document", saved.getId());
        auditService.log(verifier, "REJECT", "Document", saved.getId(), "PENDING", "REJECTED");
        return saved;
    }

    private Document findDocument(String id) {
        return documentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + id));
    }

    private Employee findEmployee(String id) {
        return employeeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found: " + id));
    }
}
