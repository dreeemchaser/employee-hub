package employeehub.repository;

import employeehub.domain.Department;
import employeehub.domain.Employee;
import employeehub.domain.Team;
import employeehub.domain.Timesheet;
import employeehub.domain.enums.TimesheetStatus;
import employeehub.repository.support.DepartmentCountProjection;
import employeehub.repository.support.EntityFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TimesheetRepositoryIT extends AbstractRepositoryIT {

    @Autowired
    TestEntityManager em;

    @Autowired
    TimesheetRepository timesheetRepository;

    private Employee manager;
    private Employee report;
    private Employee unmanaged;
    private Department dept;

    @BeforeEach
    void setUp() {
        dept = em.persist(EntityFactory.department("Dept"));
        Team team = em.persist(EntityFactory.team("Team", dept));

        manager = EntityFactory.employee("EMP-001", "mgr@x.com", dept, team);
        em.persist(manager);
        report = EntityFactory.employee("EMP-002", "rep@x.com", dept, team);
        report.setManager(manager);
        em.persist(report);
        unmanaged = EntityFactory.employee("EMP-003", "solo@x.com", dept, team);
        em.persist(unmanaged);

        persist(report);
        persist(unmanaged);
        em.flush();
    }

    @Test
    void findAllFiltered_withNoManager_returnsAll() {
        List<Timesheet> all = timesheetRepository.findAllFiltered(null);
        assertThat(all).hasSize(2);
    }

    @Test
    void findAllFiltered_byManager_returnsOnlyThatManagersReports() {
        List<Timesheet> byManager = timesheetRepository.findAllFiltered(manager.getId());
        assertThat(byManager).extracting(t -> t.getEmployee().getEmail()).containsExactly("rep@x.com");
    }

    @Test
    void findByEmployeeId_returnsOwnersTimesheets() {
        assertThat(timesheetRepository.findByEmployeeId(report.getId())).hasSize(1);
        assertThat(timesheetRepository.findByEmployeeId(unmanaged.getId())).hasSize(1);
    }

    // ── countByDepartmentAndStatus ────────────────────────────────────

    @Test
    void countByDepartmentAndStatus_groupsByEmployeesDepartment_forMatchingStatusOnly() {
        // A second department with its own employee + a SUBMITTED timesheet.
        Department otherDept = em.persist(EntityFactory.department("Other"));
        Team otherTeam = em.persist(EntityFactory.team("OtherTeam", otherDept));
        Employee otherEmp = EntityFactory.employee("EMP-004", "other@x.com", otherDept, otherTeam);
        em.persist(otherEmp);

        // Two SUBMITTED in "Dept", one SUBMITTED in "Other"; the two DRAFT rows from setUp() are ignored.
        persist(report, TimesheetStatus.SUBMITTED);
        persist(unmanaged, TimesheetStatus.SUBMITTED);
        persist(otherEmp, TimesheetStatus.SUBMITTED);
        em.flush();

        List<DepartmentCountProjection> counts =
                timesheetRepository.countByDepartmentAndStatus(TimesheetStatus.SUBMITTED);

        assertThat(counts)
                .extracting(DepartmentCountProjection::getDepartmentName, DepartmentCountProjection::getCount)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("Dept", 2L),
                        org.assertj.core.groups.Tuple.tuple("Other", 1L));
    }

    @Test
    void countByDepartmentAndStatus_whenNoTimesheetsMatchStatus_returnsEmpty() {
        // Only DRAFT rows exist from setUp(); nothing SUBMITTED.
        List<DepartmentCountProjection> counts =
                timesheetRepository.countByDepartmentAndStatus(TimesheetStatus.SUBMITTED);

        assertThat(counts).isEmpty();
    }

    private void persist(Employee owner) {
        persist(owner, TimesheetStatus.DRAFT);
    }

    private void persist(Employee owner, TimesheetStatus status) {
        Timesheet ts = new Timesheet();
        ts.setEmployee(owner);
        ts.setWeekStartDate(LocalDate.of(2026, 3, 2));
        ts.setWeekEndDate(LocalDate.of(2026, 3, 8));
        ts.setStatus(status);
        em.persist(ts);
    }
}
