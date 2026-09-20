package employeehub.repository;

import employeehub.domain.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface AuditLogRepository extends JpaRepository<AuditLog, String> {

    // The :from/:to timestamp params are cast explicitly: PostgreSQL cannot infer the type of a
    // bind parameter that only ever appears as `:param IS NULL` when the value is null, and fails
    // with "could not determine data type of parameter". The cast gives it the type. (String/UUID
    // params above don't need this — their type is inferable from the column comparison.)
    @Query("SELECT a FROM AuditLog a WHERE " +
           "(:entityType IS NULL OR a.entityType = :entityType) AND " +
           "(:employeeId IS NULL OR a.performedBy.id = :employeeId) AND " +
           "(CAST(:from AS timestamp) IS NULL OR a.timestamp >= :from) AND " +
           "(CAST(:to AS timestamp) IS NULL OR a.timestamp <= :to)")
    Page<AuditLog> findAllFiltered(
            @Param("entityType") String entityType,
            @Param("employeeId") String employeeId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            Pageable pageable);
}
