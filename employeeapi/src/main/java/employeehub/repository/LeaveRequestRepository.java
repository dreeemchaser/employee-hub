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
}
