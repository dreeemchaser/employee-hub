package employeehub.service;

import employeehub.domain.Employee;
import employeehub.domain.RefreshToken;
import employeehub.dto.TokenPair;
import employeehub.repository.RefreshTokenRepository;
import employeehub.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;

/**
 * Issues, rotates, and revokes long-lived refresh tokens.
 *
 * <p>Raw token values are high-entropy random strings that only ever exist in
 * the login/refresh response — the database stores their SHA-256 hash. Each use
 * is rotated: the presented token is revoked and a new one issued. Presenting a
 * token that has already been revoked is treated as a compromise signal and
 * revokes all of the owner's active tokens.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final int TOKEN_BYTES = 32; // 256 bits of entropy
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtUtil jwtUtil;

    @Value("${jwt.refresh-expiration}")
    private long refreshExpirationMs;

    /**
     * Issue a new refresh token for the employee, persisting only its hash, and
     * return the raw token (the only place it exists in plaintext).
     */
    @Transactional
    public String issue(Employee employee) {
        String rawToken = generateRawToken();

        RefreshToken entity = new RefreshToken();
        entity.setTokenHash(hash(rawToken));
        entity.setEmployee(employee);
        entity.setExpiresAt(LocalDateTime.now().plusNanos(refreshExpirationMs * 1_000_000));
        refreshTokenRepository.save(entity);

        return rawToken;
    }

    /**
     * Rotate a refresh token: validate the presented token, then revoke it and
     * issue a fresh access + refresh pair.
     *
     * @return the new pair, or empty if the presented token is unknown, expired,
     * or already revoked (reuse). On reuse, all of the owner's active tokens are
     * revoked before returning empty.
     */
    @Transactional
    public Optional<TokenPair> rotate(String rawRefreshToken) {
        Optional<RefreshToken> found = refreshTokenRepository.findByTokenHash(hash(rawRefreshToken));
        if (found.isEmpty()) {
            return Optional.empty();
        }

        RefreshToken token = found.get();

        // Reuse of an already-revoked token → assume compromise, kill all sessions.
        if (token.isRevoked()) {
            log.warn("Refresh token reuse detected for employee id={}; revoking all sessions",
                    token.getEmployee().getId());
            refreshTokenRepository.revokeAllForEmployee(token.getEmployee().getId());
            return Optional.empty();
        }

        if (token.isExpired()) {
            return Optional.empty();
        }

        // Rotate: revoke the presented token, issue a new one.
        token.setRevoked(true);
        refreshTokenRepository.save(token);

        Employee employee = token.getEmployee();
        String accessToken = jwtUtil.generateToken(employee.getEmail(), employee.getRole().name());
        String newRefreshToken = issue(employee);

        return Optional.of(new TokenPair(accessToken, newRefreshToken));
    }

    /**
     * Revoke a refresh token (logout). Idempotent — a no-op if the token is
     * unknown or already revoked.
     */
    @Transactional
    public void revoke(String rawRefreshToken) {
        refreshTokenRepository.findByTokenHash(hash(rawRefreshToken))
                .filter(token -> !token.isRevoked())
                .ifPresent(token -> {
                    token.setRevoked(true);
                    refreshTokenRepository.save(token);
                });
    }

    private String generateRawToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    // Deterministic SHA-256 so lookup by hash works. Entropy comes from the
    // 256-bit random token, not from a salt/KDF.
    private String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hashed);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed present on every JVM; this cannot happen.
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
