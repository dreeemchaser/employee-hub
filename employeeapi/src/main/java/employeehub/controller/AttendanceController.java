package employeehub.controller;

import employeehub.dto.ApiResponse;
import employeehub.dto.AttendanceResponse;
import employeehub.dto.ClockOutRequest;
import employeehub.service.AttendanceService;
import employeehub.service.EmployeeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/attendance")
@RequiredArgsConstructor
@Tag(name = "Attendance")
public class AttendanceController {

    private final AttendanceService attendanceService;
    private final EmployeeService employeeService;

    @PostMapping("/clock-in")
    @Operation(summary = "Clock in for today")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Already clocked in")
    })
    public ResponseEntity<ApiResponse<AttendanceResponse>> clockIn(@AuthenticationPrincipal UserDetails userDetails) {
        var employee = employeeService.getByEmail(userDetails.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(new AttendanceResponse(attendanceService.clockIn(employee))));
    }

    @PatchMapping("/clock-out")
    @Operation(summary = "Clock out of the current open session")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "No open clock-in to close")
    })
    public ResponseEntity<ApiResponse<AttendanceResponse>> clockOut(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody(required = false) ClockOutRequest request) {
        var employee = employeeService.getByEmail(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.ok(new AttendanceResponse(attendanceService.clockOut(employee, request))));
    }

    @GetMapping("/my")
    @Operation(summary = "Get current employee's attendance history")
    public ResponseEntity<ApiResponse<Page<AttendanceResponse>>> getMy(
            @AuthenticationPrincipal UserDetails userDetails, Pageable pageable) {
        var employee = employeeService.getByEmail(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.ok(attendanceService.getMy(employee.getId(), pageable).map(AttendanceResponse::new)));
    }

    @GetMapping
    @Operation(summary = "Get attendance records (Manager scoped to direct reports, HR/Admin see all)")
    public ResponseEntity<ApiResponse<Page<AttendanceResponse>>> getAll(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) String employeeId,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            Pageable pageable) {
        var requester = employeeService.getByEmail(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.ok(
                attendanceService.getAll(requester, employeeId, from, to, pageable).map(AttendanceResponse::new)));
    }
}
