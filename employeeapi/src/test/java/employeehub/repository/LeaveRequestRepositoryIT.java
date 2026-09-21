package employeehub.repository;

import employeehub.domain.Department;
import employeehub.domain.Employee;
import employeehub.domain.LeaveRequest;
import employeehub.domain.LeaveType;
import employeehub.domain.Team;
import employeehub.domain.enums.LeaveStatus;
import employeehub.repository.support.EntityFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LeaveRequestRepositoryIT extends AbstractRepositoryIT {

    @Autowired
    TestEntityManager em;

    @Autowired
    LeaveRequestRepository leaveRequestRepository;

    private Employee employee;
    private Employee report;      // has manager = employee
    private LeaveType annual;

    @BeforeEach
    void setUp() {
        Department dept = em.persist(EntityFactory.department("Dept"));
        Team team = em.persist(EntityFactory.team("Team", dept));
        annual = em.persist(EntityFactory.leaveType("Annual"));

        employee = EntityFactory.employee("EMP-001", "mgr@x.com", dept, team);
        em.persist(employee);
        report = EntityFactory.employee("EMP-002", "rep@x.com", dept, team);
        report.setManager(employee);
        em.persist(report);
        em.flush();
    }

    // ── findOverlapping ──────────────────────────────────────────────

    @Test
    void findOverlapping_whenRangesOverlap_returnsRequest() {
        persist(employee, LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 15), LeaveStatus.APPROVED);

        List<LeaveRequest> overlapping = leaveRequestRepository.findOverlapping(
                employee.getId(), LocalDate.of(2026, 3, 14), LocalDate.of(2026, 3, 20));

        assertThat(overlapping).hasSize(1);
    }

    @Test
    void findOverlapping_whenRangesTouchAtEndpoint_countsAsOverlap() {
        // Existing ends 2026-03-15; new starts 2026-03-15 — the query uses <=/>= so the shared
        // day is an overlap. Pins that boundary behavior.
        persist(employee, LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 15), LeaveStatus.PENDING);

        List<LeaveRequest> overlapping = leaveRequestRepository.findOverlapping(
                employee.getId(), LocalDate.of(2026, 3, 15), LocalDate.of(2026, 3, 18));

        assertThat(overlapping).hasSize(1);
    }

    @Test
    void findOverlapping_whenRangesDisjoint_returnsEmpty() {
        persist(employee, LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 15), LeaveStatus.APPROVED);

        List<LeaveRequest> overlapping = leaveRequestRepository.findOverlapping(
                employee.getId(), LocalDate.of(2026, 3, 16), LocalDate.of(2026, 3, 20));

        assertThat(overlapping).isEmpty();
    }

    @Test
    void findOverlapping_ignoresCancelledAndRejected() {
        persist(employee, LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 15), LeaveStatus.CANCELLED);

        List<LeaveRequest> overlapping = leaveRequestRepository.findOverlapping(
                employee.getId(), LocalDate.of(2026, 3, 12), LocalDate.of(2026, 3, 14));

        assertThat(overlapping).isEmpty();
    }

    // ── findAllFiltered (managerId, status) ──────────────────────────

    @Test
    void findAllFiltered_withNoFilters_returnsAll() {
        persist(employee, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 2), LeaveStatus.PENDING);
        persist(report, LocalDate.of(2026, 4, 3), LocalDate.of(2026, 4, 4), LeaveStatus.APPROVED);

        assertThat(leaveRequestRepository.findAllFiltered(null, null)).hasSize(2);
    }

    @Test
    void findAllFiltered_byManager_returnsOnlyThatManagersReports() {
        persist(employee, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 2), LeaveStatus.PENDING);
        persist(report, LocalDate.of(2026, 4, 3), LocalDate.of(2026, 4, 4), LeaveStatus.PENDING);

        List<LeaveRequest> byManager = leaveRequestRepository.findAllFiltered(employee.getId(), null);

        // Only 'report' has manager = employee; the manager's own request has no manager link.
        assertThat(byManager).extracting(lr -> lr.getEmployee().getEmail()).containsExactly("rep@x.com");
    }

    @Test
    void findAllFiltered_byStatus_filters() {
        persist(employee, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 2), LeaveStatus.PENDING);
        persist(report, LocalDate.of(2026, 4, 3), LocalDate.of(2026, 4, 4), LeaveStatus.APPROVED);

        assertThat(leaveRequestRepository.findAllFiltered(null, LeaveStatus.APPROVED)).hasSize(1);
    }

    // ── findApprovedInMonth ──────────────────────────────────────────

    @Test
    void findApprovedInMonth_returnsOnlyApprovedIntersectingTheWindow() {
        persist(employee, LocalDate.of(2026, 5, 28), LocalDate.of(2026, 6, 2), LeaveStatus.APPROVED); // spans into June
        persist(report, LocalDate.of(2026, 6, 10), LocalDate.of(2026, 6, 12), LeaveStatus.PENDING);   // not approved
        persist(report, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 3), LeaveStatus.APPROVED);    // July

        List<LeaveRequest> june = leaveRequestRepository.findApprovedInMonth(
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30));

        assertThat(june).hasSize(1);
        assertThat(june.get(0).getStartDate()).isEqualTo(LocalDate.of(2026, 5, 28));
    }

    @Test
    void findApprovedForManagerInMonth_scopesToDirectReportsAndFiltersType() {
        persist(employee, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 2), LeaveStatus.APPROVED);
        persist(report, LocalDate.of(2026, 6, 3), LocalDate.of(2026, 6, 4), LeaveStatus.APPROVED);

        List<LeaveRequest> results = leaveRequestRepository.findApprovedForManagerInMonth(
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30),
                employee.getId(), null, annual.getId());

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getEmployee().getId()).isEqualTo(report.getId());
    }

    private void persist(Employee owner, LocalDate start, LocalDate end, LeaveStatus status) {
        LeaveRequest lr = EntityFactory.leaveRequest(owner, annual, start, end);
        lr.setStatus(status);
        em.persist(lr);
        em.flush();
    }
}
