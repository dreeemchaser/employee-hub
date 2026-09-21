package employeehub.controller;

import employeehub.domain.enums.EmploymentStatus;
import employeehub.dto.ApiResponse;
import employeehub.dto.EmployeeRequest;
import employeehub.dto.EmployeeResponse;
import employeehub.dto.OffboardEmployeeRequest;
import employeehub.service.EmployeeService;
import employeehub.service.PhotoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/employees")
@RequiredArgsConstructor
@Tag(name = "Employees")
public class EmployeeController {

    private final EmployeeService employeeService;
    private final PhotoService photoService;

    @GetMapping
    @Operation(summary = "Get all employees (paginated, filterable)")
    public ResponseEntity<ApiResponse<Page<EmployeeResponse>>> getAll(
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Long teamId,
            @RequestParam(required = false) EmploymentStatus status,
            Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(employeeService.getAll(departmentId, teamId, status, pageable)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get employee by ID")
    public ResponseEntity<ApiResponse<EmployeeResponse>> getById(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.ok(new EmployeeResponse(employeeService.getById(id))));
    }

    @PostMapping
    @Operation(summary = "Create a new employee")
    public ResponseEntity<ApiResponse<EmployeeResponse>> create(@RequestBody EmployeeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(new EmployeeResponse(employeeService.create(request))));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an employee")
    public ResponseEntity<ApiResponse<EmployeeResponse>> update(@PathVariable String id, @RequestBody EmployeeRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(new EmployeeResponse(employeeService.update(id, request))));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete an employee")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable String id) {
        employeeService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok("Employee deleted", null));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Update employee employment status")
    public ResponseEntity<ApiResponse<EmployeeResponse>> updateStatus(
            @PathVariable String id,
            @RequestBody Map<String, String> body) {
        EmploymentStatus status = EmploymentStatus.valueOf(body.get("status"));
        return ResponseEntity.ok(ApiResponse.ok(new EmployeeResponse(employeeService.updateStatus(id, status))));
    }

    @PostMapping("/{id}/offboard")
    @Operation(summary = "Offboard an employee: terminate, cancel pending leave, deactivate benefits")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Employee is already terminated")
    })
    public ResponseEntity<ApiResponse<EmployeeResponse>> offboard(
            @PathVariable String id,
            @Valid @RequestBody OffboardEmployeeRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        var actor = employeeService.getByEmail(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.ok(new EmployeeResponse(employeeService.offboard(id, request, actor))));
    }

    @PostMapping("/{id}/photo")
    @Operation(summary = "Upload employee profile photo")
    public ResponseEntity<ApiResponse<EmployeeResponse>> uploadPhoto(
            @PathVariable String id,
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(ApiResponse.ok(new EmployeeResponse(employeeService.updatePhoto(id, photoService.save(file)))));
    }

    @GetMapping("/photo/{filename}")
    @Operation(summary = "Retrieve employee profile photo")
    public ResponseEntity<byte[]> getPhoto(@PathVariable String filename) {
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .body(photoService.load(filename));
    }
}
