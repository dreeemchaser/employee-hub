package employeehub.repository;

import employeehub.domain.Document;
import employeehub.domain.enums.DocumentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface DocumentRepository extends JpaRepository<Document, String> {
    List<Document> findByEmployeeId(String employeeId);

    // Matches on expiryDate = targetDate exactly (today + thresholdDays), not a
    // range: the reminder job runs daily, so a document at N days out today is
    // at N-1 tomorrow, and this catches it exactly once per threshold as long
    // as the job runs every day. Each threshold checks its own "already sent"
    // column being null, which is the idempotency guard -- a document already
    // reminded at this threshold is excluded automatically on every later run.
    @Query("SELECT d FROM Document d WHERE d.expiryDate = :targetDate AND " +
           "((:thresholdDays = 30 AND d.reminderSentAt30 IS NULL) OR " +
           " (:thresholdDays = 14 AND d.reminderSentAt14 IS NULL) OR " +
           " (:thresholdDays = 7  AND d.reminderSentAt7  IS NULL))")
    List<Document> findDueForReminder(@Param("thresholdDays") int thresholdDays, @Param("targetDate") LocalDate targetDate);

    // Documents that were VERIFIED and whose expiry date is exactly today,
    // that HR has not already been notified about (hrExpiryNotifiedAt IS NULL).
    @Query("SELECT d FROM Document d WHERE d.status = :status AND d.expiryDate = :today AND d.hrExpiryNotifiedAt IS NULL")
    List<Document> findNewlyExpiredVerified(@Param("status") DocumentStatus status, @Param("today") LocalDate today);
}
