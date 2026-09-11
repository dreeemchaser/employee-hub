package employeehub.service;

import employeehub.domain.Employee;
import employeehub.domain.Timesheet;
import employeehub.domain.TimesheetEntry;
import employeehub.domain.enums.Role;
import employeehub.domain.enums.NotificationType;
import employeehub.domain.enums.TimesheetStatus;
import employeehub.dto.TimesheetEntryRequest;
import employeehub.dto.TimesheetRequest;
import employeehub.exception.ResourceNotFoundException;
import employeehub.repository.EmployeeRepository;
import employeehub.repository.TimesheetRepository;
import employeehub.repository.TimesheetEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TimesheetService {

    private final TimesheetRepository timesheetRepository;
    private final EmployeeRepository employeeRepository;
    private final TimesheetEntryRepository timesheetEntryRepository;
    private final NotificationService notificationService;

    public Timesheet create(String employeeId, TimesheetRequest req) {
        Employee employee = findEmployee(employeeId);
        Timesheet timesheet = new Timesheet();
        timesheet.setEmployee(employee);
        timesheet.setWeekStartDate(req.getWeekStartDate());
        timesheet.setWeekEndDate(req.getWeekEndDate());
        return timesheetRepository.save(timesheet);
    }

    public List<Timesheet> getMy(String employeeId) {
        return timesheetRepository.findByEmployeeId(employeeId);
    }

    public List<Timesheet> getAll(Employee requester) {
        String managerId = requester.getRole() == Role.MANAGER ? requester.getId() : null;
        return timesheetRepository.findAllFiltered(managerId);
    }

    @Transactional
    public Timesheet addEntry(String timesheetId, String employeeId, TimesheetEntryRequest req) {
        Timesheet timesheet = findTimesheet(timesheetId);
        validateOwner(timesheet, employeeId);
        validateDraft(timesheet);

        TimesheetEntry entry = new TimesheetEntry();
        entry.setTimesheet(timesheet);
        entry.setDate(req.getDate());
        entry.setHoursWorked(req.getHoursWorked());
        entry.setDescription(req.getDescription());
        entry.setProjectOrTask(req.getProjectOrTask());
        timesheet.getEntries().add(entry);

        timesheet.setTotalHours(
                timesheet.getEntries().stream()
                        .map(TimesheetEntry::getHoursWorked)
                        .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add)
        );

        return timesheetRepository.save(timesheet);
    }

    public Timesheet submit(String timesheetId, String employeeId) {
        Timesheet timesheet = findTimesheet(timesheetId);
        validateOwner(timesheet, employeeId);
        validateDraft(timesheet);
        timesheet.setStatus(TimesheetStatus.SUBMITTED);
        Timesheet saved = timesheetRepository.save(timesheet);

        if (timesheet.getEmployee().getManager() != null) {
            notificationService.send(timesheet.getEmployee().getManager(),
                    "Timesheet Submitted",
                    timesheet.getEmployee().getFirstName() + " submitted a timesheet for review",
                    NotificationType.TIMESHEET, "Timesheet", saved.getId());
        }
        return saved;
    }

    @Transactional
    public Timesheet approve(String timesheetId, String approverId) {
        Timesheet timesheet = findTimesheet(timesheetId);
        validateApprover(timesheet, approverId);
        timesheet.setStatus(TimesheetStatus.APPROVED);
        timesheet.setApprovedBy(findEmployee(approverId));
        timesheet.setApprovedAt(LocalDateTime.now());
        return timesheetRepository.save(timesheet);
    }

    @Transactional
    public Timesheet reject(String timesheetId, String approverId, String reason) {
        Timesheet timesheet = findTimesheet(timesheetId);
        validateApprover(timesheet, approverId);
        timesheet.setStatus(TimesheetStatus.REJECTED);
        timesheet.setApprovedBy(findEmployee(approverId));
        timesheet.setApprovedAt(LocalDateTime.now());
        timesheet.setRejectionReason(reason);
        return timesheetRepository.save(timesheet);
    }

    // ── Helpers ──────────────────────────────────────────────────────

    @Transactional
    public Timesheet deleteEntry(String timesheetId, Long entryId, String employeeId) {
        Timesheet timesheet = findTimesheet(timesheetId);
        validateOwner(timesheet, employeeId);
        validateDraft(timesheet);

        TimesheetEntry entry = timesheetEntryRepository.findById(entryId)
                .orElseThrow(() -> new ResourceNotFoundException("Entry not found: " + entryId));
        if (!entry.getTimesheet().getId().equals(timesheetId)) {
            throw new IllegalArgumentException("Entry does not belong to this timesheet");
        }
        timesheet.getEntries().remove(entry);
        timesheetEntryRepository.delete(entry);

        timesheet.setTotalHours(
                timesheet.getEntries().stream()
                        .map(TimesheetEntry::getHoursWorked)
                        .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add)
        );
        return timesheetRepository.save(timesheet);
    }

    private void validateOwner(Timesheet timesheet, String employeeId) {
        if (!timesheet.getEmployee().getId().equals(employeeId)) {
            throw new IllegalArgumentException("You do not own this timesheet");
        }
    }

    private void validateDraft(Timesheet timesheet) {
        if (timesheet.getStatus() != TimesheetStatus.DRAFT) {
            throw new IllegalArgumentException("Timesheet is not in DRAFT status");
        }
    }

    private void validateApprover(Timesheet timesheet, String approverId) {
        if (timesheet.getStatus() != TimesheetStatus.SUBMITTED) {
            throw new IllegalArgumentException("Timesheet is not in SUBMITTED status");
        }
        Employee approver = findEmployee(approverId);
        Role role = approver.getRole();
        if (role != Role.MANAGER && role != Role.HR_ADMIN && role != Role.SUPER_ADMIN) {
            throw new IllegalArgumentException("You are not authorised to approve timesheets");
        }
        if (role == Role.MANAGER && !approver.getId().equals(
                timesheet.getEmployee().getManager() != null
                        ? timesheet.getEmployee().getManager().getId() : null)) {
            throw new IllegalArgumentException("You can only approve timesheets for your direct reports");
        }
    }

    private Timesheet findTimesheet(String id) {
        return timesheetRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Timesheet not found: " + id));
    }

    private Employee findEmployee(String id) {
        return employeeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found: " + id));
    }
}
