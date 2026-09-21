package employeehub.dto;

import employeehub.domain.Document;
import employeehub.domain.enums.DocumentStatus;
import employeehub.domain.enums.DocumentType;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Safe projection of {@link Document}. The lazy {@code employee},
 * {@code uploadedBy}, and {@code verifiedBy} associations become safe summaries
 * — no {@code idNumber}/{@code password} or entity graph escapes. The internal
 * {@code fileUrl} storage path is deliberately omitted; downloads go through
 * {@code GET /documents/{id}/download}.
 */
@Getter
public class DocumentResponse {

    private final String id;
    private final EmployeeSummary employee;
    private final DocumentType documentType;
    private final String fileName;
    private final BigDecimal fileSize;
    private final EmployeeSummary uploadedBy;
    private final EmployeeSummary verifiedBy;
    private final LocalDateTime verifiedAt;
    private final DocumentStatus status;
    private final LocalDateTime createdAt;
    private final LocalDate expiryDate;
    // Computed at construction time, never persisted -- expiry is a function of
    // "now" vs expiryDate, not a stored fact that could drift out of sync.
    private final boolean isExpired;

    public DocumentResponse(Document d) {
        this.id = d.getId();
        this.employee = EmployeeSummary.of(d.getEmployee());
        this.documentType = d.getDocumentType();
        this.fileName = d.getFileName();
        this.fileSize = d.getFileSize();
        this.uploadedBy = EmployeeSummary.of(d.getUploadedBy());
        this.verifiedBy = EmployeeSummary.of(d.getVerifiedBy());
        this.verifiedAt = d.getVerifiedAt();
        this.status = d.getStatus();
        this.createdAt = d.getCreatedAt();
        this.expiryDate = d.getExpiryDate();
        this.isExpired = d.getExpiryDate() != null && d.getExpiryDate().isBefore(LocalDate.now());
    }

    public static List<DocumentResponse> from(List<Document> documents) {
        return documents.stream().map(DocumentResponse::new).toList();
    }
}
