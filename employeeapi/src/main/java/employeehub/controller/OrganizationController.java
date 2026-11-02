package employeehub.controller;

import employeehub.domain.enums.EmploymentStatus;
import employeehub.dto.ApiResponse;
import employeehub.dto.OrganizationNodeResponse;
import employeehub.service.EmployeeService;
import employeehub.service.OrganizationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/organization")
@RequiredArgsConstructor
@Tag(name = "Organization")
public class OrganizationController {

    private final OrganizationService organizationService;
    private final EmployeeService employeeService;

    @GetMapping("/tree")
    @Operation(summary = "Get the caller's permitted organization roots")
    public ResponseEntity<ApiResponse<Page<OrganizationNodeResponse>>> getTree(
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Long teamId,
            @RequestParam(required = false) EmploymentStatus status,
            Pageable pageable,
            @AuthenticationPrincipal UserDetails userDetails) {
        var caller = employeeService.getByEmail(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.ok(
                organizationService.getTree(caller, departmentId, teamId, status, pageable)));
    }

    @GetMapping("/search")
    @Operation(summary = "Search permitted organization nodes")
    public ResponseEntity<ApiResponse<Page<OrganizationNodeResponse>>> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Long teamId,
            @RequestParam(required = false) EmploymentStatus status,
            Pageable pageable,
            @AuthenticationPrincipal UserDetails userDetails) {
        var caller = employeeService.getByEmail(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.ok(
                organizationService.search(caller, q, departmentId, teamId, status, pageable)));
    }

    @GetMapping("/nodes/{employeeId}")
    @Operation(summary = "Get a permitted organization node and direct reports")
    public ResponseEntity<ApiResponse<OrganizationNodeResponse>> getNode(
            @PathVariable String employeeId,
            @AuthenticationPrincipal UserDetails userDetails) {
        var caller = employeeService.getByEmail(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.ok(organizationService.getNode(caller, employeeId)));
    }
}
