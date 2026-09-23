package employeehub.controller;

import employeehub.dto.ApiResponse;
import employeehub.dto.LeaveBalanceResponse;
import employeehub.dto.LeaveForecastResponse;
import employeehub.dto.LeaveRequestDto;
import employeehub.dto.LeaveRequestResponse;
import employeehub.dto.LeaveTypeSummary;
import employeehub.service.EmployeeService;
import employeehub.service.LeaveService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/leave")
@RequiredArgsConstructor
@Tag(name = "Leave Management")
public class LeaveController {

    private final LeaveService leaveService;
    private final EmployeeService employeeService;

    @GetMapping("/requests")
    @Operation(summary = "Get all leave requests (HR/Manager filtered)")
    public ResponseEntity<ApiResponse<List<LeaveRequestResponse>>> getAll(@AuthenticationPrincipal UserDetails userDetails) {
        var requester = employeeService.getByEmail(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.ok(LeaveRequestResponse.from(leaveService.getAllRequests(requester))));
    }

    @GetMapping("/requests/my")
    @Operation(summary = "Get current employee's leave requests")
    public ResponseEntity<ApiResponse<List<LeaveRequestResponse>>> getMy(@AuthenticationPrincipal UserDetails userDetails) {
        var employee = employeeService.getByEmail(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.ok(LeaveRequestResponse.from(leaveService.getMyRequests(employee.getId()))));
    }

    @PostMapping("/requests")
    @Operation(summary = "Submit a leave request")
    public ResponseEntity<ApiResponse<LeaveRequestResponse>> submit(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody LeaveRequestDto dto) {
        var employee = employeeService.getByEmail(userDetails.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(new LeaveRequestResponse(leaveService.submit(employee.getId(), dto))));
    }

    @PatchMapping("/requests/{id}/approve")
    @Operation(summary = "Approve a leave request")
    public ResponseEntity<ApiResponse<LeaveRequestResponse>> approve(
            @PathVariable String id,
            @AuthenticationPrincipal UserDetails userDetails) {
        var approver = employeeService.getByEmail(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.ok(new LeaveRequestResponse(leaveService.approve(id, approver.getId()))));
    }

    @PatchMapping("/requests/{id}/reject")
    @Operation(summary = "Reject a leave request")
    public ResponseEntity<ApiResponse<LeaveRequestResponse>> reject(
            @PathVariable String id,
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody Map<String, String> body) {
        var approver = employeeService.getByEmail(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.ok(new LeaveRequestResponse(leaveService.reject(id, approver.getId(), body.get("reason")))));
    }

    @DeleteMapping("/requests/{id}")
    @Operation(summary = "Cancel a leave request (employee only, must be PENDING)")
    public ResponseEntity<ApiResponse<Void>> cancel(
            @PathVariable String id,
            @AuthenticationPrincipal UserDetails userDetails) {
        var employee = employeeService.getByEmail(userDetails.getUsername());
        leaveService.cancel(id, employee.getId());
        return ResponseEntity.ok(ApiResponse.ok("Leave request cancelled", null));
    }

    @GetMapping("/balances/my")
    @Operation(summary = "Get current employee's leave balances")
    public ResponseEntity<ApiResponse<List<LeaveBalanceResponse>>> getMyBalances(@AuthenticationPrincipal UserDetails userDetails) {
        var employee = employeeService.getByEmail(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.ok(LeaveBalanceResponse.from(leaveService.getMyBalances(employee.getId()))));
    }

    @GetMapping("/balances/forecast")
    @Operation(summary = "Project remaining leave at cycle end, including pending requests")
    public ResponseEntity<ApiResponse<List<LeaveForecastResponse>>> getForecast(
            @AuthenticationPrincipal UserDetails userDetails) {
        var employee = employeeService.getByEmail(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.ok(leaveService.getForecast(employee.getId())));
    }

    @GetMapping("/types")
    @Operation(summary = "List leave types for filters and apply forms")
    public ResponseEntity<ApiResponse<List<LeaveTypeSummary>>> getLeaveTypes() {
        return ResponseEntity.ok(ApiResponse.ok(LeaveTypeSummary.from(leaveService.getLeaveTypes())));
    }

    @GetMapping("/calendar")
    @Operation(summary = "Get approved leaves for a given month (team calendar)")
    public ResponseEntity<ApiResponse<List<LeaveRequestResponse>>> getCalendar(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam int year,
            @RequestParam int month,
            @RequestParam(required = false) Long leaveTypeId,
            @RequestParam(required = false) String employeeId,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Long teamId) {
        var requester = employeeService.getByEmail(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.ok(LeaveRequestResponse.from(
                leaveService.getCalendar(requester, year, month, leaveTypeId, employeeId, departmentId, teamId))));
    }

    @GetMapping("/conflicts")
    @Operation(summary = "Approved team leave overlapping a date range (busy-period preview)")
    public ResponseEntity<ApiResponse<List<LeaveRequestResponse>>> getConflicts(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam LocalDate startDate,
            @RequestParam LocalDate endDate) {
        var requester = employeeService.getByEmail(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.ok(LeaveRequestResponse.from(
                leaveService.getConflicts(requester, startDate, endDate))));
    }
}
