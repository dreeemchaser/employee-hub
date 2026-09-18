package employeehub.controller;

import employeehub.domain.Document;
import employeehub.domain.enums.DocumentType;
import employeehub.dto.ApiResponse;
import employeehub.dto.DocumentResponse;
import employeehub.service.EmployeeService;
import employeehub.service.DocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/documents")
@RequiredArgsConstructor
@Tag(name = "Documents")
public class DocumentController {

    private final DocumentService documentService;
    private final EmployeeService employeeService;

    @PostMapping("/upload")
    @Operation(summary = "Upload a document")
    public ResponseEntity<ApiResponse<DocumentResponse>> upload(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam DocumentType type,
            @RequestParam("file") MultipartFile file) {
        var employee = employeeService.getByEmail(userDetails.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(new DocumentResponse(documentService.upload(employee.getId(), type, file))));
    }

    @GetMapping("/my")
    @Operation(summary = "Get current employee's documents")
    public ResponseEntity<ApiResponse<List<DocumentResponse>>> getMy(@AuthenticationPrincipal UserDetails userDetails) {
        var employee = employeeService.getByEmail(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.ok(DocumentResponse.from(documentService.getMy(employee.getId()))));
    }

    @GetMapping
    @Operation(summary = "Get all documents (HR_ADMIN only)")
    public ResponseEntity<ApiResponse<List<DocumentResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.ok(DocumentResponse.from(documentService.getAll())));
    }

    @GetMapping("/{id}/download")
    @Operation(summary = "Download a document")
    public ResponseEntity<byte[]> download(@PathVariable String id) {
        Document doc = documentService.getDocument(id);
        byte[] data = documentService.download(id);
        String disposition = "attachment; filename=\"" + doc.getFileName() + "\"";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .body(data);
    }

    @PatchMapping("/{id}/verify")
    @Operation(summary = "Verify a document (HR only)")
    public ResponseEntity<ApiResponse<DocumentResponse>> verify(
            @PathVariable String id,
            @AuthenticationPrincipal UserDetails userDetails) {
        var verifier = employeeService.getByEmail(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.ok(new DocumentResponse(documentService.verify(id, verifier.getId()))));
    }

    @PatchMapping("/{id}/reject")
    @Operation(summary = "Reject a document (HR only)")
    public ResponseEntity<ApiResponse<DocumentResponse>> reject(
            @PathVariable String id,
            @AuthenticationPrincipal UserDetails userDetails) {
        var verifier = employeeService.getByEmail(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.ok(new DocumentResponse(documentService.reject(id, verifier.getId()))));
    }
}
