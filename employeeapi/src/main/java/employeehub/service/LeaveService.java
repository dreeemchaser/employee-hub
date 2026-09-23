package employeehub.service;

import employeehub.domain.*;
import employeehub.domain.enums.LeaveStatus;
import employeehub.domain.enums.NotificationType;
import employeehub.domain.enums.Role;
import employeehub.dto.LeaveForecastResponse;
import employeehub.dto.LeaveRequestDto;
import employeehub.exception.BusinessRuleException;
import employeehub.exception.ResourceNotFoundException;
import employeehub.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LeaveService {

    // Annual leave requires at least this many days advance notice
    private static final int ANNUAL_LEAVE_NOTICE_DAYS = 14;

    // Sick leave beyond this threshold flags a documentation requirement
    private static final int SICK_LEAVE_DOC_THRESHOLD = 3;

    // Short annual leave with no team clash is approved without a manager step
    private static final int AUTO_APPROVE_MAX_WORKING_DAYS = 2;
    private static final String AUTO_APPROVE_LEAVE_TYPE = "Annual Leave";

    private final LeaveRequestRepository leaveRequestRepository;
    private final LeaveBalanceRepository leaveBalanceRepository;
    private final LeaveTypeRepository leaveTypeRepository;
    private final EmployeeRepository employeeRepository;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final Clock clock;

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
            long daysUntilStart = ChronoUnit.DAYS.between(LocalDate.now(clock), start);
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
            throw new BusinessRuleException(
                "You already have a pending or approved leave request that overlaps with these dates.");
        }

        // ── Balance check ────────────────────────────────────────────
        LeaveBalance balance = leaveBalanceRepository
                .findByEmployeeIdAndLeaveTypeId(employeeId, dto.getLeaveTypeId())
                .orElseThrow(() -> new ResourceNotFoundException(
                    "No leave balance found for this leave type. Please contact HR."));

        if (balance.getRemainingDays().compareTo(days) < 0) {
            throw new BusinessRuleException(
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

        if (isEligibleForAutoApproval(employee, leaveType, days, start, end)) {
            return autoApprove(saved, employee);
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
        request.setApprovedAt(LocalDateTime.now(clock));
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
        request.setApprovedAt(LocalDateTime.now(clock));
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
            throw new BusinessRuleException("Only pending requests can be cancelled");
        }
        request.setStatus(LeaveStatus.CANCELLED);
        leaveRequestRepository.save(request);
    }

    // ── Leave Balances ───────────────────────────────────────────────

    public List<LeaveBalance> getMyBalances(String employeeId) {
        return leaveBalanceRepository.findByEmployeeId(employeeId);
    }

    // ── Leave Calendar ───────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<LeaveRequest> getCalendar(Employee requester, int year, int month,
                                          Long leaveTypeId, String employeeId,
                                          Long departmentId, Long teamId) {
        if (year < 2000 || year > 2100 || month < 1 || month > 12) {
            throw new IllegalArgumentException("Calendar year must be between 2000 and 2100 and month must be between 1 and 12");
        }
        if (requester == null || requester.getRole() == null) {
            throw new IllegalArgumentException("Authenticated employee is required");
        }
        boolean admin = requester.getRole() == Role.HR_ADMIN || requester.getRole() == Role.SUPER_ADMIN;
        boolean manager = requester.getRole() == Role.MANAGER;
        if (!admin && (departmentId != null || teamId != null)) {
            throw new IllegalArgumentException("Department and team filters are restricted to HR administrators");
        }
        if (requester.getRole() == Role.EMPLOYEE && employeeId != null
                && !requester.getId().equals(employeeId)) {
            throw new IllegalArgumentException("Employees may only view their own leave");
        }
        LocalDate firstDay;
        try {
            firstDay = LocalDate.of(year, month, 1);
        } catch (DateTimeException ex) {
            throw new IllegalArgumentException("Invalid calendar date", ex);
        }
        LocalDate lastDay  = firstDay.withDayOfMonth(firstDay.lengthOfMonth());
        if (admin) {
            return leaveRequestRepository.findApprovedForAdminInMonth(
                    firstDay, lastDay, employeeId, leaveTypeId, departmentId, teamId);
        }
        if (manager) {
            return leaveRequestRepository.findApprovedForManagerInMonth(
                    firstDay, lastDay, requester.getId(), employeeId, leaveTypeId);
        }
        return leaveRequestRepository.findApprovedForEmployeeInMonth(
                firstDay, lastDay, requester.getId(), leaveTypeId);
    }

    @Transactional(readOnly = true)
    public List<LeaveRequest> getConflicts(Employee requester, LocalDate start, LocalDate end) {
        if (requester == null) {
            throw new IllegalArgumentException("Authenticated employee is required");
        }
        if (start == null || end == null) {
            throw new IllegalArgumentException("Start date and end date are required");
        }
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("End date cannot be before start date");
        }
        if (requester.getTeam() == null) {
            return List.of();
        }
        return leaveRequestRepository.findApprovedOverlappingForTeam(
                requester.getTeam().getId(), start, end, requester.getId());
    }

    @Transactional(readOnly = true)
    public List<LeaveForecastResponse> getForecast(String employeeId) {
        LocalDate today = LocalDate.now(clock);
        List<LeaveRequest> pending = leaveRequestRepository.findByEmployeeId(employeeId).stream()
                .filter(request -> request.getStatus() == LeaveStatus.PENDING)
                .toList();
        return leaveBalanceRepository.findByEmployeeId(employeeId).stream()
                .map(balance -> {
                    BigDecimal pendingDays = pending.stream()
                            .filter(request -> request.getLeaveType().getId().equals(balance.getLeaveType().getId()))
                            .map(LeaveRequest::getTotalDays)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    return new LeaveForecastResponse(balance, pendingDays, today);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<LeaveType> getLeaveTypes() {
        return leaveTypeRepository.findAll();
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private void validateApprover(LeaveRequest request, Employee approver) {
        if (request.getStatus() != LeaveStatus.PENDING) {
            throw new BusinessRuleException("Request is no longer pending");
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

    /**
     * Annual leave of two working days or fewer is approved immediately when
     * nobody else on the same team already has approved leave on those dates.
     * Anything else (sick/maternity, documentation-required types, or a busy
     * team) stays PENDING for the manager.
     */
    private boolean isEligibleForAutoApproval(Employee employee, LeaveType leaveType,
                                              BigDecimal days, LocalDate start, LocalDate end) {
        if (!AUTO_APPROVE_LEAVE_TYPE.equalsIgnoreCase(leaveType.getName())) {
            return false;
        }
        if (Boolean.TRUE.equals(leaveType.getRequiresDocumentation())) {
            return false;
        }
        if (days.compareTo(BigDecimal.valueOf(AUTO_APPROVE_MAX_WORKING_DAYS)) > 0) {
            return false;
        }
        if (employee.getTeam() == null) {
            return true;
        }
        return leaveRequestRepository.findApprovedOverlappingForTeam(
                employee.getTeam().getId(), start, end, employee.getId()).isEmpty();
    }

    private LeaveRequest autoApprove(LeaveRequest request, Employee employee) {
        request.setStatus(LeaveStatus.APPROVED);
        request.setApprovedBy(employee);
        request.setApprovedAt(LocalDateTime.now(clock));
        deductBalance(request);
        LeaveRequest saved = leaveRequestRepository.save(request);

        notificationService.send(employee,
                "Leave Request Auto-Approved",
                "Your " + request.getLeaveType().getName() + " request was approved automatically "
                        + "(two working days or fewer, no team clash).",
                NotificationType.LEAVE, "LeaveRequest", saved.getId());
        if (employee.getManager() != null) {
            notificationService.send(employee.getManager(),
                    "Leave Auto-Approved",
                    employee.getFirstName() + " " + employee.getLastName()
                            + " had a short annual-leave request approved automatically",
                    NotificationType.LEAVE, "LeaveRequest", saved.getId());
        }
        auditService.log(employee, "AUTO_APPROVE", "LeaveRequest", saved.getId(), "PENDING", "APPROVED");
        return saved;
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
