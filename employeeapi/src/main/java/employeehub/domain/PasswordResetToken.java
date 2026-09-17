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
 * A single-use, time-limited token issued for a password reset.
 *
 * <p>A token is valid when it has not expired ({@code expiresAt} in the future)
 * and has not yet been consumed ({@code usedAt} is null). Consuming a token sets
 * {@code usedAt} so it cannot be reused.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
@Table(name = "password_reset_tokens")
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    // The opaque token value sent to the user and presented on reset.
    @Column(nullable = false, unique = true)
    private String token;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "password", "manager"})
    private Employee employee;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    // Null until the token is consumed by a successful reset.
    private LocalDateTime usedAt;

    @CreationTimestamp
    private LocalDateTime createdAt;

    public boolean isUsed() {
        return usedAt != null;
    }

    public boolean isExpired() {
        return expiresAt.isBefore(LocalDateTime.now());
    }

    public boolean isValid() {
        return !isUsed() && !isExpired();
    }
}
