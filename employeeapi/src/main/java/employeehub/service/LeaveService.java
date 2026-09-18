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

    // Annual leave requires at least this many days advance notice
    private static final int ANNUAL_LEAVE_NOTICE_DAYS = 14;

    // Sick leave beyond this threshold flags a documentation requirement
    private static final int SICK_LEAVE_DOC_THRESHOLD = 3;

    private final LeaveRequestRepository leaveRequestRepository;
    private final LeaveBalanceRepository leaveBalanceRepository;
    private final LeaveTypeRepository leaveTypeRepository;
    private final EmployeeRepository employeeRepository;
    private final NotificationService notificationService;
    private final AuditService auditService;

    // ── Leave Requests ──────────────────────────────────────────────

    @Transactional
    public LeaveRequest submit(String employeeId, LeaveRequestDto dto) {
        Employee employee = findEmployee(employeeId);
        LeaveType leaveType = leaveTypeRepository.findById(dto.getLeaveTypeId())
                .orElseThrow(() -> new ResourceNotFoundException("Leave type not found: " + dto.getLeaveTypeId()));

        LocalDate start = dto.getStartDate();
        LocalDate end   = dto.getEndDate();

        // ── Date order ───────────────────────────────────────────────
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("End date cannot be before start date");
        }

        // ── Weekday count (excludes weekends) ────────────────────────
        BigDecimal days = calculateDays(start, end);
        if (days.compareTo(BigDecimal.ZERO) == 0) {
            throw new IllegalArgumentException(
                "The selected date range contains no working days. Please choose weekdays.");
        }

        // ── Annual leave: 14-day advance notice ──────────────────────
        if (leaveType.getName().equalsIgnoreCase("Annual Leave")) {
            long daysUntilStart = LocalDate.now().until(start).getDays() +
                    (long) LocalDate.now().until(start).getMonths() * 30 +
                    (long) LocalDate.now().until(start).getYears() * 365;
            if (daysUntilStart < ANNUAL_LEAVE_NOTICE_DAYS) {
                throw new IllegalArgumentException(
                    "Annual leave requires at least " + ANNUAL_LEAVE_NOTICE_DAYS +
                    " days advance notice. Please plan ahead or speak to your manager.");
            }
        }

        // ── Sick leave > 3 days requires documentation ───────────────
        // The employee may proceed once they confirm (via the form checkbox)
        // that they have emailed their manager the supporting documentation.
        boolean requiresDocConfirmation = leaveType.getName().equalsIgnoreCase("Sick Leave")
                && days.compareTo(BigDecimal.valueOf(SICK_LEAVE_DOC_THRESHOLD)) > 0;
        if (requiresDocConfirmation && !dto.isDocumentationConfirmed()) {
            throw new IllegalArgumentException(
                "REQUIRES_DOCUMENTATION: Sick leave exceeding " + SICK_LEAVE_DOC_THRESHOLD +
                " days requires a doctor's note. Please email your manager with supporting documentation before submitting.");
        }

        // ── requiresDocumentation flag on other leave types ──────────
        if (Boolean.TRUE.equals(leaveType.getRequiresDocumentation())
                && !leaveType.getName().equalsIgnoreCase("Sick Leave")) {
            // For other doc-required types (e.g. Maternity, Study), pass through but flag
            // The frontend already warns the user; we allow submission
        }

        // ── Overlap check ────────────────────────────────────────────
        List<LeaveRequest> overlapping = leaveRequestRepository.findOverlapping(employeeId, start, end);
        if (!overlapping.isEmpty()) {
            throw new IllegalArgumentException(
                "You already have a pending or approved leave request that overlaps with these dates.");
        }

        // ── Balance check ────────────────────────────────────────────
        LeaveBalance balance = leaveBalanceRepository
                .findByEmployeeIdAndLeaveTypeId(employeeId, dto.getLeaveTypeId())
                .orElseThrow(() -> new ResourceNotFoundException(
                    "No leave balance found for this leave type. Please contact HR."));

        if (balance.getRemainingDays().compareTo(days) < 0) {
            throw new IllegalArgumentException(
                "Insufficient leave balance. You have " + balance.getRemainingDays() +
                " day(s) remaining but requested " + days + " day(s).");
        }

        // ── Persist ──────────────────────────────────────────────────
        LeaveRequest request = new LeaveRequest();
        request.setEmployee(employee);
        request.setLeaveType(leaveType);
        request.setStartDate(start);
        request.setEndDate(end);
        request.setTotalDays(days);
        request.setReason(dto.getReason());
        LeaveRequest saved = leaveRequestRepository.save(request);

        // Record the employee's attestation that they emailed their manager the
        // required documentation, so the confirmation is auditable after the fact.
        if (requiresDocConfirmation) {
            auditService.log(employee, "SUBMIT_WITH_DOC_CONFIRMATION", "LeaveRequest",
                    saved.getId(), null, "Employee confirmed documentation emailed to manager");
        }

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
            throw new IllegalArgumentException("Only pending requests can be cancelled");
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

    /**
     * Counts working days (Mon–Fri) between start and end dates inclusive.
     */
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
