package employeehub.repository;

import employeehub.domain.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, String> {

    Optional<PasswordResetToken> findByToken(String token);

    // Invalidate any still-valid tokens for an employee before issuing a new one,
    // so only the most recent reset link works.
    @Modifying
    @Query("UPDATE PasswordResetToken t SET t.usedAt = CURRENT_TIMESTAMP " +
           "WHERE t.employee.id = :employeeId AND t.usedAt IS NULL")
    void invalidateActiveTokensForEmployee(@Param("employeeId") String employeeId);
}
