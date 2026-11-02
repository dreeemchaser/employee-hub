package employeehub.controller;

import employeehub.domain.enums.PolicyStatus;
import employeehub.dto.ApiResponse;
import employeehub.dto.LeavePolicyRequest;
import employeehub.dto.LeavePolicyResponse;
import employeehub.service.EmployeeService;
import employeehub.service.LeavePolicyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/leave/policies")
@RequiredArgsConstructor
@Tag(name = "Leave Policies")
public class LeavePolicyController {

    private final LeavePolicyService policyService;
    private final EmployeeService employeeService;

    @GetMapping
    @Operation(summary = "Get active leave policies")
    public ResponseEntity<ApiResponse<Page<LeavePolicyResponse>>> getActive(Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(policyService.getActive(pageable).map(LeavePolicyResponse::new)));
    }

    @GetMapping("/admin")
    @Operation(summary = "Get all leave policy versions")
    public ResponseEntity<ApiResponse<Page<LeavePolicyResponse>>> getAll(Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(policyService.getAll(pageable).map(LeavePolicyResponse::new)));
    }

    @PostMapping
    @Operation(summary = "Create a draft leave policy version")
    public ResponseEntity<ApiResponse<LeavePolicyResponse>> create(
            @Valid @RequestBody LeavePolicyRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        var actor = employeeService.getByEmail(userDetails.getUsername());
        return ResponseEntity.status(201).body(ApiResponse.ok(
                new LeavePolicyResponse(policyService.create(request, actor))));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a draft leave policy version")
    public ResponseEntity<ApiResponse<LeavePolicyResponse>> update(
            @PathVariable String id, @Valid @RequestBody LeavePolicyRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        var actor = employeeService.getByEmail(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.ok(
                new LeavePolicyResponse(policyService.update(id, request, actor))));
    }

    @PatchMapping("/{id}/activate")
    @Operation(summary = "Activate a leave policy version")
    public ResponseEntity<ApiResponse<LeavePolicyResponse>> activate(
            @PathVariable String id, @AuthenticationPrincipal UserDetails userDetails) {
        var actor = employeeService.getByEmail(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.ok(
                new LeavePolicyResponse(policyService.activate(id, actor))));
    }

    @PatchMapping("/{id}/retire")
    @Operation(summary = "Retire a leave policy version")
    public ResponseEntity<ApiResponse<LeavePolicyResponse>> retire(
            @PathVariable String id, @AuthenticationPrincipal UserDetails userDetails) {
        var actor = employeeService.getByEmail(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.ok(
                new LeavePolicyResponse(policyService.retire(id, actor))));
    }
}
