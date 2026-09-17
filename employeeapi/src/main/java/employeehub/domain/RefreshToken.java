package employeehub.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * A long-lived, server-tracked refresh token used to mint new access tokens.
 *
 * <p>Unlike {@link PasswordResetToken}, the raw token value is never stored — only
 * its SHA-256 hash ({@code tokenHash}). Lookup is by hash, so a database leak does
 * not expose usable tokens. A token is usable when it is not revoked and not
 * expired; each successful use is rotated (the presented token is revoked and a
 * fresh one issued).
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
@Table(name = "refresh_tokens")
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    // SHA-256 hash of the raw token. The raw value only ever exists in the
    // login/refresh response body — never persisted or logged.
    @Column(nullable = false, unique = true)
    private String tokenHash;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "password", "manager"})
    private Employee employee;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    // Set true on rotation (token was used), logout, or reuse-detection revoke-all.
    @Column(nullable = false)
    private boolean revoked = false;

    @CreationTimestamp
    private LocalDateTime createdAt;

    public boolean isExpired() {
        return expiresAt.isBefore(LocalDateTime.now());
    }

    public boolean isActive() {
        return !revoked && !isExpired();
    }
}
