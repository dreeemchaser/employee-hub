package employeehub.service;

import employeehub.domain.Document;
import employeehub.domain.Employee;
import employeehub.domain.enums.DocumentStatus;
import employeehub.domain.enums.DocumentAccessLevel;
import employeehub.domain.enums.DocumentType;
import employeehub.domain.enums.NotificationType;
import employeehub.domain.enums.Role;
import employeehub.exception.ResourceNotFoundException;
import employeehub.repository.DocumentRepository;
import employeehub.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
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

    @Transactional
    public Document upload(String employeeId, DocumentType type, MultipartFile file, LocalDate expiryDate) {
        Employee employee = findEmployee(employeeId);
        String filename = photoService.save(file);

        Document doc = new Document();
        doc.setEmployee(employee);
        doc.setUploadedBy(employee);
        doc.setDocumentType(type);
        doc.setFileName(file.getOriginalFilename());
        doc.setFileUrl(filename);
        doc.setFileSize(BigDecimal.valueOf(file.getSize()));
        doc.setExpiryDate(expiryDate);
        doc.setAccessLevel(defaultAccessLevel(type));
        Document saved = documentRepository.save(doc);
        auditService.log(employee, "UPLOAD", "Document", saved.getId(), null, saved.getAccessLevel().name());
        return saved;
    }

    public List<Document> getMy(String employeeId) {
        return documentRepository.findByEmployeeId(employeeId);
    }

    public List<Document> getAll() {
        return documentRepository.findAll();
    }

    @Transactional
    public byte[] download(String documentId, String requesterId) {
        Document doc = findDocument(documentId);
        Employee requester = findEmployee(requesterId);
        authorizeRead(doc, requester);
        auditService.log(requester, "DOWNLOAD", "Document", doc.getId(), null, doc.getAccessLevel().name());
        return photoService.load(doc.getFileUrl());
    }

    @Transactional(readOnly = true)
    public Document getDocument(String documentId, String requesterId) {
        Document doc = findDocument(documentId);
        authorizeRead(doc, findEmployee(requesterId));
        return doc;
    }

    @Transactional
    public Document updateAccessLevel(String documentId, DocumentAccessLevel accessLevel, String actorId) {
        Document doc = findDocument(documentId);
        Employee actor = findEmployee(actorId);
        if (!isAdmin(actor)) {
            throw new ResourceNotFoundException("Document not found: " + documentId);
        }
        DocumentAccessLevel previous = doc.getAccessLevel();
        doc.setAccessLevel(accessLevel);
        Document saved = documentRepository.save(doc);
        auditService.log(actor, "ACCESS_LEVEL_CHANGE", "Document", saved.getId(),
                previous.name(), accessLevel.name());
        return saved;
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

    private void authorizeRead(Document doc, Employee requester) {
        if (requester.getId().equals(doc.getEmployee().getId())
                || isAdmin(requester)
                || (requester.getRole() == Role.PAYROLL_ADMIN
                    && doc.getAccessLevel() == DocumentAccessLevel.PAYROLL_ONLY)) {
            return;
        }
        throw new ResourceNotFoundException("Document not found: " + doc.getId());
    }

    private boolean isAdmin(Employee employee) {
        return employee.getRole() == Role.HR_ADMIN
                || employee.getRole() == Role.SUPER_ADMIN;
    }

    private DocumentAccessLevel defaultAccessLevel(DocumentType type) {
        return type == DocumentType.PAYSLIP
                ? DocumentAccessLevel.PAYROLL_ONLY
                : DocumentAccessLevel.STANDARD;
    }
}
