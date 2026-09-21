package employeehub.repository;

import employeehub.domain.Timesheet;
import employeehub.domain.enums.TimesheetStatus;
import employeehub.repository.support.DepartmentCountProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TimesheetRepository extends JpaRepository<Timesheet, String> {

    List<Timesheet> findByEmployeeId(String employeeId);

    @Query("SELECT t FROM Timesheet t WHERE " +
           "(:managerId IS NULL OR t.employee.manager.id = :managerId)")
    List<Timesheet> findAllFiltered(@Param("managerId") String managerId);

    @Query("SELECT t.employee.department.name AS departmentName, COUNT(t) AS count " +
           "FROM Timesheet t WHERE t.status = :status " +
           "GROUP BY t.employee.department.name")
    List<DepartmentCountProjection> countByDepartmentAndStatus(@Param("status") TimesheetStatus status);
}
