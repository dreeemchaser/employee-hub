package employeehub.service;

import employeehub.domain.Employee;
import employeehub.repository.EmployeeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoginAttemptServiceTest {

    @Mock EmployeeRepository employeeRepository;

    @InjectMocks LoginAttemptService loginAttemptService;

    private Employee employee;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(loginAttemptService, "maxAttempts", 3);
        ReflectionTestUtils.setField(loginAttemptService, "lockoutDurationMinutes", 15L);

        employee = new Employee();
        employee.setId("emp-1");
        employee.setEmail("user@example.com");
        employee.setFailedLoginAttempts(0);
    }

    @Test
    void recordFailure_shouldIncrementCounter_belowThreshold() {
        when(employeeRepository.findByEmail("user@example.com")).thenReturn(Optional.of(employee));
        when(employeeRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        loginAttemptService.recordFailure("user@example.com");

        assertThat(employee.getFailedLoginAttempts()).isEqualTo(1);
        assertThat(employee.getLockedUntil()).isNull();
    }

    @Test
    void recordFailure_shouldLockAccount_atThreshold() {
        employee.setFailedLoginAttempts(2); // one below threshold of 3
        when(employeeRepository.findByEmail("user@example.com")).thenReturn(Optional.of(employee));
        when(employeeRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        loginAttemptService.recordFailure("user@example.com");

        assertThat(employee.getFailedLoginAttempts()).isEqualTo(3);
        assertThat(employee.getLockedUntil()).isNotNull();
        assertThat(employee.getLockedUntil()).isAfter(LocalDateTime.now());
    }

    @Test
    void recordFailure_shouldBeNoOp_forUnknownEmail() {
        when(employeeRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        loginAttemptService.recordFailure("ghost@example.com");

        verify(employeeRepository, never()).save(any());
    }

    @Test
    void recordSuccess_shouldResetCounterAndUnlock() {
        employee.setFailedLoginAttempts(2);
        employee.setLockedUntil(LocalDateTime.now().plusMinutes(10));
        when(employeeRepository.findByEmail("user@example.com")).thenReturn(Optional.of(employee));
        when(employeeRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        loginAttemptService.recordSuccess("user@example.com");

        assertThat(employee.getFailedLoginAttempts()).isZero();
        assertThat(employee.getLockedUntil()).isNull();
    }

    @Test
    void recordSuccess_shouldNotSave_whenNothingToClear() {
        // Fresh account with no failures — avoid a needless write.
        when(employeeRepository.findByEmail("user@example.com")).thenReturn(Optional.of(employee));

        loginAttemptService.recordSuccess("user@example.com");

        verify(employeeRepository, never()).save(any());
    }

    @Test
    void isLocked_shouldReturnTrue_whenLockInFuture() {
        employee.setLockedUntil(LocalDateTime.now().plusMinutes(5));
        when(employeeRepository.findByEmail("user@example.com")).thenReturn(Optional.of(employee));

        assertThat(loginAttemptService.isLocked("user@example.com")).isTrue();
    }

    @Test
    void isLocked_shouldReturnFalse_whenLockExpired() {
        employee.setLockedUntil(LocalDateTime.now().minusMinutes(1));
        when(employeeRepository.findByEmail("user@example.com")).thenReturn(Optional.of(employee));

        assertThat(loginAttemptService.isLocked("user@example.com")).isFalse();
    }

    @Test
    void isLocked_shouldReturnFalse_forUnknownEmail() {
        when(employeeRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        assertThat(loginAttemptService.isLocked("ghost@example.com")).isFalse();
    }

    @Test
    void lockedUntil_shouldReturnInstant_whenLocked() {
        LocalDateTime until = LocalDateTime.now().plusMinutes(5);
        employee.setLockedUntil(until);
        when(employeeRepository.findByEmail("user@example.com")).thenReturn(Optional.of(employee));

        assertThat(loginAttemptService.lockedUntil("user@example.com")).isEqualTo(until);
    }

    @Test
    void lockedUntil_shouldReturnNull_whenNotLocked() {
        when(employeeRepository.findByEmail("user@example.com")).thenReturn(Optional.of(employee));

        assertThat(loginAttemptService.lockedUntil("user@example.com")).isNull();
    }

    @Test
    void secondsUntilUnlock_shouldReturnPositive_whenLocked() {
        employee.setLockedUntil(LocalDateTime.now().plusMinutes(5));
        when(employeeRepository.findByEmail("user@example.com")).thenReturn(Optional.of(employee));

        long seconds = loginAttemptService.secondsUntilUnlock("user@example.com");

        // ~300s remaining; allow a small window for execution time.
        assertThat(seconds).isBetween(290L, 301L);
    }

    @Test
    void secondsUntilUnlock_shouldReturnZero_whenNotLocked() {
        when(employeeRepository.findByEmail("user@example.com")).thenReturn(Optional.of(employee));

        assertThat(loginAttemptService.secondsUntilUnlock("user@example.com")).isZero();
    }
}
