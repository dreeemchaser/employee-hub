package employeehub.dto;

import employeehub.domain.*;
import employeehub.domain.enums.BenefitStatus;
import employeehub.domain.enums.PerformanceReviewStatus;
import employeehub.domain.enums.SalaryIncreaseStatus;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the Tier 1 response DTOs project a safe, flattened view of their
 * entities: employee associations become id + display name, and NO field
 * exposes the underlying Employee entity (so password / idNumber can never
 * serialize through them). This is the security guarantee of the DTO refactor.
 */
class Tier1ResponseDtoTest {

    private Employee employee(String id, String first, String last) {
        Employee e = new Employee();
        e.setId(id);
        e.setFirstName(first);
        e.setLastName(last);
        e.setPassword("$2b$10$secret-hash");
        e.setIdNumber("9001015800086"); // SA ID — must never leak
        return e;
    }

    /** No DTO field may be of type Employee (or expose the entity graph). */
    private void assertNoEmployeeTyped(Object dto) {
        for (Field f : dto.getClass().getDeclaredFields()) {
            assertThat(f.getType())
                    .as("DTO field '%s' must not expose the Employee entity", f.getName())
                    .isNotEqualTo(Employee.class);
        }
    }

    @Test
    void benefitApplicationResponse_flattensEmployees_andExposesNoEntity() {
        Employee emp = employee("emp-1", "Kim", "Lee");
        Employee reviewer = employee("hr-1", "Hanna", "Reed");
        BenefitType type = new BenefitType();
        type.setId(3L);
        type.setName("Medical Aid");

        BenefitApplication a = new BenefitApplication();
        a.setId("app-1");
        a.setEmployee(emp);
        a.setBenefitType(type);
        a.setReviewedBy(reviewer);
        a.setStatus(BenefitStatus.APPROVED);

        BenefitApplicationResponse dto = new BenefitApplicationResponse(a);

        assertThat(dto.getEmployeeId()).isEqualTo("emp-1");
        assertThat(dto.getEmployeeName()).isEqualTo("Kim Lee");
        assertThat(dto.getReviewedById()).isEqualTo("hr-1");
        assertThat(dto.getReviewedByName()).isEqualTo("Hanna Reed");
        assertThat(dto.getBenefitTypeId()).isEqualTo(3L);
        assertThat(dto.getBenefitTypeName()).isEqualTo("Medical Aid");
        assertThat(dto.getStatus()).isEqualTo(BenefitStatus.APPROVED);
        assertNoEmployeeTyped(dto);
    }

    @Test
    void employeeBenefitResponse_flattensEmployee_andExposesNoEntity() {
        Employee emp = employee("emp-2", "Sam", "Ncube");
        BenefitType type = new BenefitType();
        type.setId(1L);
        type.setName("Pension Fund");

        EmployeeBenefit b = new EmployeeBenefit();
        b.setId("eb-1");
        b.setEmployee(emp);
        b.setBenefitType(type);
        b.setStartDate(LocalDate.of(2026, 1, 1));
        b.setStatus(BenefitStatus.ACTIVE);

        EmployeeBenefitResponse dto = new EmployeeBenefitResponse(b);

        assertThat(dto.getEmployeeId()).isEqualTo("emp-2");
        assertThat(dto.getEmployeeName()).isEqualTo("Sam Ncube");
        assertThat(dto.getBenefitTypeName()).isEqualTo("Pension Fund");
        assertThat(dto.getStartDate()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertNoEmployeeTyped(dto);
    }

    @Test
    void salaryIncreaseRequestResponse_flattensEmployees_andExposesNoEntity() {
        Employee emp = employee("emp-3", "Alex", "Smith");
        Employee requestedBy = employee("mgr-1", "Mia", "Brand");

        SalaryIncreaseRequest r = new SalaryIncreaseRequest();
        r.setId("sir-1");
        r.setEmployee(emp);
        r.setRequestedBy(requestedBy);
        r.setCurrentSalary(new BigDecimal("50000"));
        r.setProposedSalary(new BigDecimal("55000"));
        r.setIncreasePercentage(new BigDecimal("10.00"));
        r.setJustification("Strong performance");
        r.setStatus(SalaryIncreaseStatus.PENDING);

        SalaryIncreaseRequestResponse dto = new SalaryIncreaseRequestResponse(r);

        assertThat(dto.getEmployeeId()).isEqualTo("emp-3");
        assertThat(dto.getEmployeeName()).isEqualTo("Alex Smith");
        assertThat(dto.getRequestedByName()).isEqualTo("Mia Brand");
        assertThat(dto.getProposedSalary()).isEqualByComparingTo("55000");
        assertThat(dto.getReviewedById()).isNull();
        assertNoEmployeeTyped(dto);
    }

    @Test
    void performanceGoalResponse_flattensEmployeeAndCycle_andExposesNoEntity() {
        Employee emp = employee("emp-4", "Jo", "Daniels");
        Employee creator = employee("mgr-2", "Pat", "Vega");
        PerformanceCycle cycle = new PerformanceCycle();
        cycle.setId("cyc-1");
        cycle.setName("2026 H1");

        PerformanceGoal g = new PerformanceGoal();
        g.setId("goal-1");
        g.setEmployee(emp);
        g.setCreatedBy(creator);
        g.setCycle(cycle);
        g.setTitle("Ship refresh tokens");
        g.setTargetDate(LocalDate.of(2026, 6, 30));

        PerformanceGoalResponse dto = new PerformanceGoalResponse(g);

        assertThat(dto.getEmployeeName()).isEqualTo("Jo Daniels");
        assertThat(dto.getCreatedByName()).isEqualTo("Pat Vega");
        assertThat(dto.getCycleId()).isEqualTo("cyc-1");
        assertThat(dto.getCycleName()).isEqualTo("2026 H1");
        assertThat(dto.getTitle()).isEqualTo("Ship refresh tokens");
        assertNoEmployeeTyped(dto);
    }

    @Test
    void performanceReviewResponse_flattensEmployees_andExposesNoEntity() {
        Employee emp = employee("emp-5", "Lee", "Adams");
        Employee reviewer = employee("mgr-3", "Sky", "Roman");
        PerformanceCycle cycle = new PerformanceCycle();
        cycle.setId("cyc-2");
        cycle.setName("2026 H2");

        PerformanceReview r = new PerformanceReview();
        r.setId("rev-1");
        r.setEmployee(emp);
        r.setReviewer(reviewer);
        r.setCycle(cycle);
        r.setOverallRating(4);
        r.setStatus(PerformanceReviewStatus.SUBMITTED);

        PerformanceReviewResponse dto = new PerformanceReviewResponse(r);

        assertThat(dto.getEmployeeName()).isEqualTo("Lee Adams");
        assertThat(dto.getReviewerName()).isEqualTo("Sky Roman");
        assertThat(dto.getCycleName()).isEqualTo("2026 H2");
        assertThat(dto.getOverallRating()).isEqualTo(4);
        assertNoEmployeeTyped(dto);
    }
}
