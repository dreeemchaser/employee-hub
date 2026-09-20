package employeehub.repository;

import employeehub.domain.Department;
import employeehub.domain.Employee;
import employeehub.domain.Notification;
import employeehub.domain.RefreshToken;
import employeehub.domain.Team;
import employeehub.domain.enums.NotificationType;
import employeehub.repository.support.EntityFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the {@code @Modifying} bulk-update queries. Bulk updates bypass the persistence context,
 * so each test flushes fixtures, runs the update, then {@code em.clear()}s and re-reads to observe
 * the committed state rather than a stale first-level-cache copy.
 */
class ModifyingQueriesIT extends AbstractRepositoryIT {

    @Autowired
    TestEntityManager em;

    @Autowired
    NotificationRepository notificationRepository;

    @Autowired
    RefreshTokenRepository refreshTokenRepository;

    private Employee alice;
    private Employee bob;

    @BeforeEach
    void setUp() {
        Department dept = em.persist(EntityFactory.department("Dept"));
        Team team = em.persist(EntityFactory.team("Team", dept));
        alice = EntityFactory.employee("EMP-001", "alice@x.com", dept, team);
        em.persist(alice);
        bob = EntityFactory.employee("EMP-002", "bob@x.com", dept, team);
        em.persist(bob);
        em.flush();
    }

    @Test
    void markAllAsRead_marksOnlyTheGivenRecipientsNotifications() {
        persistNotification(alice, false);
        persistNotification(alice, false);
        persistNotification(bob, false);
        em.flush();

        notificationRepository.markAllAsRead(alice.getId());
        em.clear();

        assertThat(notificationRepository.findByRecipientIdOrderByCreatedAtDesc(alice.getId()))
                .allMatch(Notification::getIsRead);
        assertThat(notificationRepository.findByRecipientIdOrderByCreatedAtDesc(bob.getId()))
                .noneMatch(Notification::getIsRead);
    }

    @Test
    void revokeAllForEmployee_revokesOnlyActiveTokensOfThatEmployee() {
        RefreshToken aliceActive = persistToken(alice, "hash-a1", false);
        persistToken(alice, "hash-a2", false);
        RefreshToken bobActive = persistToken(bob, "hash-b1", false);
        em.flush();

        refreshTokenRepository.revokeAllForEmployee(alice.getId());
        em.clear();

        assertThat(refreshTokenRepository.findByTokenHash("hash-a1").orElseThrow().isRevoked()).isTrue();
        assertThat(refreshTokenRepository.findByTokenHash("hash-a2").orElseThrow().isRevoked()).isTrue();
        // Bob's token is untouched.
        assertThat(refreshTokenRepository.findByTokenHash("hash-b1").orElseThrow().isRevoked()).isFalse();
    }

    private void persistNotification(Employee recipient, boolean read) {
        Notification n = new Notification();
        n.setRecipient(recipient);
        n.setTitle("t");
        n.setMessage("m");
        n.setType(NotificationType.GENERAL);
        n.setIsRead(read);
        em.persist(n);
    }

    private RefreshToken persistToken(Employee employee, String hash, boolean revoked) {
        RefreshToken t = new RefreshToken();
        t.setTokenHash(hash);
        t.setEmployee(employee);
        t.setExpiresAt(LocalDateTime.now().plusDays(7));
        t.setRevoked(revoked);
        em.persist(t);
        return t;
    }
}
