package employeehub.service;

import employeehub.domain.*;
import employeehub.domain.enums.LeaveStatus;
import employeehub.domain.enums.NotificationType;
import employeehub.domain.enums.Role;
import employeehub.dto.LeaveRequestDto;
import employeehub.exception.ResourceNotFoundException;
import employeehub.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LeaveService {

    private final LeaveRequestRepository leaveRequestRepository;
    private final LeaveBalanceRepository leaveBalanceRepository;
    private final LeaveTypeRepository leaveTypeRepository;
    private final EmployeeRepository employeeRepository;
    private final NotificationService notificationService;
    private final AuditService auditService;

    // ── Leave Requests ──────────────────────────────────────────────

    public LeaveRequest submit(String employeeId, LeaveRequestDto dto) {
        Employee employee = findEmployee(employeeId);
        LeaveType leaveType = leaveTypeRepository.findById(dto.getLeaveTypeId())
                .orElseThrow(() -> new ResourceNotFoundException("Leave type not found: " + dto.getLeaveTypeId()));

        BigDecimal days = calculateDays(dto.getStartDate(), dto.getEndDate());

        LeaveBalance balance = leaveBalanceRepository
                .findByEmployeeIdAndLeaveTypeId(employeeId, dto.getLeaveTypeId())
                .orElseThrow(() -> new ResourceNotFoundException("No leave balance found for this leave type"));

        if (balance.getRemainingDays().compareTo(days) < 0) {
            throw new IllegalArgumentException("Insufficient leave balance");
        }

        LeaveRequest request = new LeaveRequest();
        request.setEmployee(employee);
        request.setLeaveType(leaveType);
        request.setStartDate(dto.getStartDate());
        request.setEndDate(dto.getEndDate());
        request.setTotalDays(days);
        request.setReason(dto.getReason());
        LeaveRequest saved = leaveRequestRepository.save(request);

        if (employee.getManager() != null) {
            notificationService.send(employee.getManager(),
                    "Leave Request Submitted",
                    employee.getFirstName() + " " + employee.getLastName() + " submitted a leave request",
                    NotificationType.LEAVE, "LeaveRequest", saved.getId());
        }
        return saved;
    }

    public List<LeaveRequest> getMyRequests(String employeeId) {
        return leaveRequestRepository.findByEmployeeId(employeeId);
    }

    public List<LeaveRequest> getAllRequests(Employee requester) {
        String managerId = requester.getRole() == Role.MANAGER ? requester.getId() : null;
        return leaveRequestRepository.findAllFiltered(managerId, null);
    }

    @Transactional
    public LeaveRequest approve(String requestId, String approverId) {
        LeaveRequest request = findRequest(requestId);
        Employee approver = findEmployee(approverId);
        validateApprover(request, approver);

        request.setStatus(LeaveStatus.APPROVED);
        request.setApprovedBy(approver);
        request.setApprovedAt(LocalDateTime.now());
        deductBalance(request);
        LeaveRequest saved = leaveRequestRepository.save(request);

        notificationService.send(request.getEmployee(),
                "Leave Request Approved",
                "Your leave request has been approved",
                NotificationType.LEAVE, "LeaveRequest", saved.getId());
        auditService.log(approver, "APPROVE", "LeaveRequest", saved.getId(), "PENDING", "APPROVED");
        return saved;
    }

    @Transactional
    public LeaveRequest reject(String requestId, String approverId, String reason) {
        LeaveRequest request = findRequest(requestId);
        Employee approver = findEmployee(approverId);
        validateApprover(request, approver);

        request.setStatus(LeaveStatus.REJECTED);
        request.setApprovedBy(approver);
        request.setApprovedAt(LocalDateTime.now());
        request.setRejectionReason(reason);
        LeaveRequest saved = leaveRequestRepository.save(request);

        notificationService.send(request.getEmployee(),
                "Leave Request Rejected",
                "Your leave request has been rejected: " + reason,
                NotificationType.LEAVE, "LeaveRequest", saved.getId());
        auditService.log(approver, "REJECT", "LeaveRequest", saved.getId(), "PENDING", "REJECTED");
        return saved;
    }

    public void cancel(String requestId, String employeeId) {
        LeaveRequest request = findRequest(requestId);
        if (!request.getEmployee().getId().equals(employeeId)) {
            throw new IllegalArgumentException("You can only cancel your own requests");
        }
        if (request.getStatus() != LeaveStatus.PENDING) {
            throw new IllegalArgumentException("Only PENDING requests can be cancelled");
        }
        request.setStatus(LeaveStatus.CANCELLED);
        leaveRequestRepository.save(request);
    }

    // ── Leave Balances ───────────────────────────────────────────────

    public List<LeaveBalance> getMyBalances(String employeeId) {
        return leaveBalanceRepository.findByEmployeeId(employeeId);
    }

    // ── Leave Calendar ───────────────────────────────────────────────

    public List<LeaveRequest> getCalendar(int year, int month) {
        LocalDate firstDay = LocalDate.of(year, month, 1);
        LocalDate lastDay  = firstDay.withDayOfMonth(firstDay.lengthOfMonth());
        return leaveRequestRepository.findApprovedInMonth(firstDay, lastDay);
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private void validateApprover(LeaveRequest request, Employee approver) {
        if (request.getStatus() != LeaveStatus.PENDING) {
            throw new IllegalArgumentException("Request is no longer pending");
        }
        Role role = approver.getRole();
        if (role != Role.MANAGER && role != Role.HR_ADMIN && role != Role.SUPER_ADMIN) {
            throw new IllegalArgumentException("You are not authorised to approve leave requests");
        }
        // MANAGER can only approve their own team's requests
        if (role == Role.MANAGER && !approver.getId().equals(request.getEmployee().getManager() != null
                ? request.getEmployee().getManager().getId() : null)) {
            throw new IllegalArgumentException("You can only approve requests for your direct reports");
        }
    }

    private void deductBalance(LeaveRequest request) {
        leaveBalanceRepository
                .findByEmployeeIdAndLeaveTypeId(
                        request.getEmployee().getId(),
                        request.getLeaveType().getId())
                .ifPresent(balance -> {
                    balance.setUsedDays(balance.getUsedDays().add(request.getTotalDays()));
                    balance.setRemainingDays(balance.getRemainingDays().subtract(request.getTotalDays()));
                    leaveBalanceRepository.save(balance);
                });
    }

    private BigDecimal calculateDays(LocalDate start, LocalDate end) {
        long days = start.datesUntil(end.plusDays(1))
                .filter(d -> d.getDayOfWeek().getValue() < 6)
                .count();
        return BigDecimal.valueOf(days);
    }

    private Employee findEmployee(String id) {
        return employeeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found: " + id));
    }

    private LeaveRequest findRequest(String id) {
        return leaveRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Leave request not found: " + id));
    }
}
