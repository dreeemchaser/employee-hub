package employeehub.repository;

import employeehub.domain.Department;
import employeehub.domain.Employee;
import employeehub.domain.Team;
import employeehub.domain.enums.EmploymentStatus;
import employeehub.repository.support.EntityFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;

class EmployeeRepositoryIT extends AbstractRepositoryIT {

    @Autowired
    TestEntityManager em;

    @Autowired
    EmployeeRepository employeeRepository;

    private Department deptA;
    private Department deptB;
    private Team teamA;
    private Team teamB;

    @BeforeEach
    void setUp() {
        deptA = em.persist(EntityFactory.department("Alpha"));
        deptB = em.persist(EntityFactory.department("Bravo"));
        teamA = em.persist(EntityFactory.team("TeamA", deptA));
        teamB = em.persist(EntityFactory.team("TeamB", deptB));

        Employee active = EntityFactory.employee("EMP-001", "a@x.com", deptA, teamA);
        Employee inactive = EntityFactory.employee("EMP-002", "b@x.com", deptA, teamA);
        inactive.setEmploymentStatus(EmploymentStatus.INACTIVE);
        Employee other = EntityFactory.employee("EMP-003", "c@x.com", deptB, teamB);

        em.persist(active);
        em.persist(inactive);
        em.persist(other);
        em.flush();
    }

    @Test
    void findAllFiltered_withNoFilters_returnsAll() {
        Page<Employee> page = employeeRepository.findAllFiltered(null, null, null, PageRequest.of(0, 10));
        assertThat(page.getTotalElements()).isEqualTo(3);
    }

    @Test
    void findAllFiltered_byDepartment_returnsOnlyThatDepartment() {
        Page<Employee> page = employeeRepository.findAllFiltered(deptA.getId(), null, null, PageRequest.of(0, 10));
        assertThat(page.getContent()).extracting(Employee::getEmail)
                .containsExactlyInAnyOrder("a@x.com", "b@x.com");
    }

    @Test
    void findAllFiltered_byStatus_returnsOnlyMatchingStatus() {
        Page<Employee> page = employeeRepository.findAllFiltered(null, null, EmploymentStatus.INACTIVE, PageRequest.of(0, 10));
        assertThat(page.getContent()).extracting(Employee::getEmail).containsExactly("b@x.com");
    }

    @Test
    void findAllFiltered_byDepartmentAndStatus_combinesFilters() {
        Page<Employee> page = employeeRepository.findAllFiltered(
                deptA.getId(), null, EmploymentStatus.ACTIVE, PageRequest.of(0, 10));
        assertThat(page.getContent()).extracting(Employee::getEmail).containsExactly("a@x.com");
    }

    @Test
    void findMaxEmployeeSequence_returnsHighestNumericSuffix() {
        // EMP-001..003 seeded above -> max suffix is 3. Exercises the Postgres
        // CAST(SUBSTRING(...) AS int) expression that H2 would not evaluate identically.
        assertThat(employeeRepository.findMaxEmployeeSequence()).contains(3);
    }
}
