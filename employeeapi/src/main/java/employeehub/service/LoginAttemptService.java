package employeehub.service;

import employeehub.domain.Employee;
import employeehub.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Tracks consecutive failed login attempts per account and applies a temporary
 * lockout once a configurable threshold is reached.
 *
 * <p>Lockout state lives on the {@link Employee} entity ({@code failedLoginAttempts}
 * and {@code lockedUntil}). This service is invoked from the login flow only — it
 * does not touch the {@code UserDetailsService} used by the JWT filter, so an
 * already-issued valid token is never affected by lockout.
 *
 * <p>To avoid leaking which accounts exist, callers should apply lockout checks
 * only for known accounts and otherwise let authentication fail normally.
 */
@Service
@RequiredArgsConstructor
public class LoginAttemptService {

    private final EmployeeRepository employeeRepository;

    @Value("${app.security.lockout.max-attempts:5}")
    private int maxAttempts;

    @Value("${app.security.lockout.duration-minutes:15}")
    private long lockoutDurationMinutes;

    /**
     * @return true if the account for this email exists and is currently locked.
     *         Unknown emails return false (handled as a normal auth failure).
     */
    @Transactional(readOnly = true)
    public boolean isLocked(String email) {
        return employeeRepository.findByEmail(email)
                .map(this::isCurrentlyLocked)
                .orElse(false);
    }

    /**
     * The instant the current lockout ends, or null if the account is not locked.
     */
    @Transactional(readOnly = true)
    public LocalDateTime lockedUntil(String email) {
        return employeeRepository.findByEmail(email)
                .filter(this::isCurrentlyLocked)
                .map(Employee::getLockedUntil)
                .orElse(null);
    }

    /**
     * Whole seconds remaining until the account unlocks, or 0 if it is not
     * locked. Rounds up so a partial final second still reports at least 1.
     */
    @Transactional(readOnly = true)
    public long secondsUntilUnlock(String email) {
        LocalDateTime until = lockedUntil(email);
        if (until == null) {
            return 0;
        }
        long seconds = Duration.between(LocalDateTime.now(), until).getSeconds();
        return Math.max(0, seconds) + (seconds >= 0 ? 1 : 0);
    }

    /**
     * Record a failed login for the given email. Increments the counter and, once
     * the threshold is reached, sets a lockout window. No-op for unknown emails.
     */
    @Transactional
    public void recordFailure(String email) {
        employeeRepository.findByEmail(email).ifPresent(employee -> {
            int attempts = employee.getFailedLoginAttempts() + 1;
            employee.setFailedLoginAttempts(attempts);
            if (attempts >= maxAttempts) {
                employee.setLockedUntil(LocalDateTime.now().plusMinutes(lockoutDurationMinutes));
            }
            employeeRepository.save(employee);
        });
    }

    /**
     * Clear failure tracking after a successful login. No-op for unknown emails.
     */
    @Transactional
    public void recordSuccess(String email) {
        employeeRepository.findByEmail(email).ifPresent(employee -> {
            if (employee.getFailedLoginAttempts() != 0 || employee.getLockedUntil() != null) {
                employee.setFailedLoginAttempts(0);
                employee.setLockedUntil(null);
                employeeRepository.save(employee);
            }
        });
    }

    private boolean isCurrentlyLocked(Employee employee) {
        LocalDateTime until = employee.getLockedUntil();
        return until != null && until.isAfter(LocalDateTime.now());
    }
}
