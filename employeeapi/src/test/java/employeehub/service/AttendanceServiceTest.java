package employeehub.service;

import employeehub.domain.AttendanceRecord;
import employeehub.domain.Employee;
import employeehub.domain.enums.AttendanceStatus;
import employeehub.domain.enums.Role;
import employeehub.dto.ClockOutRequest;
import employeehub.exception.BusinessRuleException;
import employeehub.exception.ResourceNotFoundException;
import employeehub.repository.AttendanceRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttendanceServiceTest {

    @Mock AttendanceRecordRepository attendanceRecordRepository;

    @InjectMocks AttendanceService attendanceService;

    private Employee employee;
    private Employee manager;

    @BeforeEach
    void setUp() {
        manager = new Employee();
        manager.setId("mgr-1");
        manager.setRole(Role.MANAGER);

        employee = new Employee();
        employee.setId("emp-1");
        employee.setRole(Role.EMPLOYEE);
        employee.setManager(manager);
    }

    @Test
    void clockIn_shouldCreateOpenRecord_whenNoneOpen() {
        when(attendanceRecordRepository.findByEmployeeIdAndStatus("emp-1", AttendanceStatus.OPEN))
                .thenReturn(Optional.empty());
        when(attendanceRecordRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        AttendanceRecord result = attendanceService.clockIn(employee);

        assertThat(result.getStatus()).isEqualTo(AttendanceStatus.OPEN);
        assertThat(result.getEmployee()).isEqualTo(employee);
        assertThat(result.getWorkDate()).isEqualTo(LocalDate.now());
        assertThat(result.getClockInAt()).isNotNull();
    }

    @Test
    void clockIn_shouldThrow_whenAlreadyClockedIn() {
        AttendanceRecord open = new AttendanceRecord();
        open.setStatus(AttendanceStatus.OPEN);
        when(attendanceRecordRepository.findByEmployeeIdAndStatus("emp-1", AttendanceStatus.OPEN))
                .thenReturn(Optional.of(open));

        assertThatThrownBy(() -> attendanceService.clockIn(employee))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Already clocked in");
    }

    @Test
    void clockOut_shouldCloseOpenRecord_andSaveNotes() {
        AttendanceRecord open = new AttendanceRecord();
        open.setId("att-1");
        open.setEmployee(employee);
        open.setStatus(AttendanceStatus.OPEN);
        when(attendanceRecordRepository.findByEmployeeIdAndStatus("emp-1", AttendanceStatus.OPEN))
                .thenReturn(Optional.of(open));
        when(attendanceRecordRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        ClockOutRequest req = new ClockOutRequest();
        req.setNotes("Left early for appointment");

        AttendanceRecord result = attendanceService.clockOut(employee, req);

        assertThat(result.getStatus()).isEqualTo(AttendanceStatus.CLOSED);
        assertThat(result.getClockOutAt()).isNotNull();
        assertThat(result.getNotes()).isEqualTo("Left early for appointment");
    }

    @Test
    void clockOut_shouldThrow_whenNoOpenRecord() {
        when(attendanceRecordRepository.findByEmployeeIdAndStatus("emp-1", AttendanceStatus.OPEN))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> attendanceService.clockOut(employee, null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("No open clock-in");
    }

    @Test
    void clockOut_shouldTolerateNullRequestBody() {
        AttendanceRecord open = new AttendanceRecord();
        open.setEmployee(employee);
        open.setStatus(AttendanceStatus.OPEN);
        when(attendanceRecordRepository.findByEmployeeIdAndStatus("emp-1", AttendanceStatus.OPEN))
                .thenReturn(Optional.of(open));
        when(attendanceRecordRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        AttendanceRecord result = attendanceService.clockOut(employee, null);

        assertThat(result.getStatus()).isEqualTo(AttendanceStatus.CLOSED);
        assertThat(result.getNotes()).isNull();
    }

    @Test
    void getAll_shouldScopeToManagerId_whenRequesterIsManager() {
        Pageable pageable = PageRequest.of(0, 20);
        when(attendanceRecordRepository.findAllFiltered(eq("mgr-1"), any(), any(), any(), eq(pageable)))
                .thenReturn(org.springframework.data.domain.Page.empty());

        attendanceService.getAll(manager, null, null, null, pageable);

        org.mockito.Mockito.verify(attendanceRecordRepository)
                .findAllFiltered(eq("mgr-1"), any(), any(), any(), eq(pageable));
    }

    @Test
    void getAll_shouldPassNullManagerId_whenRequesterIsHrAdmin() {
        Employee hrAdmin = new Employee();
        hrAdmin.setId("hr-1");
        hrAdmin.setRole(Role.HR_ADMIN);
        Pageable pageable = PageRequest.of(0, 20);
        when(attendanceRecordRepository.findAllFiltered(eq(null), any(), any(), any(), eq(pageable)))
                .thenReturn(org.springframework.data.domain.Page.empty());

        attendanceService.getAll(hrAdmin, null, null, null, pageable);

        org.mockito.Mockito.verify(attendanceRecordRepository)
                .findAllFiltered(eq(null), any(), any(), any(), eq(pageable));
    }

    @Test
    void getOpenForEmployee_shouldThrow_whenNoneOpen() {
        when(attendanceRecordRepository.findByEmployeeIdAndStatus("emp-1", AttendanceStatus.OPEN))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> attendanceService.getOpenForEmployee("emp-1"))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
