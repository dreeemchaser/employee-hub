package employeehub.service;

import employeehub.domain.*;
import employeehub.domain.enums.SalaryIncreaseStatus;
import employeehub.domain.enums.NotificationType;
import employeehub.dto.PaySlipGenerateRequest;
import employeehub.dto.SalaryIncreaseRequestDto;
import employeehub.dto.SalaryRecordRequest;
import employeehub.exception.ResourceNotFoundException;
import employeehub.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SalaryService {

    private static final BigDecimal UIF_RATE = new BigDecimal("0.01");
    private static final BigDecimal UIF_MONTHLY_CAP = new BigDecimal("177.12");

    private final SalaryRecordRepository salaryRecordRepository;
    private final PaySlipRepository paySlipRepository;
    private final SalaryIncreaseRequestRepository increaseRequestRepository;
    private final TaxBracketRepository taxBracketRepository;
    private final EmployeeRepository employeeRepository;
    private final NotificationService notificationService;
    private final AuditService auditService;

    // ── Salary Records ───────────────────────────────────────────────

    @Transactional
    public SalaryRecord createRecord(SalaryRecordRequest req, String createdById) {
        Employee employee = findEmployee(req.getEmployeeId());
        Employee createdBy = findEmployee(createdById);

        // Close previous record
        salaryRecordRepository.findFirstByEmployeeIdOrderByEffectiveDateDesc(req.getEmployeeId())
                .ifPresent(prev -> {
                    prev.setEndDate(req.getEffectiveDate().minusDays(1));
                    salaryRecordRepository.save(prev);
                });

        SalaryRecord record = new SalaryRecord();
        record.setEmployee(employee);
        record.setBasicSalary(req.getBasicSalary());
        record.setEffectiveDate(req.getEffectiveDate());
        record.setCreatedBy(createdBy);
        SalaryRecord saved = salaryRecordRepository.save(record);
        auditService.log(createdBy, "CREATE", "SalaryRecord", saved.getId(), null, req.getBasicSalary().toString());
        return saved;
    }

    public List<SalaryRecord> getSalaryHistory(String employeeId) {
        return salaryRecordRepository.findByEmployeeIdOrderByEffectiveDateDesc(employeeId);
    }

    // ── Payslips ─────────────────────────────────────────────────────

    public PaySlip generatePaySlip(PaySlipGenerateRequest req, String generatedById) {
        SalaryRecord record = salaryRecordRepository
                .findFirstByEmployeeIdOrderByEffectiveDateDesc(req.getEmployeeId())
                .orElseThrow(() -> new ResourceNotFoundException("No salary record found for employee"));

        BigDecimal monthly = record.getBasicSalary();
        BigDecimal annual = monthly.multiply(BigDecimal.valueOf(12));
        int taxYear = Year.now().getValue();

        BigDecimal paye = calculateMonthlyPaye(annual, taxYear);
        BigDecimal uif = monthly.multiply(UIF_RATE).min(UIF_MONTHLY_CAP).setScale(2, RoundingMode.HALF_UP);

        BigDecimal medicalAid = orZero(req.getMedicalAid());
        BigDecimal pensionFund = orZero(req.getPensionFund());
        BigDecimal otherDeductions = orZero(req.getOtherDeductions());

        BigDecimal totalDeductions = paye.add(uif).add(medicalAid).add(pensionFund).add(otherDeductions);
        BigDecimal netSalary = monthly.subtract(totalDeductions).setScale(2, RoundingMode.HALF_UP);

        PaySlip slip = new PaySlip();
        slip.setEmployee(findEmployee(req.getEmployeeId()));
        slip.setMonth(req.getMonth());
        slip.setBasicSalary(monthly);
        slip.setGrossSalary(monthly);
        slip.setPaye(paye);
        slip.setUif(uif);
        slip.setMedicalAid(medicalAid);
        slip.setPensionFund(pensionFund);
        slip.setOtherDeductions(otherDeductions);
        slip.setNetSalary(netSalary);
        slip.setTaxYear(taxYear);
        return paySlipRepository.save(slip);
    }

    public List<PaySlip> getMyPaySlips(String employeeId) {
        return paySlipRepository.findByEmployeeIdOrderByCreatedAtDesc(employeeId);
    }

    public PaySlip getPaySlip(String id) {
        return paySlipRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payslip not found: " + id));
    }

    // ── Salary Increase Requests ─────────────────────────────────────

    public SalaryIncreaseRequest submitIncreaseRequest(SalaryIncreaseRequestDto dto, String requestedById) {
        Employee employee = findEmployee(dto.getEmployeeId());
        Employee requestedBy = findEmployee(requestedById);

        BigDecimal current = salaryRecordRepository
                .findFirstByEmployeeIdOrderByEffectiveDateDesc(dto.getEmployeeId())
                .map(SalaryRecord::getBasicSalary)
                .orElse(BigDecimal.ZERO);

        BigDecimal percentage = dto.getProposedSalary()
                .subtract(current)
                .divide(current, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);

        SalaryIncreaseRequest request = new SalaryIncreaseRequest();
        request.setEmployee(employee);
        request.setRequestedBy(requestedBy);
        request.setCurrentSalary(current);
        request.setProposedSalary(dto.getProposedSalary());
        request.setIncreasePercentage(percentage);
        request.setJustification(dto.getJustification());
        return increaseRequestRepository.save(request);
    }

    @Transactional
    public SalaryIncreaseRequest approveIncreaseRequest(String id, String reviewerId) {
        SalaryIncreaseRequest request = findIncreaseRequest(id);
        Employee reviewer = findEmployee(reviewerId);
        request.setStatus(SalaryIncreaseStatus.APPROVED);
        request.setReviewedBy(reviewer);
        request.setReviewedAt(LocalDateTime.now());
        SalaryIncreaseRequest saved = increaseRequestRepository.save(request);

        notificationService.send(request.getEmployee(),
                "Salary Increase Approved",
                "Your salary increase request has been approved",
                NotificationType.SALARY, "SalaryIncreaseRequest", saved.getId());
        auditService.log(reviewer, "APPROVE", "SalaryIncreaseRequest", saved.getId(),
                request.getCurrentSalary().toString(), request.getProposedSalary().toString());
        return saved;
    }

    @Transactional
    public SalaryIncreaseRequest rejectIncreaseRequest(String id, String reviewerId, String reason) {
        SalaryIncreaseRequest request = findIncreaseRequest(id);
        request.setStatus(SalaryIncreaseStatus.REJECTED);
        request.setReviewedBy(findEmployee(reviewerId));
        request.setReviewedAt(LocalDateTime.now());
        request.setRejectionReason(reason);
        return increaseRequestRepository.save(request);
    }

    // ── SA PAYE Calculation ──────────────────────────────────────────

    private BigDecimal calculateMonthlyPaye(BigDecimal annualIncome, int taxYear) {
        TaxBracket bracket = taxBracketRepository
                .findBracketForIncome(taxYear, annualIncome)
                .orElseThrow(() -> new ResourceNotFoundException("No tax bracket found for income"));

        BigDecimal excess = annualIncome.subtract(bracket.getMinIncome());
        BigDecimal annualTax = bracket.getBaseTax()
                .add(excess.multiply(bracket.getMarginalRate().divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP)))
                .subtract(bracket.getRebate())
                .max(BigDecimal.ZERO);

        return annualTax.divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private BigDecimal orZero(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private Employee findEmployee(String id) {
        return employeeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found: " + id));
    }

    private SalaryIncreaseRequest findIncreaseRequest(String id) {
        return increaseRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Salary increase request not found: " + id));
    }
}
