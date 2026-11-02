package employeehub.repository;

import employeehub.domain.Employee;
import employeehub.domain.enums.EmploymentStatus;
import employeehub.domain.enums.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
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

    @Query("SELECT e FROM Employee e WHERE e.manager IS NULL AND " +
           "(:departmentId IS NULL OR e.department.id = :departmentId) AND " +
           "(:teamId IS NULL OR e.team.id = :teamId) AND " +
           "(:status IS NULL OR e.employmentStatus = :status)")
    Page<Employee> findOrganizationRoots(
            @Param("departmentId") Long departmentId,
            @Param("teamId") Long teamId,
            @Param("status") EmploymentStatus status,
            Pageable pageable);

    @Query("SELECT e FROM Employee e WHERE " +
           "(:query IS NULL OR LOWER(CONCAT(e.firstName, ' ', e.lastName)) LIKE :query " +
           "OR LOWER(e.jobTitle) LIKE :query OR LOWER(e.employeeNumber) LIKE :query) AND " +
           "(:departmentId IS NULL OR e.department.id = :departmentId) AND " +
           "(:teamId IS NULL OR e.team.id = :teamId) AND " +
           "(:status IS NULL OR e.employmentStatus = :status)")
    Page<Employee> searchOrganization(
            @Param("query") String query,
            @Param("departmentId") Long departmentId,
            @Param("teamId") Long teamId,
            @Param("status") EmploymentStatus status,
            Pageable pageable);

    @Query("SELECT e FROM Employee e WHERE " +
           "(e.id = :employeeId OR e.manager.id = :employeeId OR e.id = " +
           "(SELECT c.manager.id FROM Employee c WHERE c.id = :employeeId)) AND " +
           "(:query IS NULL OR LOWER(CONCAT(e.firstName, ' ', e.lastName)) LIKE :query " +
           "OR LOWER(e.jobTitle) LIKE :query OR LOWER(e.employeeNumber) LIKE :query) AND " +
           "(:departmentId IS NULL OR e.department.id = :departmentId) AND " +
           "(:teamId IS NULL OR e.team.id = :teamId) AND " +
           "(:status IS NULL OR e.employmentStatus = :status)")
    Page<Employee> findEmployeeContext(
            @Param("employeeId") String employeeId,
            @Param("query") String query,
            @Param("departmentId") Long departmentId,
            @Param("teamId") Long teamId,
            @Param("status") EmploymentStatus status,
            Pageable pageable);

    @Query(value = """
            WITH RECURSIVE descendants AS (
                SELECT e.* FROM employees e WHERE e.manager_id = :managerId
                UNION ALL
                SELECT child.* FROM employees child
                JOIN descendants parent ON child.manager_id = parent.id
            )
            SELECT e.* FROM descendants e
            WHERE (:query IS NULL OR LOWER(CONCAT(e.first_name, ' ', e.last_name)) LIKE :query
                   OR LOWER(e.job_title) LIKE :query OR LOWER(e.employee_number) LIKE :query)
              AND (:departmentId IS NULL OR e.department_id = :departmentId)
              AND (:teamId IS NULL OR e.team_id = :teamId)
              AND (:status IS NULL OR e.employment_status = :status)
            """,
            countQuery = """
            WITH RECURSIVE descendants AS (
                SELECT e.* FROM employees e WHERE e.manager_id = :managerId
                UNION ALL
                SELECT child.* FROM employees child
                JOIN descendants parent ON child.manager_id = parent.id
            )
            SELECT COUNT(*) FROM descendants e
            WHERE (:query IS NULL OR LOWER(CONCAT(e.first_name, ' ', e.last_name)) LIKE :query
                   OR LOWER(e.job_title) LIKE :query OR LOWER(e.employee_number) LIKE :query)
              AND (:departmentId IS NULL OR e.department_id = :departmentId)
              AND (:teamId IS NULL OR e.team_id = :teamId)
              AND (:status IS NULL OR e.employment_status = :status)
            """,
            nativeQuery = true)
    Page<Employee> searchDescendants(
            @Param("managerId") String managerId,
            @Param("query") String query,
            @Param("departmentId") Long departmentId,
            @Param("teamId") Long teamId,
            @Param("status") EmploymentStatus status,
            Pageable pageable);

    List<Employee> findByManager_IdOrderByLastNameAscFirstNameAsc(String managerId);

    List<Employee> findByManager_IdIn(Collection<String> managerIds);

    @Query(value = """
            SELECT EXISTS (
                WITH RECURSIVE descendants AS (
                    SELECT e.id FROM employees e WHERE e.manager_id = :managerId
                    UNION ALL
                    SELECT child.id FROM employees child
                    JOIN descendants parent ON child.manager_id = parent.id
                )
                SELECT 1 FROM descendants WHERE id = :employeeId
            )
            """, nativeQuery = true)
    boolean existsInManagerScope(
            @Param("managerId") String managerId,
            @Param("employeeId") String employeeId);

    @Query("SELECT MAX(CAST(SUBSTRING(e.employeeNumber, 5) AS int)) FROM Employee e WHERE e.employeeNumber LIKE 'EMP-%'")
    Optional<Integer> findMaxEmployeeSequence();
}
