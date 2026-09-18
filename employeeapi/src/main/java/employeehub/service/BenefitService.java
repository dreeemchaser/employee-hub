package employeehub.service;

import employeehub.domain.BenefitApplication;
import employeehub.domain.BenefitType;
import employeehub.domain.Employee;
import employeehub.domain.EmployeeBenefit;
import employeehub.domain.enums.BenefitStatus;
import employeehub.exception.BusinessRuleException;
import employeehub.exception.ResourceNotFoundException;
import employeehub.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BenefitService {

    private final BenefitTypeRepository benefitTypeRepository;
    private final BenefitApplicationRepository applicationRepository;
    private final EmployeeBenefitRepository employeeBenefitRepository;
    private final EmployeeRepository employeeRepository;

    public List<BenefitType> getAllTypes() {
        return benefitTypeRepository.findAll();
    }

    public List<EmployeeBenefit> getMyBenefits(String employeeId) {
        return employeeBenefitRepository.findByEmployeeId(employeeId);
    }

    public BenefitApplication apply(String employeeId, Long benefitTypeId) {
        Employee employee = findEmployee(employeeId);
        BenefitType type = benefitTypeRepository.findById(benefitTypeId)
                .orElseThrow(() -> new ResourceNotFoundException("Benefit type not found: " + benefitTypeId));

        BenefitApplication application = new BenefitApplication();
        application.setEmployee(employee);
        application.setBenefitType(type);
        return applicationRepository.save(application);
    }

    @Transactional
    public BenefitApplication approve(String applicationId, String reviewerId) {
        BenefitApplication application = findApplication(applicationId);
        validatePending(application);

        application.setStatus(BenefitStatus.APPROVED);
        application.setReviewedBy(findEmployee(reviewerId));
        application.setReviewedAt(LocalDateTime.now());
        applicationRepository.save(application);

        // Create active employee benefit
        EmployeeBenefit benefit = new EmployeeBenefit();
        benefit.setEmployee(application.getEmployee());
        benefit.setBenefitType(application.getBenefitType());
        benefit.setStartDate(LocalDate.now());
        benefit.setStatus(BenefitStatus.ACTIVE);
        employeeBenefitRepository.save(benefit);

        return application;
    }

    public BenefitApplication reject(String applicationId, String reviewerId) {
        BenefitApplication application = findApplication(applicationId);
        validatePending(application);

        application.setStatus(BenefitStatus.REJECTED);
        application.setReviewedBy(findEmployee(reviewerId));
        application.setReviewedAt(LocalDateTime.now());
        return applicationRepository.save(application);
    }

    private void validatePending(BenefitApplication application) {
        if (application.getStatus() != BenefitStatus.PENDING) {
            throw new BusinessRuleException("Application is no longer pending");
        }
    }

    private BenefitApplication findApplication(String id) {
        return applicationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Benefit application not found: " + id));
    }

    private Employee findEmployee(String id) {
        return employeeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found: " + id));
    }
}
