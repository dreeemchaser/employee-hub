package employeehub.arch;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaParameterizedType;
import com.tngtech.archunit.core.domain.JavaType;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.CacheMode;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING;

/**
 * Enforces the layering and coding rules stated in {@code coding-best-practices.md}. These were
 * "enforced by an ArchUnit test" on paper but not in code until this slice.
 *
 * <p>Known exceptions are encoded explicitly and each carries a comment tying it to the
 * "Known exceptions" list in {@code coding-best-practices.md}. Any new exception must be added in
 * both places.
 */
@AnalyzeClasses(
        packages = "employeehub",
        // Analyze production code only — test classes (e.g. concrete repository ITs living in the
        // employeehub.repository package) must not trip the layering rules.
        importOptions = ImportOption.DoNotIncludeTests.class,
        cacheMode = CacheMode.PER_CLASS)
class ArchitectureTest {

    // ── Layering ─────────────────────────────────────────────────────

    @ArchTest
    static final ArchRule controllers_do_not_access_repositories =
            noClasses().that().resideInAPackage("..controller..")
                    // Known exception: DashboardController queries repositories directly for a
                    // read-only aggregation shortcut (coding-best-practices.md "Known exceptions").
                    .and().doNotHaveSimpleName("DashboardController")
                    .should().dependOnClassesThat().resideInAPackage("..repository..")
                    .because("controllers must call services, not repositories");

    @ArchTest
    static final ArchRule services_do_not_depend_on_controllers =
            noClasses().that().resideInAPackage("..service..")
                    .should().dependOnClassesThat().resideInAPackage("..controller..")
                    .because("dependencies only flow downward: controller -> service -> repository");

    @ArchTest
    static final ArchRule services_and_below_do_not_touch_servlet_api =
            noClasses().that().resideInAnyPackage("..service..", "..repository..", "..domain..")
                    .should().dependOnClassesThat().resideInAPackage("jakarta.servlet..")
                    .because("web-layer concerns must not leak below the controller layer");

    @ArchTest
    static final ArchRule repositories_are_interfaces =
            classes().that().resideInAPackage("..repository..")
                    .should().beInterfaces()
                    .because("repositories are Spring Data JPA interfaces — no implementation classes");

    @ArchTest
    static final ArchRule transactional_only_in_service_layer =
            noMethods().that().areDeclaredInClassesThat().resideOutsideOfPackage("..service..")
                    .should().beAnnotatedWith(Transactional.class)
                    .because("transaction boundaries live in the service layer only");

    // ── Coding rules ─────────────────────────────────────────────────

    @ArchTest
    static final ArchRule no_standard_streams =
            NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS
                    .because("use @Slf4j logging, never System.out/System.err");

    @ArchTest
    static final ArchRule no_java_util_logging =
            NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING
                    .because("use SLF4J (@Slf4j), not java.util.logging");

    // ── DTO contract: no domain entity in a controller return ────────

    /**
     * Reference/lookup entities that are intentionally still returned raw by their controllers
     * (simple value-like tables, no lazy {@code Employee}, no PII). Excluded from the rule below and
     * tracked as known exceptions in {@code coding-best-practices.md}. The hardening spec migrated
     * only the {@code Employee}-embedding / PII-bearing entities to DTOs.
     */
    private static final Set<String> ALLOWED_RAW_ENTITY_RETURNS = Set.of(
            "employeehub.domain.Department",
            "employeehub.domain.Team",
            "employeehub.domain.BenefitType",
            "employeehub.domain.PerformanceCycle");

    @ArchTest
    static final ArchRule controllers_do_not_return_domain_entities =
            methods().that().areDeclaredInClassesThat().resideInAPackage("..controller..")
                    .and().arePublic()
                    .should(notReturnADomainEntity())
                    .because("controller responses must be DTOs, never domain entities "
                            + "(leaks fields, Hibernate proxies, and couples the API to the schema)");

    // Package-visible so NoDomainEntityReturnRuleTest can exercise the condition directly against
    // fixtures that live outside the ..controller.. package.
    static ArchCondition<JavaMethod> notReturnADomainEntity() {
        return new ArchCondition<>("not return a domain entity (directly or as a generic type argument)") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                JavaType returnType = method.getReturnType();
                String offending = firstDomainEntity(returnType);
                if (offending != null && !ALLOWED_RAW_ENTITY_RETURNS.contains(offending)) {
                    events.add(SimpleConditionEvent.violated(method,
                            method.getFullName() + " returns domain entity " + offending));
                }
            }
        };
    }

    /**
     * Walks a return type and its generic arguments (e.g. {@code ResponseEntity<ApiResponse<Employee>>},
     * {@code ApiResponse<Page<Employee>>}) and returns the first {@code employeehub.domain.*} type found,
     * or null if none.
     */
    private static String firstDomainEntity(JavaType type) {
        JavaClass raw = type.toErasure();
        String name = raw.getFullName();
        // Entities live directly in employeehub.domain; enums live in employeehub.domain.enums and
        // are fine to return (they are simple values, not entities).
        if (name.startsWith("employeehub.domain.")
                && !name.startsWith("employeehub.domain.enums.")
                && !raw.isEnum()) {
            return name;
        }
        if (type instanceof JavaParameterizedType parameterized) {
            for (JavaType argument : parameterized.getActualTypeArguments()) {
                String nested = firstDomainEntity(argument);
                if (nested != null) {
                    return nested;
                }
            }
        }
        return null;
    }
}
