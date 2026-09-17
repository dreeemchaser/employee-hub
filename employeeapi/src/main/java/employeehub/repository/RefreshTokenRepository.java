package employeehub.repository;

import employeehub.domain.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, String> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    // Revoke every still-active token for an employee. Used on reuse detection
    // (a revoked token presented again → assume compromise, kill all sessions).
    @Modifying
    @Query("UPDATE RefreshToken t SET t.revoked = true " +
           "WHERE t.employee.id = :employeeId AND t.revoked = false")
    void revokeAllForEmployee(@Param("employeeId") String employeeId);
}
