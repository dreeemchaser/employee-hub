package employeehub.repository;

import employeehub.domain.AttendanceRecord;
import employeehub.domain.enums.AttendanceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, String> {

    Optional<AttendanceRecord> findByEmployeeIdAndStatus(String employeeId, AttendanceStatus status);

    Page<AttendanceRecord> findByEmployeeId(String employeeId, Pageable pageable);

    // Nullable-filter pattern (see steering): managerId is null for HR/SUPER_ADMIN callers, in which
    // case the filter is a no-op and every employee's records are visible. A MANAGER's own id scopes
    // the result to their direct reports, mirroring TimesheetRepository.findAllFiltered.
    @Query("SELECT a FROM AttendanceRecord a WHERE " +
           "(:managerId IS NULL OR a.employee.manager.id = :managerId) AND " +
           "(:employeeId IS NULL OR a.employee.id = :employeeId) AND " +
           "(CAST(:from AS date) IS NULL OR a.workDate >= :from) AND " +
           "(CAST(:to AS date) IS NULL OR a.workDate <= :to)")
    Page<AttendanceRecord> findAllFiltered(
            @Param("managerId") String managerId,
            @Param("employeeId") String employeeId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            Pageable pageable);
}
