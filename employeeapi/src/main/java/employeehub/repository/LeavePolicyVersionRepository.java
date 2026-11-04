package employeehub.repository;

import employeehub.domain.LeavePolicyVersion;
import employeehub.domain.enums.PolicyStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface LeavePolicyVersionRepository extends JpaRepository<LeavePolicyVersion, String> {

    List<LeavePolicyVersion> findByLeaveTypeIdOrderByVersionNumberDesc(Long leaveTypeId);

    @Query("SELECT p FROM LeavePolicyVersion p WHERE p.leaveType.id = :leaveTypeId " +
           "AND p.status = :status " +
           "AND p.effectiveFrom <= :onDate AND (p.effectiveTo IS NULL OR p.effectiveTo >= :onDate)")
    Optional<LeavePolicyVersion> findEffective(
            @Param("leaveTypeId") Long leaveTypeId,
            @Param("status") PolicyStatus status,
            @Param("onDate") LocalDate onDate);

    Page<LeavePolicyVersion> findByStatus(PolicyStatus status, Pageable pageable);

    boolean existsByLeaveTypeIdAndStatus(Long leaveTypeId, PolicyStatus status);

    @Query("SELECT COUNT(p) > 0 FROM LeavePolicyVersion p WHERE p.leaveType.id = :leaveTypeId " +
           "AND p.status = employeehub.domain.enums.PolicyStatus.ACTIVE " +
           "AND (:excludeId IS NULL OR p.id <> :excludeId) " +
           "AND p.effectiveFrom <= COALESCE(:effectiveTo, p.effectiveFrom) " +
           "AND (p.effectiveTo IS NULL OR p.effectiveTo >= :effectiveFrom)")
    boolean hasOverlappingVersion(
            @Param("leaveTypeId") Long leaveTypeId,
            @Param("effectiveFrom") LocalDate effectiveFrom,
            @Param("effectiveTo") LocalDate effectiveTo,
            @Param("excludeId") String excludeId);
}
