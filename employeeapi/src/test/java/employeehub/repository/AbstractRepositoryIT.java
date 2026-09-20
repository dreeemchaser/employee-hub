package employeehub.repository;

import org.junit.jupiter.api.BeforeAll;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Base for repository integration tests. Runs against a real PostgreSQL 15 container (matching the
 * production DB major version) rather than H2, whose SQL/JPQL dialect differs — an H2-green test
 * would prove nothing about the Postgres-specific queries (e.g. {@code CAST(SUBSTRING(...) AS int)}).
 *
 * <p>Flyway runs against the fresh container, so {@code V1__baseline.sql} is applied and Hibernate's
 * {@code ddl-auto=validate} runs against the migrated schema — meaning every test in this tier also
 * asserts baseline↔entity parity as a side effect. {@code spring.sql.init} is disabled here so the
 * {@code data.sql} seed does not pollute per-test assertions; each test arranges its own fixtures.
 *
 * <p><b>Singleton container:</b> the container is started once (manually, not via the
 * {@code @Testcontainers}/{@code @Container} extension) and deliberately never stopped, so a single
 * Postgres is shared across every repository test class. The JVM reaps it via Ryuk on exit. Managing
 * it with the extension would stop it after the first class, breaking the {@code @ServiceConnection}
 * for later classes.
 *
 * <p><b>Docker-optional:</b> when no Docker daemon is reachable the whole tier is <em>skipped</em>
 * (JUnit assumption), not failed — so a developer without Docker still gets a green build while CI
 * (which has Docker) runs these for real.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public abstract class AbstractRepositoryIT {

    private static final PostgreSQLContainer<?> POSTGRES;

    static {
        POSTGRES = new PostgreSQLContainer<>("postgres:15-alpine");
        // Only start when Docker is present; otherwise leave it unstarted and let the assumption in
        // requireDocker() skip the tests. Starting is guarded so class initialization never fails.
        if (DockerClientFactory.instance().isDockerAvailable()) {
            POSTGRES.start();
        }
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        // Wire the shared container to the datasource. Guarded reads so property resolution does not
        // fail on a machine without Docker (the tests are skipped there anyway).
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        // Flyway owns the schema; the data.sql seed must NOT run in repository tests so assertions
        // see only the rows each test arranges.
        registry.add("spring.sql.init.mode", () -> "never");
    }

    @BeforeAll
    static void requireDocker() {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable(),
                "Docker is not available — skipping Testcontainers repository tests");
    }
}
