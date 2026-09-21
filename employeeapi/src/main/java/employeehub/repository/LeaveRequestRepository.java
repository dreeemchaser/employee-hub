package employeehub.repository;

import employeehub.domain.LeaveRequest;
import employeehub.domain.enums.LeaveStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, String> {

    List<LeaveRequest> findByEmployeeId(String employeeId);
    void deleteByEmployeeId(String employeeId);

    @Query("SELECT lr FROM LeaveRequest lr WHERE " +
           "(:managerId IS NULL OR lr.employee.manager.id = :managerId) AND " +
           "(:status IS NULL OR lr.status = :status)")
    List<LeaveRequest> findAllFiltered(
            @Param("managerId") String managerId,
            @Param("status") LeaveStatus status);

    @Query("SELECT lr FROM LeaveRequest lr WHERE lr.status = 'APPROVED' AND " +
           "(lr.startDate <= :lastDay AND lr.endDate >= :firstDay)")
    List<LeaveRequest> findApprovedInMonth(
            @Param("firstDay") LocalDate firstDay,
            @Param("lastDay") LocalDate lastDay);

    @Query("SELECT lr FROM LeaveRequest lr WHERE lr.status = 'APPROVED' " +
           "AND lr.startDate <= :lastDay AND lr.endDate >= :firstDay " +
           "AND lr.employee.id = :employeeId " +
           "AND (:leaveTypeId IS NULL OR lr.leaveType.id = :leaveTypeId)")
    List<LeaveRequest> findApprovedForEmployeeInMonth(
            @Param("firstDay") LocalDate firstDay, @Param("lastDay") LocalDate lastDay,
            @Param("employeeId") String employeeId, @Param("leaveTypeId") Long leaveTypeId);

    @Query("SELECT lr FROM LeaveRequest lr WHERE lr.status = 'APPROVED' " +
           "AND lr.startDate <= :lastDay AND lr.endDate >= :firstDay " +
           "AND lr.employee.manager.id = :managerId " +
           "AND (:employeeId IS NULL OR lr.employee.id = :employeeId) " +
           "AND (:leaveTypeId IS NULL OR lr.leaveType.id = :leaveTypeId)")
    List<LeaveRequest> findApprovedForManagerInMonth(
            @Param("firstDay") LocalDate firstDay, @Param("lastDay") LocalDate lastDay,
            @Param("managerId") String managerId, @Param("employeeId") String employeeId,
            @Param("leaveTypeId") Long leaveTypeId);

    @Query("SELECT lr FROM LeaveRequest lr WHERE lr.status = 'APPROVED' " +
           "AND lr.startDate <= :lastDay AND lr.endDate >= :firstDay " +
           "AND (:employeeId IS NULL OR lr.employee.id = :employeeId) " +
           "AND (:leaveTypeId IS NULL OR lr.leaveType.id = :leaveTypeId) " +
           "AND (:departmentId IS NULL OR lr.employee.department.id = :departmentId) " +
           "AND (:teamId IS NULL OR lr.employee.team.id = :teamId)")
    List<LeaveRequest> findApprovedForAdminInMonth(
            @Param("firstDay") LocalDate firstDay, @Param("lastDay") LocalDate lastDay,
            @Param("employeeId") String employeeId, @Param("leaveTypeId") Long leaveTypeId,
            @Param("departmentId") Long departmentId, @Param("teamId") Long teamId);

    /**
     * Returns any PENDING or APPROVED requests for the given employee whose date
     * range overlaps [startDate, endDate]. Used to detect duplicate submissions.
     */
    @Query("SELECT lr FROM LeaveRequest lr WHERE lr.employee.id = :employeeId " +
           "AND lr.status IN ('PENDING', 'APPROVED') " +
           "AND lr.startDate <= :endDate AND lr.endDate >= :startDate")
    List<LeaveRequest> findOverlapping(
            @Param("employeeId") String employeeId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);
}
