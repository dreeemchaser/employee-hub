package employeehub.service;

import employeehub.domain.Department;
import employeehub.domain.Employee;
import employeehub.domain.LeaveType;
import employeehub.domain.Team;
import employeehub.domain.enums.EmploymentStatus;
import employeehub.domain.enums.EmploymentType;
import employeehub.domain.enums.Role;
import employeehub.dto.EmployeeRequest;
import employeehub.dto.EmployeeResponse;
import employeehub.exception.BusinessRuleException;
import employeehub.exception.ResourceNotFoundException;
import employeehub.domain.EmployeeBenefit;
import employeehub.domain.LeaveRequest;
import employeehub.domain.enums.BenefitStatus;
import employeehub.domain.enums.LeaveStatus;
import employeehub.dto.OffboardEmployeeRequest;
import employeehub.repository.DepartmentRepository;
import employeehub.repository.EmployeeBenefitRepository;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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
    @Mock EmployeeBenefitRepository employeeBenefitRepository;
    @Mock AuditService auditService;
    @Mock NotificationService notificationService;

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
                .isInstanceOf(BusinessRuleException.class)
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

    /**
     * Regression test for the "HR dashboard shows no employees" bug.
     *
     * <p>Root cause: {@code getAll} returned raw {@link Employee} entities whose
     * lazy {@code manager} association was serialized outside the Hibernate
     * session, throwing mid-stream and corrupting the JSON response. The fix
     * projects to {@link EmployeeResponse}, flattening the manager to a name/id
     * so no lazy proxy reaches the serializer. This test pins that contract: a
     * page containing a MANAGER and one of their reports maps cleanly, with the
     * report's manager flattened to a display name.
     */
    @Test
    void getAll_shouldReturnFlattenedResponses_forManagerAndReport() {
        Employee manager = new Employee();
        manager.setId("mgr-1");
        manager.setFirstName("Alex");
        manager.setLastName("Smith");
        manager.setEmail("alex.smith@test.com");
        manager.setJobTitle("Team Lead");
        manager.setRole(Role.MANAGER);
        manager.setEmploymentStatus(EmploymentStatus.ACTIVE);
        manager.setDepartment(department);
        manager.setTeam(team);

        Employee report = new Employee();
        report.setId("emp-2");
        report.setFirstName("Kim");
        report.setLastName("Lee");
        report.setEmail("kim.lee@test.com");
        report.setJobTitle("Engineer");
        report.setRole(Role.EMPLOYEE);
        report.setEmploymentStatus(EmploymentStatus.ACTIVE);
        report.setDepartment(department);
        report.setTeam(team);
        report.setManager(manager);

        Pageable pageable = PageRequest.of(0, 10);
        Page<Employee> page = new PageImpl<>(List.of(manager, report), pageable, 2);
        when(employeeRepository.findAllFiltered(null, null, null, pageable)).thenReturn(page);

        Page<EmployeeResponse> result = employeeService.getAll(null, null, null, pageable);

        assertThat(result.getTotalElements()).isEqualTo(2);
        EmployeeResponse mgrDto = result.getContent().get(0);
        EmployeeResponse reportDto = result.getContent().get(1);

        // Manager has no manager of their own -> null, and department/team flattened to names.
        assertThat(mgrDto.getRole()).isEqualTo(Role.MANAGER);
        assertThat(mgrDto.getManager()).isNull();
        assertThat(mgrDto.getDepartment()).isEqualTo("Engineering");
        assertThat(mgrDto.getTeam()).isEqualTo("Backend");

        // Report's manager is flattened to a display name + id, not an entity.
        assertThat(reportDto.getManager()).isEqualTo("Alex Smith");
        assertThat(reportDto.getManagerId()).isEqualTo("mgr-1");
        assertThat(reportDto.getEmail()).isEqualTo("kim.lee@test.com");
    }

    @Test
    void offboard_shouldTerminateAndCascade() {
        Employee manager = new Employee();
        manager.setId("mgr-1");

        Employee employee = new Employee();
        employee.setId("emp-1");
        employee.setFirstName("Kim");
        employee.setLastName("Lee");
        employee.setEmploymentStatus(EmploymentStatus.ACTIVE);
        employee.setManager(manager);

        LeaveRequest pending = new LeaveRequest();
        pending.setId("lr-1");
        pending.setStatus(LeaveStatus.PENDING);
        LeaveRequest approved = new LeaveRequest();
        approved.setId("lr-2");
        approved.setStatus(LeaveStatus.APPROVED);

        EmployeeBenefit activeBenefit = new EmployeeBenefit();
        activeBenefit.setId("ben-1");
        activeBenefit.setStatus(BenefitStatus.ACTIVE);

        Employee actor = new Employee();
        actor.setId("hr-1");

        when(employeeRepository.findById("emp-1")).thenReturn(Optional.of(employee));
        when(employeeRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(leaveRequestRepository.findByEmployeeId("emp-1")).thenReturn(List.of(pending, approved));
        when(leaveRequestRepository.saveAll(any())).thenAnswer(i -> i.getArgument(0));
        when(employeeBenefitRepository.findByEmployeeId("emp-1")).thenReturn(List.of(activeBenefit));
        when(employeeBenefitRepository.saveAll(any())).thenAnswer(i -> i.getArgument(0));

        OffboardEmployeeRequest req = new OffboardEmployeeRequest();
        req.setReason("Resignation");
        req.setLastWorkingDay(LocalDate.now());

        Employee result = employeeService.offboard("emp-1", req, actor);

        assertThat(result.getEmploymentStatus()).isEqualTo(EmploymentStatus.TERMINATED);
        assertThat(result.getEndDate()).isEqualTo(req.getLastWorkingDay());
        assertThat(pending.getStatus()).isEqualTo(LeaveStatus.CANCELLED);
        assertThat(approved.getStatus()).isEqualTo(LeaveStatus.APPROVED); // untouched
        assertThat(activeBenefit.getStatus()).isEqualTo(BenefitStatus.INACTIVE);
        assertThat(activeBenefit.getEndDate()).isEqualTo(req.getLastWorkingDay());

        verify(auditService).log(eq(actor), eq("OFFBOARD"), eq("Employee"), eq("emp-1"), eq("ACTIVE"), eq("TERMINATED"));
        verify(notificationService).send(eq(manager), anyString(), anyString(), any(), anyString(), any());
    }

    @Test
    void offboard_shouldDefaultLastWorkingDayToToday_whenOmitted() {
        Employee employee = new Employee();
        employee.setId("emp-1");
        employee.setEmploymentStatus(EmploymentStatus.ACTIVE);
        when(employeeRepository.findById("emp-1")).thenReturn(Optional.of(employee));
        when(employeeRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(leaveRequestRepository.findByEmployeeId("emp-1")).thenReturn(List.of());
        when(employeeBenefitRepository.findByEmployeeId("emp-1")).thenReturn(List.of());

        OffboardEmployeeRequest req = new OffboardEmployeeRequest();
        req.setReason("Resignation");

        Employee result = employeeService.offboard("emp-1", req, new Employee());

        assertThat(result.getEndDate()).isEqualTo(LocalDate.now());
    }

    @Test
    void offboard_shouldThrow_whenAlreadyTerminated() {
        Employee employee = new Employee();
        employee.setId("emp-1");
        employee.setEmploymentStatus(EmploymentStatus.TERMINATED);
        when(employeeRepository.findById("emp-1")).thenReturn(Optional.of(employee));

        OffboardEmployeeRequest req = new OffboardEmployeeRequest();
        req.setReason("Resignation");

        assertThatThrownBy(() -> employeeService.offboard("emp-1", req, new Employee()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already terminated");
    }

    @Test
    void offboard_shouldNotNotify_whenEmployeeHasNoManager() {
        Employee employee = new Employee();
        employee.setId("emp-1");
        employee.setEmploymentStatus(EmploymentStatus.ACTIVE);
        // no manager set
        when(employeeRepository.findById("emp-1")).thenReturn(Optional.of(employee));
        when(employeeRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(leaveRequestRepository.findByEmployeeId("emp-1")).thenReturn(List.of());
        when(employeeBenefitRepository.findByEmployeeId("emp-1")).thenReturn(List.of());

        OffboardEmployeeRequest req = new OffboardEmployeeRequest();
        req.setReason("Resignation");

        employeeService.offboard("emp-1", req, new Employee());

        verify(notificationService, never()).send(any(), anyString(), anyString(), any(), anyString(), any());
    }
}
