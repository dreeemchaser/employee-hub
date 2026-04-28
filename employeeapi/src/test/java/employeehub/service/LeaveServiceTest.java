package employeehub.service;

import employeehub.domain.*;
import employeehub.domain.enums.LeaveStatus;
import employeehub.domain.enums.Role;
import employeehub.dto.LeaveRequestDto;
import employeehub.exception.ResourceNotFoundException;
import employeehub.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LeaveServiceTest {

    @Mock LeaveRequestRepository leaveRequestRepository;
    @Mock LeaveBalanceRepository leaveBalanceRepository;
    @Mock LeaveTypeRepository leaveTypeRepository;
    @Mock EmployeeRepository employeeRepository;
    @Mock NotificationService notificationService;
    @Mock AuditService auditService;

    @InjectMocks LeaveService leaveService;

    private Employee employee;
    private Employee manager;
    private LeaveType leaveType;
    private LeaveBalance balance;
    private LeaveRequestDto dto;

    @BeforeEach
    void setUp() {
        manager = new Employee();
        manager.setId("mgr-1");
        manager.setRole(Role.MANAGER);

        employee = new Employee();
        employee.setId("emp-1");
        employee.setFirstName("John");
        employee.setLastName("Doe");
        employee.setRole(Role.EMPLOYEE);
        employee.setManager(manager);

        leaveType = new LeaveType();
        leaveType.setId(1L);
        leaveType.setName("Annual Leave");
        leaveType.setDefaultDays(15);

        balance = new LeaveBalance();
        balance.setEmployee(employee);
        balance.setLeaveType(leaveType);
        balance.setTotalDays(BigDecimal.valueOf(15));
        balance.setUsedDays(BigDecimal.ZERO);
        balance.setRemainingDays(BigDecimal.valueOf(15));

        dto = new LeaveRequestDto();
        dto.setLeaveTypeId(1L);
        dto.setStartDate(LocalDate.now().plusDays(1));
        dto.setEndDate(LocalDate.now().plusDays(3));
        dto.setReason("Holiday");
    }

    @Test
    void submit_shouldCreateLeaveRequest() {
        when(employeeRepository.findById("emp-1")).thenReturn(Optional.of(employee));
        when(leaveTypeRepository.findById(1L)).thenReturn(Optional.of(leaveType));
        when(leaveBalanceRepository.findByEmployeeIdAndLeaveTypeId("emp-1", 1L)).thenReturn(Optional.of(balance));
        when(leaveRequestRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        LeaveRequest result = leaveService.submit("emp-1", dto);

        assertThat(result.getStatus()).isEqualTo(LeaveStatus.PENDING);
        assertThat(result.getEmployee()).isEqualTo(employee);
        verify(notificationService).send(eq(manager), anyString(), anyString(), any(), anyString(), any());
    }

    @Test
    void submit_shouldThrow_whenInsufficientBalance() {
        balance.setRemainingDays(BigDecimal.valueOf(1));
        when(employeeRepository.findById("emp-1")).thenReturn(Optional.of(employee));
        when(leaveTypeRepository.findById(1L)).thenReturn(Optional.of(leaveType));
        when(leaveBalanceRepository.findByEmployeeIdAndLeaveTypeId("emp-1", 1L)).thenReturn(Optional.of(balance));

        assertThatThrownBy(() -> leaveService.submit("emp-1", dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Insufficient leave balance");
    }

    @Test
    void approve_shouldUpdateStatusAndDeductBalance() {
        LeaveRequest request = new LeaveRequest();
        request.setId("req-1");
        request.setEmployee(employee);
        request.setLeaveType(leaveType);
        request.setTotalDays(BigDecimal.valueOf(3));
        request.setStatus(LeaveStatus.PENDING);

        when(leaveRequestRepository.findById("req-1")).thenReturn(Optional.of(request));
        when(employeeRepository.findById("mgr-1")).thenReturn(Optional.of(manager));
        when(leaveBalanceRepository.findByEmployeeIdAndLeaveTypeId(any(), any())).thenReturn(Optional.of(balance));
        when(leaveRequestRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        LeaveRequest result = leaveService.approve("req-1", "mgr-1");

        assertThat(result.getStatus()).isEqualTo(LeaveStatus.APPROVED);
        verify(notificationService).send(eq(employee), anyString(), anyString(), any(), anyString(), any());
        verify(auditService).log(any(), eq("APPROVE"), eq("LeaveRequest"), any(), any(), any());
    }

    @Test
    void reject_shouldUpdateStatusAndNotifyEmployee() {
        LeaveRequest request = new LeaveRequest();
        request.setId("req-1");
        request.setEmployee(employee);
        request.setLeaveType(leaveType);
        request.setStatus(LeaveStatus.PENDING);

        when(leaveRequestRepository.findById("req-1")).thenReturn(Optional.of(request));
        when(employeeRepository.findById("mgr-1")).thenReturn(Optional.of(manager));
        when(leaveRequestRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        LeaveRequest result = leaveService.reject("req-1", "mgr-1", "Not enough cover");

        assertThat(result.getStatus()).isEqualTo(LeaveStatus.REJECTED);
        assertThat(result.getRejectionReason()).isEqualTo("Not enough cover");
        verify(notificationService).send(eq(employee), anyString(), anyString(), any(), anyString(), any());
    }

    @Test
    void cancel_shouldThrow_whenNotPending() {
        LeaveRequest request = new LeaveRequest();
        request.setId("req-1");
        request.setEmployee(employee);
        request.setStatus(LeaveStatus.APPROVED);

        when(leaveRequestRepository.findById("req-1")).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> leaveService.cancel("req-1", "emp-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Only PENDING requests can be cancelled");
    }

    @Test
    void cancel_shouldThrow_whenNotOwner() {
        LeaveRequest request = new LeaveRequest();
        request.setId("req-1");
        request.setEmployee(employee);
        request.setStatus(LeaveStatus.PENDING);

        when(leaveRequestRepository.findById("req-1")).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> leaveService.cancel("req-1", "other-emp"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("your own requests");
    }

}
