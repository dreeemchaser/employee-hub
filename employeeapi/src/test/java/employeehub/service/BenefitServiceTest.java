package employeehub.service;

import employeehub.domain.*;
import employeehub.domain.enums.BenefitStatus;
import employeehub.exception.BusinessRuleException;
import employeehub.exception.ResourceNotFoundException;
import employeehub.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BenefitServiceTest {

    @Mock BenefitTypeRepository benefitTypeRepository;
    @Mock BenefitApplicationRepository applicationRepository;
    @Mock EmployeeBenefitRepository employeeBenefitRepository;
    @Mock EmployeeRepository employeeRepository;

    @InjectMocks BenefitService benefitService;

    private Employee employee;
    private Employee hrAdmin;
    private BenefitType benefitType;
    private BenefitApplication application;

    @BeforeEach
    void setUp() {
        employee = new Employee();
        employee.setId("emp-1");

        hrAdmin = new Employee();
        hrAdmin.setId("hr-1");

        benefitType = new BenefitType();
        benefitType.setId(1L);
        benefitType.setName("Medical Aid");
        benefitType.setEmployeeContribution(BigDecimal.valueOf(1500));
        benefitType.setEmployerContribution(BigDecimal.valueOf(2000));

        application = new BenefitApplication();
        application.setId("app-1");
        application.setEmployee(employee);
        application.setBenefitType(benefitType);
        application.setStatus(BenefitStatus.PENDING);
    }

    @Test
    void apply_shouldCreatePendingApplication() {
        when(employeeRepository.findById("emp-1")).thenReturn(Optional.of(employee));
        when(benefitTypeRepository.findById(1L)).thenReturn(Optional.of(benefitType));
        when(applicationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        BenefitApplication result = benefitService.apply("emp-1", 1L);

        assertThat(result.getStatus()).isEqualTo(BenefitStatus.PENDING);
        assertThat(result.getEmployee()).isEqualTo(employee);
    }

    @Test
    void apply_shouldThrow_whenBenefitTypeNotFound() {
        when(employeeRepository.findById("emp-1")).thenReturn(Optional.of(employee));
        when(benefitTypeRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> benefitService.apply("emp-1", 99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void approve_shouldSetApprovedAndCreateEmployeeBenefit() {
        when(applicationRepository.findById("app-1")).thenReturn(Optional.of(application));
        when(employeeRepository.findById("hr-1")).thenReturn(Optional.of(hrAdmin));
        when(applicationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        BenefitApplication result = benefitService.approve("app-1", "hr-1");

        assertThat(result.getStatus()).isEqualTo(BenefitStatus.APPROVED);
        assertThat(result.getReviewedBy()).isEqualTo(hrAdmin);
        verify(employeeBenefitRepository).save(any());
    }

    @Test
    void approve_shouldThrow_whenNotPending() {
        application.setStatus(BenefitStatus.APPROVED);
        when(applicationRepository.findById("app-1")).thenReturn(Optional.of(application));

        assertThatThrownBy(() -> benefitService.approve("app-1", "hr-1"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("no longer pending");
    }

    @Test
    void reject_shouldSetRejectedStatus() {
        when(applicationRepository.findById("app-1")).thenReturn(Optional.of(application));
        when(employeeRepository.findById("hr-1")).thenReturn(Optional.of(hrAdmin));
        when(applicationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        BenefitApplication result = benefitService.reject("app-1", "hr-1");

        assertThat(result.getStatus()).isEqualTo(BenefitStatus.REJECTED);
        verify(employeeBenefitRepository, never()).save(any());
    }
}
