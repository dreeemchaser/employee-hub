package employeehub.service;

import employeehub.domain.Employee;
import employeehub.domain.RefreshToken;
import employeehub.dto.TokenPair;
import employeehub.repository.RefreshTokenRepository;
import employeehub.security.JwtUtil;
import employeehub.domain.enums.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
class RefreshTokenServiceTest {

    @Mock RefreshTokenRepository refreshTokenRepository;
    @Mock JwtUtil jwtUtil;

    @InjectMocks RefreshTokenService refreshTokenService;

    private Employee employee;

    @BeforeEach
    void setUp() {
        // 7 days in ms
        ReflectionTestUtils.setField(refreshTokenService, "refreshExpirationMs", 604800000L);

        employee = new Employee();
        employee.setId("emp-1");
        employee.setEmail("user@example.com");
        employee.setRole(Role.EMPLOYEE);
    }

    @Test
    void issue_shouldPersistHashNotRawToken_andReturnRawToken() {
        when(refreshTokenRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        String raw = refreshTokenService.issue(employee);

        assertThat(raw).isNotBlank();
        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        RefreshToken saved = captor.getValue();
        // The stored hash must not equal the raw token, and must be non-blank.
        assertThat(saved.getTokenHash()).isNotBlank().isNotEqualTo(raw);
        assertThat(saved.getEmployee()).isEqualTo(employee);
        assertThat(saved.isRevoked()).isFalse();
        assertThat(saved.getExpiresAt()).isAfter(LocalDateTime.now());
    }

    @Test
    void rotate_shouldRevokeOld_issueNew_andReturnPair_forActiveToken() {
        RefreshToken active = new RefreshToken();
        active.setEmployee(employee);
        active.setExpiresAt(LocalDateTime.now().plusDays(1));
        active.setRevoked(false);

        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(active));
        when(refreshTokenRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(jwtUtil.generateToken("user@example.com", "EMPLOYEE")).thenReturn("new-access-jwt");

        Optional<TokenPair> result = refreshTokenService.rotate("some-raw-token");

        assertThat(result).isPresent();
        assertThat(result.get().accessToken()).isEqualTo("new-access-jwt");
        assertThat(result.get().refreshToken()).isNotBlank();
        // Presented token was revoked as part of rotation.
        assertThat(active.isRevoked()).isTrue();
        // Two saves: the revoked old token + the newly issued one.
        verify(refreshTokenRepository, times(2)).save(any());
    }

    @Test
    void rotate_shouldReturnEmpty_forUnknownToken() {
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        Optional<TokenPair> result = refreshTokenService.rotate("ghost");

        assertThat(result).isEmpty();
        verify(refreshTokenRepository, never()).save(any());
        verify(jwtUtil, never()).generateToken(any(), any());
    }

    @Test
    void rotate_shouldReturnEmpty_forExpiredToken() {
        RefreshToken expired = new RefreshToken();
        expired.setEmployee(employee);
        expired.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        expired.setRevoked(false);

        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(expired));

        Optional<TokenPair> result = refreshTokenService.rotate("expired-raw");

        assertThat(result).isEmpty();
        verify(refreshTokenRepository, never()).save(any());
        verify(jwtUtil, never()).generateToken(any(), any());
    }

    @Test
    void rotate_shouldRevokeAllSessions_andReturnEmpty_whenRevokedTokenReused() {
        RefreshToken revoked = new RefreshToken();
        revoked.setEmployee(employee);
        revoked.setExpiresAt(LocalDateTime.now().plusDays(1));
        revoked.setRevoked(true); // already revoked → reuse

        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(revoked));

        Optional<TokenPair> result = refreshTokenService.rotate("reused-raw");

        assertThat(result).isEmpty();
        // Reuse detection revokes every active session for the employee.
        verify(refreshTokenRepository).revokeAllForEmployee("emp-1");
        verify(jwtUtil, never()).generateToken(any(), any());
    }

    @Test
    void revoke_shouldMarkActiveTokenRevoked() {
        RefreshToken active = new RefreshToken();
        active.setEmployee(employee);
        active.setExpiresAt(LocalDateTime.now().plusDays(1));
        active.setRevoked(false);

        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(active));
        when(refreshTokenRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        refreshTokenService.revoke("some-raw");

        assertThat(active.isRevoked()).isTrue();
        verify(refreshTokenRepository).save(active);
    }

    @Test
    void revoke_shouldBeIdempotent_forUnknownToken() {
        // The service hashes the raw token before lookup, so stub on anyString().
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        refreshTokenService.revoke("unknown");

        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void revoke_shouldBeIdempotent_forAlreadyRevokedToken() {
        RefreshToken alreadyRevoked = new RefreshToken();
        alreadyRevoked.setEmployee(employee);
        alreadyRevoked.setExpiresAt(LocalDateTime.now().plusDays(1));
        alreadyRevoked.setRevoked(true);
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(alreadyRevoked));

        refreshTokenService.revoke("already");

        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void sameRawToken_hashesDeterministically_soLookupMatchesIssue() {
        // Issue captures the stored hash; rotating with the same raw token must
        // resolve to that same hash (deterministic hashing).
        when(refreshTokenRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        String raw = refreshTokenService.issue(employee);

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        String storedHash = captor.getValue().getTokenHash();

        // Re-issue with same employee produces a different raw token (random),
        // but hashing the FIRST raw token again must equal its stored hash.
        String hashedAgain = (String) ReflectionTestUtils.invokeMethod(refreshTokenService, "hash", raw);
        assertThat(hashedAgain).isEqualTo(storedHash);
    }
}
