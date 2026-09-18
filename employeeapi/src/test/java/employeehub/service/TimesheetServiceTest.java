package employeehub.service;

import employeehub.domain.Employee;
import employeehub.domain.Timesheet;
import employeehub.domain.enums.Role;
import employeehub.domain.enums.TimesheetStatus;
import employeehub.dto.TimesheetRequest;
import employeehub.exception.BusinessRuleException;
import employeehub.exception.ResourceNotFoundException;
import employeehub.repository.EmployeeRepository;
import employeehub.repository.TimesheetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TimesheetServiceTest {

    @Mock TimesheetRepository timesheetRepository;
    @Mock EmployeeRepository employeeRepository;
    @Mock NotificationService notificationService;

    @InjectMocks TimesheetService timesheetService;

    private Employee employee;
    private Employee manager;
    private Timesheet timesheet;

    @BeforeEach
    void setUp() {
        manager = new Employee();
        manager.setId("mgr-1");
        manager.setRole(Role.MANAGER);

        employee = new Employee();
        employee.setId("emp-1");
        employee.setFirstName("Jane");
        employee.setRole(Role.EMPLOYEE);
        employee.setManager(manager);

        timesheet = new Timesheet();
        timesheet.setId("ts-1");
        timesheet.setEmployee(employee);
        timesheet.setStatus(TimesheetStatus.DRAFT);
        timesheet.setWeekStartDate(LocalDate.now().minusDays(7));
        timesheet.setWeekEndDate(LocalDate.now());
    }

    @Test
    void create_shouldReturnDraftTimesheet() {
        when(employeeRepository.findById("emp-1")).thenReturn(Optional.of(employee));
        when(timesheetRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        TimesheetRequest req = new TimesheetRequest();
        req.setWeekStartDate(LocalDate.now().minusDays(7));
        req.setWeekEndDate(LocalDate.now());

        Timesheet result = timesheetService.create("emp-1", req);

        assertThat(result.getStatus()).isEqualTo(TimesheetStatus.DRAFT);
        assertThat(result.getEmployee()).isEqualTo(employee);
    }

    @Test
    void submit_shouldChangeStatusToSubmitted_andNotifyManager() {
        when(timesheetRepository.findById("ts-1")).thenReturn(Optional.of(timesheet));
        when(timesheetRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Timesheet result = timesheetService.submit("ts-1", "emp-1");

        assertThat(result.getStatus()).isEqualTo(TimesheetStatus.SUBMITTED);
        verify(notificationService).send(eq(manager), anyString(), anyString(), any(), anyString(), any());
    }

    @Test
    void submit_shouldThrow_whenNotOwner() {
        when(timesheetRepository.findById("ts-1")).thenReturn(Optional.of(timesheet));

        assertThatThrownBy(() -> timesheetService.submit("ts-1", "other-emp"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("do not own");
    }

    @Test
    void submit_shouldThrow_whenNotDraft() {
        timesheet.setStatus(TimesheetStatus.SUBMITTED);
        when(timesheetRepository.findById("ts-1")).thenReturn(Optional.of(timesheet));

        assertThatThrownBy(() -> timesheetService.submit("ts-1", "emp-1"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("not in DRAFT");
    }

    @Test
    void approve_shouldChangeStatusToApproved() {
        timesheet.setStatus(TimesheetStatus.SUBMITTED);
        when(timesheetRepository.findById("ts-1")).thenReturn(Optional.of(timesheet));
        when(employeeRepository.findById("mgr-1")).thenReturn(Optional.of(manager));
        when(timesheetRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Timesheet result = timesheetService.approve("ts-1", "mgr-1");

        assertThat(result.getStatus()).isEqualTo(TimesheetStatus.APPROVED);
        assertThat(result.getApprovedBy()).isEqualTo(manager);
    }

    @Test
    void reject_shouldChangeStatusToRejected_withReason() {
        timesheet.setStatus(TimesheetStatus.SUBMITTED);
        when(timesheetRepository.findById("ts-1")).thenReturn(Optional.of(timesheet));
        when(employeeRepository.findById("mgr-1")).thenReturn(Optional.of(manager));
        when(timesheetRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Timesheet result = timesheetService.reject("ts-1", "mgr-1", "Missing entries");

        assertThat(result.getStatus()).isEqualTo(TimesheetStatus.REJECTED);
        assertThat(result.getRejectionReason()).isEqualTo("Missing entries");
    }

    @Test
    void approve_shouldThrow_whenNotSubmitted() {
        when(timesheetRepository.findById("ts-1")).thenReturn(Optional.of(timesheet));

        assertThatThrownBy(() -> timesheetService.approve("ts-1", "mgr-1"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("not in SUBMITTED");
    }
}
