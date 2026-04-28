package employeehub.service;

import employeehub.domain.Department;
import employeehub.domain.Employee;
import employeehub.domain.LeaveType;
import employeehub.domain.Team;
import employeehub.domain.enums.EmploymentStatus;
import employeehub.domain.enums.EmploymentType;
import employeehub.domain.enums.Role;
import employeehub.dto.EmployeeRequest;
import employeehub.exception.ResourceNotFoundException;
import employeehub.repository.DepartmentRepository;
import employeehub.repository.EmployeeRepository;
import employeehub.repository.LeaveBalanceRepository;
import employeehub.repository.LeaveRequestRepository;
import employeehub.repository.LeaveTypeRepository;
import employeehub.repository.NotificationRepository;
import employeehub.repository.TeamRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmployeeServiceTest {

    @Mock EmployeeRepository employeeRepository;
    @Mock DepartmentRepository departmentRepository;
    @Mock TeamRepository teamRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock LeaveBalanceRepository leaveBalanceRepository;
    @Mock LeaveRequestRepository leaveRequestRepository;
    @Mock LeaveTypeRepository leaveTypeRepository;
    @Mock NotificationRepository notificationRepository;

    @InjectMocks EmployeeService employeeService;

    private Department department;
    private Team team;
    private EmployeeRequest request;

    @BeforeEach
    void setUp() {
        department = new Department();
        department.setId(1L);
        department.setName("Engineering");

        team = new Team();
        team.setId(1L);
        team.setName("Backend");
        team.setDepartment(department);

        request = new EmployeeRequest();
        request.setFirstName("John");
        request.setLastName("Doe");
        request.setEmail("john.doe@test.com");
        request.setJobTitle("Developer");
        request.setEmploymentType(EmploymentType.FULL_TIME);
        request.setEmploymentStatus(EmploymentStatus.ACTIVE);
        request.setStartDate(LocalDate.now());
        request.setDepartmentId(1L);
        request.setTeamId(1L);
        request.setRole(Role.EMPLOYEE);
        request.setPassword("secret123");
    }

    @Test
    void create_shouldSaveEmployeeWithGeneratedNumber() {
        LeaveType leaveType = new LeaveType();
        leaveType.setId(1L);
        leaveType.setName("Annual Leave");
        leaveType.setDefaultDays(15);
        leaveType.setCycleYears(1);

        when(employeeRepository.existsByEmail(anyString())).thenReturn(false);
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(department));
        when(teamRepository.findById(1L)).thenReturn(Optional.of(team));
        when(employeeRepository.findMaxEmployeeSequence()).thenReturn(Optional.empty());
        when(employeeRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(passwordEncoder.encode(anyString())).thenReturn("encoded-secret");
        when(leaveTypeRepository.findAll()).thenReturn(List.of(leaveType));

        Employee result = employeeService.create(request);

        assertThat(result.getEmployeeNumber()).isEqualTo("EMP-001");
        assertThat(result.getEmail()).isEqualTo("john.doe@test.com");
        verify(leaveBalanceRepository, atLeastOnce()).save(any());
    }

    @Test
    void create_shouldThrow_whenEmailAlreadyExists() {
        when(employeeRepository.existsByEmail(anyString())).thenReturn(true);

        assertThatThrownBy(() -> employeeService.create(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Email already in use");
    }

    @Test
    void getById_shouldReturnEmployee() {
        Employee employee = new Employee();
        employee.setId("emp-1");
        when(employeeRepository.findById("emp-1")).thenReturn(Optional.of(employee));

        Employee result = employeeService.getById("emp-1");

        assertThat(result.getId()).isEqualTo("emp-1");
    }

    @Test
    void getById_shouldThrow_whenNotFound() {
        when(employeeRepository.findById(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> employeeService.getById("missing"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateStatus_shouldSetTerminatedAndEndDate() {
        Employee employee = new Employee();
        employee.setId("emp-1");
        when(employeeRepository.findById("emp-1")).thenReturn(Optional.of(employee));
        when(employeeRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Employee result = employeeService.updateStatus("emp-1", EmploymentStatus.TERMINATED);

        assertThat(result.getEmploymentStatus()).isEqualTo(EmploymentStatus.TERMINATED);
        assertThat(result.getEndDate()).isEqualTo(LocalDate.now());
    }

    @Test
    void updatePhoto_shouldPersistFilename() {
        Employee employee = new Employee();
        employee.setId("emp-1");
        when(employeeRepository.findById("emp-1")).thenReturn(Optional.of(employee));
        when(employeeRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Employee result = employeeService.updatePhoto("emp-1", "photo.jpg");

        assertThat(result.getProfilePhoto()).isEqualTo("photo.jpg");
    }
}
