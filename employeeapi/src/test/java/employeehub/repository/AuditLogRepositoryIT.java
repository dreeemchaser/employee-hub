package employeehub.repository;

import employeehub.domain.AuditLog;
import employeehub.domain.Department;
import employeehub.domain.Employee;
import employeehub.domain.Team;
import employeehub.repository.support.EntityFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class AuditLogRepositoryIT extends AbstractRepositoryIT {

    @Autowired
    TestEntityManager em;

    @Autowired
    AuditLogRepository auditLogRepository;

    private Employee actor;

    @BeforeEach
    void setUp() {
        Department dept = em.persist(EntityFactory.department("Dept"));
        Team team = em.persist(EntityFactory.team("Team", dept));
        actor = EntityFactory.employee("EMP-001", "actor@x.com", dept, team);
        em.persist(actor);

        persist("Employee", actor, LocalDateTime.of(2026, 1, 10, 9, 0));
        persist("Leave", actor, LocalDateTime.of(2026, 2, 10, 9, 0));
        persist("Leave", null, LocalDateTime.of(2026, 3, 10, 9, 0));
        em.flush();
    }

    @Test
    void findAllFiltered_withNoFilters_returnsAll() {
        Page<AuditLog> page = auditLogRepository.findAllFiltered(null, null, null, null, PageRequest.of(0, 10));
        assertThat(page.getTotalElements()).isEqualTo(3);
    }

    @Test
    void findAllFiltered_byEntityType_filters() {
        Page<AuditLog> page = auditLogRepository.findAllFiltered("Leave", null, null, null, PageRequest.of(0, 10));
        assertThat(page.getTotalElements()).isEqualTo(2);
    }

    @Test
    void findAllFiltered_byEmployee_filters() {
        Page<AuditLog> page = auditLogRepository.findAllFiltered(null, actor.getId(), null, null, PageRequest.of(0, 10));
        assertThat(page.getTotalElements()).isEqualTo(2);
    }

    @Test
    void findAllFiltered_byDateRange_filters() {
        Page<AuditLog> page = auditLogRepository.findAllFiltered(
                null, null,
                LocalDateTime.of(2026, 2, 1, 0, 0),
                LocalDateTime.of(2026, 2, 28, 23, 59),
                PageRequest.of(0, 10));
        assertThat(page.getTotalElements()).isEqualTo(1);
    }

    private void persist(String entityType, Employee performedBy, LocalDateTime when) {
        AuditLog log = new AuditLog();
        log.setAction("UPDATE");
        log.setEntityType(entityType);
        log.setPerformedBy(performedBy);
        log.setTimestamp(when);
        em.persist(log);
    }
}
