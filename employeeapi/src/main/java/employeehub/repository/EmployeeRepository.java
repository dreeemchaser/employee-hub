package employeehub.repository;

import employeehub.domain.Employee;
import employeehub.domain.enums.EmploymentStatus;
import employeehub.domain.enums.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EmployeeRepository extends JpaRepository<Employee, String> {

    boolean existsByEmail(String email);

    Optional<Employee> findByEmail(String email);

    List<Employee> findByRoleIn(List<Role> roles);

    @Query("SELECT e FROM Employee e WHERE " +
           "(:departmentId IS NULL OR e.department.id = :departmentId) AND " +
           "(:teamId IS NULL OR e.team.id = :teamId) AND " +
           "(:status IS NULL OR e.employmentStatus = :status)")
    Page<Employee> findAllFiltered(
            @Param("departmentId") Long departmentId,
            @Param("teamId") Long teamId,
            @Param("status") EmploymentStatus status,
            Pageable pageable);

    @Query("SELECT MAX(CAST(SUBSTRING(e.employeeNumber, 5) AS int)) FROM Employee e WHERE e.employeeNumber LIKE 'EMP-%'")
    Optional<Integer> findMaxEmployeeSequence();
}
