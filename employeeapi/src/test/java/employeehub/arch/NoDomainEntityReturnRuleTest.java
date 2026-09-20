package employeehub.arch;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.EvaluationResult;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the guard: proves the "no controller returns a domain entity" ArchUnit rule from
 * {@link ArchitectureTest} actually FAILS on a known-bad controller and PASSES on a DTO-returning
 * one. A rule that can never fire would give false confidence. Fixtures are local, so no bad code
 * ships in production.
 */
class NoDomainEntityReturnRuleTest {

    // Apply the production condition directly to the fixtures (they live outside ..controller..,
    // so we target them by class rather than reusing the package-scoped rule).
    private static final ArchRule RULE = methods()
            .that().arePublic()
            .and().areDeclaredInClassesThat().haveSimpleNameEndingWith("Controller")
            .should(ArchitectureTest.notReturnADomainEntity());

    @Test
    void rule_flagsControllerReturningDomainEntity() {
        JavaClasses bad = new ClassFileImporter().importClasses(BadController.class);

        EvaluationResult result = RULE.evaluate(bad);

        assertThat(result.hasViolation()).isTrue();
        assertThat(result.getFailureReport().getDetails())
                .anyMatch(d -> d.contains("BadController") && d.contains("employeehub.domain.Employee"));
    }

    @Test
    void rule_passesControllerReturningDto() {
        JavaClasses good = new ClassFileImporter().importClasses(GoodController.class);

        EvaluationResult result = RULE.evaluate(good);

        assertThat(result.hasViolation()).isFalse();
    }

    // ── Fixtures ─────────────────────────────────────────────────────

    @RestController
    static class BadController {
        public employeehub.dto.ApiResponse<employeehub.domain.Employee> leak() {
            return null;
        }
    }

    @RestController
    static class GoodController {
        public employeehub.dto.ApiResponse<List<String>> safe() {
            return null;
        }
    }
}
