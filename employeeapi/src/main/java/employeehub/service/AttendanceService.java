package employeehub.service;

import employeehub.domain.AttendanceRecord;
import employeehub.domain.Employee;
import employeehub.domain.enums.AttendanceStatus;
import employeehub.domain.enums.Role;
import employeehub.dto.ClockOutRequest;
import employeehub.exception.BusinessRuleException;
import employeehub.exception.ResourceNotFoundException;
import employeehub.repository.AttendanceRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AttendanceService {

    private final AttendanceRecordRepository attendanceRecordRepository;

    @Transactional
    public AttendanceRecord clockIn(Employee employee) {
        attendanceRecordRepository.findByEmployeeIdAndStatus(employee.getId(), AttendanceStatus.OPEN)
                .ifPresent(open -> {
                    throw new BusinessRuleException("Already clocked in since " + open.getClockInAt());
                });

        AttendanceRecord record = new AttendanceRecord();
        record.setEmployee(employee);
        record.setWorkDate(LocalDate.now());
        record.setClockInAt(LocalDateTime.now());
        record.setStatus(AttendanceStatus.OPEN);
        return attendanceRecordRepository.save(record);
    }

    @Transactional
    public AttendanceRecord clockOut(Employee employee, ClockOutRequest req) {
        AttendanceRecord record = attendanceRecordRepository
                .findByEmployeeIdAndStatus(employee.getId(), AttendanceStatus.OPEN)
                .orElseThrow(() -> new BusinessRuleException("No open clock-in to close"));

        record.setClockOutAt(LocalDateTime.now());
        record.setStatus(AttendanceStatus.CLOSED);
        if (req != null) {
            record.setNotes(req.getNotes());
        }
        return attendanceRecordRepository.save(record);
    }

    public Page<AttendanceRecord> getMy(String employeeId, Pageable pageable) {
        return attendanceRecordRepository.findByEmployeeId(employeeId, pageable);
    }

    /**
     * HR/SUPER_ADMIN see every employee's records (optionally narrowed by {@code employeeId}).
     * A MANAGER is scoped to their own direct reports regardless of the requested employeeId,
     * mirroring {@code TimesheetService.getAll}.
     */
    public Page<AttendanceRecord> getAll(Employee requester, String employeeId,
                                          LocalDate from, LocalDate to, Pageable pageable) {
        String managerId = requester.getRole() == Role.MANAGER ? requester.getId() : null;
        return attendanceRecordRepository.findAllFiltered(managerId, employeeId, from, to, pageable);
    }

    public AttendanceRecord getOpenForEmployee(String employeeId) {
        return attendanceRecordRepository.findByEmployeeIdAndStatus(employeeId, AttendanceStatus.OPEN)
                .orElseThrow(() -> new ResourceNotFoundException("No open clock-in for employee: " + employeeId));
    }
}
