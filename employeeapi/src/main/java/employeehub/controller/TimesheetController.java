package employeehub.controller;

import employeehub.dto.ApiResponse;
import employeehub.dto.TimesheetEntryRequest;
import employeehub.dto.TimesheetRequest;
import employeehub.dto.TimesheetResponse;
import employeehub.service.EmployeeService;
import employeehub.service.TimesheetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/timesheets")
@RequiredArgsConstructor
@Tag(name = "Timesheets")
public class TimesheetController {

    private final TimesheetService timesheetService;
    private final EmployeeService employeeService;

    @GetMapping("/my")
    @Operation(summary = "Get current employee's timesheets")
    public ResponseEntity<ApiResponse<List<TimesheetResponse>>> getMy(@AuthenticationPrincipal UserDetails userDetails) {
        var employee = employeeService.getByEmail(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.ok(TimesheetResponse.from(timesheetService.getMy(employee.getId()))));
    }

    @GetMapping
    @Operation(summary = "Get all timesheets (Manager/HR filtered by team)")
    public ResponseEntity<ApiResponse<List<TimesheetResponse>>> getAll(@AuthenticationPrincipal UserDetails userDetails) {
        var requester = employeeService.getByEmail(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.ok(TimesheetResponse.from(timesheetService.getAll(requester))));
    }

    @PostMapping
    @Operation(summary = "Create a timesheet (DRAFT)")
    public ResponseEntity<ApiResponse<TimesheetResponse>> create(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody TimesheetRequest request) {
        var employee = employeeService.getByEmail(userDetails.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(new TimesheetResponse(timesheetService.create(employee.getId(), request))));
    }

    @PostMapping("/{id}/entries")
    @Operation(summary = "Add an entry to a timesheet")
    public ResponseEntity<ApiResponse<TimesheetResponse>> addEntry(
            @PathVariable String id,
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody TimesheetEntryRequest request) {
        var employee = employeeService.getByEmail(userDetails.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(new TimesheetResponse(timesheetService.addEntry(id, employee.getId(), request))));
    }

    @PatchMapping("/{id}/submit")
    @Operation(summary = "Submit a timesheet for approval")
    public ResponseEntity<ApiResponse<TimesheetResponse>> submit(
            @PathVariable String id,
            @AuthenticationPrincipal UserDetails userDetails) {
        var employee = employeeService.getByEmail(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.ok(new TimesheetResponse(timesheetService.submit(id, employee.getId()))));
    }

    @PatchMapping("/{id}/approve")
    @Operation(summary = "Approve a timesheet")
    public ResponseEntity<ApiResponse<TimesheetResponse>> approve(
            @PathVariable String id,
            @AuthenticationPrincipal UserDetails userDetails) {
        var approver = employeeService.getByEmail(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.ok(new TimesheetResponse(timesheetService.approve(id, approver.getId()))));
    }

    @PatchMapping("/{id}/reject")
    @Operation(summary = "Reject a timesheet")
    public ResponseEntity<ApiResponse<TimesheetResponse>> reject(
            @PathVariable String id,
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody Map<String, String> body) {
        var approver = employeeService.getByEmail(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.ok(new TimesheetResponse(timesheetService.reject(id, approver.getId(), body.get("reason")))));
    }

    @DeleteMapping("/{id}/entries/{entryId}")
    @Operation(summary = "Delete an entry from a DRAFT timesheet")
    public ResponseEntity<ApiResponse<TimesheetResponse>> deleteEntry(
            @PathVariable String id,
            @PathVariable Long entryId,
            @AuthenticationPrincipal UserDetails userDetails) {
        var employee = employeeService.getByEmail(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.ok(new TimesheetResponse(timesheetService.deleteEntry(id, entryId, employee.getId()))));
    }
}
