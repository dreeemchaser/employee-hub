package employeehub.service;

import employeehub.domain.Employee;
import employeehub.domain.PasswordResetToken;
import employeehub.dto.ResetPasswordRequest;
import employeehub.exception.ResourceNotFoundException;
import employeehub.repository.EmployeeRepository;
import employeehub.repository.PasswordResetTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Issues and consumes single-use, time-limited password reset tokens.
 *
 * <p>Token creation never reveals whether an email is registered — callers
 * always get the same generic response. A successful reset also clears any
 * account-lockout state so the user can sign in immediately.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private final EmployeeRepository employeeRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    @Value("${app.security.password-reset.token-ttl-minutes:30}")
    private long tokenTtlMinutes;

    // Base URL of the app the user resets from; the token is appended as a query
    // param. Defaults to the employee portal dev URL.
    @Value("${app.security.password-reset.reset-url:http://localhost:3000/reset-password}")
    private String resetUrl;

    /**
     * Create a reset token for the account with this email, invalidating any
     * previously issued active tokens, and email the reset link. Returns the raw
     * token when the account exists, or empty otherwise — the caller must not
     * leak which case occurred.
     */
    @Transactional
    public Optional<String> createResetToken(String email) {
        return employeeRepository.findByEmail(email).map(employee -> {
            tokenRepository.invalidateActiveTokensForEmployee(employee.getId());

            PasswordResetToken token = new PasswordResetToken();
            token.setToken(UUID.randomUUID().toString());
            token.setEmployee(employee);
            token.setExpiresAt(LocalDateTime.now().plusMinutes(tokenTtlMinutes));
            tokenRepository.save(token);

            sendResetEmail(employee, token.getToken());
            return token.getToken();
        });
    }

    private void sendResetEmail(Employee employee, String token) {
        String link = resetUrl + "?token=" + token;
        String body = "Hi " + employee.getFirstName() + ",\n\n"
                + "We received a request to reset your EmployeeHub password. "
                + "Use the link below to choose a new password. This link expires in "
                + tokenTtlMinutes + " minutes.\n\n"
                + link + "\n\n"
                + "If you did not request this, you can safely ignore this email.";
        emailService.sendPlainText(employee.getEmail(), "Reset your EmployeeHub password", body);
    }

    /**
     * Consume a reset token and set the new password. Also clears any lockout so
     * the account is immediately usable.
     *
     * @throws ResourceNotFoundException if the token does not exist
     * @throws IllegalArgumentException  if the token is expired or already used
     */
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        PasswordResetToken token = tokenRepository.findByToken(request.getToken())
                .orElseThrow(() -> new ResourceNotFoundException("Invalid or unknown reset token"));

        if (!token.isValid()) {
            throw new IllegalArgumentException("Reset token has expired or has already been used");
        }

        Employee employee = token.getEmployee();
        employee.setPassword(passwordEncoder.encode(request.getNewPassword()));
        // A successful reset should also lift any active lockout.
        employee.setFailedLoginAttempts(0);
        employee.setLockedUntil(null);
        employeeRepository.save(employee);

        token.setUsedAt(LocalDateTime.now());
        tokenRepository.save(token);

        // Security-relevant state change: log actor id only, never the password.
        log.info("Password reset completed for employee id={}", employee.getId());
    }
}
