package employeehub.controller;

import employeehub.domain.enums.DocumentStatus;
import employeehub.domain.enums.LeaveStatus;
import employeehub.domain.enums.TimesheetStatus;
import employeehub.dto.ApiResponse;
import employeehub.repository.DocumentRepository;
import employeehub.repository.EmployeeRepository;
import employeehub.repository.LeaveRequestRepository;
import employeehub.repository.TimesheetRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
@Tag(name = "Dashboard")
public class DashboardController {

    private final EmployeeRepository employeeRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final TimesheetRepository timesheetRepository;
    private final DocumentRepository documentRepository;

    @GetMapping("/stats")
    @Operation(summary = "Get dashboard stats (HR/Admin)")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getStats() {
        long totalEmployees      = employeeRepository.count();
        long pendingLeave        = leaveRequestRepository.findAllFiltered(null, LeaveStatus.PENDING).size();
        long pendingTimesheets   = timesheetRepository.findAllFiltered(null)
                .stream().filter(t -> t.getStatus() == TimesheetStatus.SUBMITTED).count();
        long pendingDocuments    = documentRepository.findAll()
                .stream().filter(d -> d.getStatus() == DocumentStatus.PENDING).count();

        Map<String, Object> stats = Map.of(
                "employees",          totalEmployees,
                "pendingLeave",       pendingLeave,
                "pendingTimesheets",  pendingTimesheets,
                "pendingDocuments",   pendingDocuments
        );
        return ResponseEntity.ok(ApiResponse.ok(stats));
    }
}
