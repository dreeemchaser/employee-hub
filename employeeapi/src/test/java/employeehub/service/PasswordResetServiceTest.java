package employeehub.service;

import employeehub.domain.Employee;
import employeehub.domain.PasswordResetToken;
import employeehub.dto.ResetPasswordRequest;
import employeehub.exception.ResourceNotFoundException;
import employeehub.repository.EmployeeRepository;
import employeehub.repository.PasswordResetTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    @Mock EmployeeRepository employeeRepository;
    @Mock PasswordResetTokenRepository tokenRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock EmailService emailService;

    @InjectMocks PasswordResetService passwordResetService;

    private Employee employee;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(passwordResetService, "tokenTtlMinutes", 30L);

        employee = new Employee();
        employee.setId("emp-1");
        employee.setEmail("user@example.com");
        employee.setFirstName("Sam");
        employee.setPassword("oldHash");
    }

    @Test
    void createResetToken_shouldIssueToken_forKnownEmail() {
        when(employeeRepository.findByEmail("user@example.com")).thenReturn(Optional.of(employee));
        when(tokenRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Optional<String> token = passwordResetService.createResetToken("user@example.com");

        assertThat(token).isPresent();
        verify(tokenRepository).invalidateActiveTokensForEmployee("emp-1");
        verify(tokenRepository).save(argThat(t ->
                t.getEmployee().equals(employee) &&
                t.getExpiresAt().isAfter(LocalDateTime.now()) &&
                t.getUsedAt() == null));
        verify(emailService).sendPlainText(eq("user@example.com"), anyString(), contains(token.get()));
    }

    @Test
    void createResetToken_shouldReturnEmpty_forUnknownEmail() {
        when(employeeRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        Optional<String> token = passwordResetService.createResetToken("ghost@example.com");

        assertThat(token).isEmpty();
        verify(tokenRepository, never()).save(any());
        verify(tokenRepository, never()).invalidateActiveTokensForEmployee(any());
    }

    @Test
    void resetPassword_shouldSetNewPassword_andConsumeToken_andClearLockout() {
        employee.setFailedLoginAttempts(5);
        employee.setLockedUntil(LocalDateTime.now().plusMinutes(10));

        PasswordResetToken token = new PasswordResetToken();
        token.setToken("tok-1");
        token.setEmployee(employee);
        token.setExpiresAt(LocalDateTime.now().plusMinutes(15));

        when(tokenRepository.findByToken("tok-1")).thenReturn(Optional.of(token));
        when(passwordEncoder.encode("NewPass123")).thenReturn("newHash");
        when(employeeRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(tokenRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        passwordResetService.resetPassword(request("tok-1", "NewPass123"));

        assertThat(employee.getPassword()).isEqualTo("newHash");
        assertThat(employee.getFailedLoginAttempts()).isZero();
        assertThat(employee.getLockedUntil()).isNull();
        assertThat(token.getUsedAt()).isNotNull();
    }

    @Test
    void resetPassword_shouldThrow_whenTokenUnknown() {
        when(tokenRepository.findByToken("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> passwordResetService.resetPassword(request("missing", "NewPass123")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void resetPassword_shouldThrow_whenTokenExpired() {
        PasswordResetToken token = new PasswordResetToken();
        token.setToken("tok-exp");
        token.setEmployee(employee);
        token.setExpiresAt(LocalDateTime.now().minusMinutes(1)); // expired

        when(tokenRepository.findByToken("tok-exp")).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> passwordResetService.resetPassword(request("tok-exp", "NewPass123")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expired");
        verify(employeeRepository, never()).save(any());
    }

    @Test
    void resetPassword_shouldThrow_whenTokenAlreadyUsed() {
        PasswordResetToken token = new PasswordResetToken();
        token.setToken("tok-used");
        token.setEmployee(employee);
        token.setExpiresAt(LocalDateTime.now().plusMinutes(15));
        token.setUsedAt(LocalDateTime.now().minusMinutes(1)); // already consumed

        when(tokenRepository.findByToken("tok-used")).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> passwordResetService.resetPassword(request("tok-used", "NewPass123")))
                .isInstanceOf(IllegalArgumentException.class);
        verify(employeeRepository, never()).save(any());
    }

    private ResetPasswordRequest request(String token, String newPassword) {
        ResetPasswordRequest r = new ResetPasswordRequest();
        r.setToken(token);
        r.setNewPassword(newPassword);
        return r;
    }
}
