package employeehub.service;

import employeehub.domain.Department;
import employeehub.domain.Employee;
import employeehub.domain.LeaveBalance;
import employeehub.domain.LeaveType;
import employeehub.domain.Team;
import employeehub.domain.enums.EmploymentStatus;
import employeehub.dto.EmployeeRequest;
import employeehub.exception.ResourceNotFoundException;
import employeehub.repository.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final TeamRepository teamRepository;
    private final PasswordEncoder passwordEncoder;
    private final LeaveBalanceRepository leaveBalanceRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final LeaveTypeRepository leaveTypeRepository;
    private final NotificationRepository notificationRepository;

    @Transactional
    public Employee create(EmployeeRequest req) {
        if (employeeRepository.existsByEmail(req.getEmail())) {
            throw new IllegalArgumentException("Email already in use: " + req.getEmail());
        }
        if (req.getPassword() == null || req.getPassword().isBlank()) {
            throw new IllegalArgumentException("Password is required");
        }
        Employee employee = mapToEmployee(new Employee(), req);
        employee.setPassword(passwordEncoder.encode(req.getPassword()));
        employee.setEmployeeNumber(generateEmployeeNumber());
        Employee saved = employeeRepository.save(employee);
        createLeaveBalances(saved);
        return saved;
    }

    public Employee getById(String id) {
        return employeeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found: " + id));
    }

    public Employee getByEmail(String email) {
        return employeeRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found for email: " + email));
    }

    public Page<Employee> getAll(Long departmentId, Long teamId, EmploymentStatus status, Pageable pageable) {
        return employeeRepository.findAllFiltered(departmentId, teamId, status, pageable);
    }

    @Transactional
    public Employee update(String id, EmployeeRequest req) {
        Employee existing = getById(id);
        return employeeRepository.save(mapToEmployee(existing, req));
    }

    @Transactional
    public Employee updateStatus(String id, EmploymentStatus status) {
        Employee employee = getById(id);
        employee.setEmploymentStatus(status);
        if (status == EmploymentStatus.TERMINATED) {
            employee.setEndDate(java.time.LocalDate.now());
        }
        return employeeRepository.save(employee);
    }

    public Employee updatePhoto(String id, String filename) {
        Employee employee = getById(id);
        employee.setProfilePhoto(filename);
        return employeeRepository.save(employee);
    }

    @Transactional
    public void delete(String id) {
        Employee employee = getById(id);
        leaveBalanceRepository.deleteByEmployeeId(id);
        leaveRequestRepository.deleteByEmployeeId(id);
        notificationRepository.deleteByRecipientId(id);
        employeeRepository.delete(employee);
    }

    private Employee mapToEmployee(Employee employee, EmployeeRequest req) {
        Department department = departmentRepository.findById(req.getDepartmentId())
                .orElseThrow(() -> new ResourceNotFoundException("Department not found: " + req.getDepartmentId()));
        Team team = teamRepository.findById(req.getTeamId())
                .orElseThrow(() -> new ResourceNotFoundException("Team not found: " + req.getTeamId()));

        employee.setFirstName(req.getFirstName());
        employee.setLastName(req.getLastName());
        employee.setEmail(req.getEmail());
        employee.setPhone(req.getPhone());
        employee.setDateOfBirth(req.getDateOfBirth());
        employee.setGender(req.getGender());
        employee.setNationality(req.getNationality());
        employee.setIdNumber(req.getIdNumber());
        employee.setAddress(req.getAddress());
        employee.setJobTitle(req.getJobTitle());
        employee.setEmploymentType(req.getEmploymentType());
        employee.setStartDate(req.getStartDate());
        employee.setEndDate(req.getEndDate());
        employee.setDepartment(department);
        employee.setTeam(team);
        employee.setRole(req.getRole() != null ? req.getRole() : employee.getRole());
        employee.setEmploymentStatus(req.getEmploymentStatus() != null ? req.getEmploymentStatus() : employee.getEmploymentStatus());

        if (req.getManagerId() != null && !req.getManagerId().isBlank()) {
            Employee manager = employeeRepository.findById(req.getManagerId())
                    .orElseThrow(() -> new ResourceNotFoundException("Manager not found: " + req.getManagerId()));
            employee.setManager(manager);
        }

        return employee;
    }

    private void createLeaveBalances(Employee employee) {
        List<LeaveType> leaveTypes = leaveTypeRepository.findAll();
        LocalDate now = LocalDate.now();
        for (LeaveType type : leaveTypes) {
            LeaveBalance balance = new LeaveBalance();
            balance.setEmployee(employee);
            balance.setLeaveType(type);
            balance.setTotalDays(BigDecimal.valueOf(type.getDefaultDays()));
            balance.setUsedDays(BigDecimal.ZERO);
            balance.setRemainingDays(BigDecimal.valueOf(type.getDefaultDays()));
            balance.setCycleStartDate(now);
            balance.setCycleEndDate(now.plusYears(type.getCycleYears()));
            leaveBalanceRepository.save(balance);
        }
    }

    private String generateEmployeeNumber() {
        int next = employeeRepository.findMaxEmployeeSequence()
                .map(max -> max + 1)
                .orElse(1);
        return String.format("EMP-%03d", next);
    }
}
