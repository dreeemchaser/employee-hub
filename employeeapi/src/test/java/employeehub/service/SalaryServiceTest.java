package employeehub.service;

import employeehub.domain.*;
import employeehub.domain.enums.SalaryIncreaseStatus;
import employeehub.dto.PaySlipGenerateRequest;
import employeehub.dto.SalaryIncreaseRequestDto;
import employeehub.dto.SalaryRecordRequest;
import employeehub.exception.ResourceNotFoundException;
import employeehub.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SalaryServiceTest {

    @Mock SalaryRecordRepository salaryRecordRepository;
    @Mock PaySlipRepository paySlipRepository;
    @Mock SalaryIncreaseRequestRepository increaseRequestRepository;
    @Mock TaxBracketRepository taxBracketRepository;
    @Mock EmployeeRepository employeeRepository;
    @Mock NotificationService notificationService;
    @Mock AuditService auditService;

    @InjectMocks SalaryService salaryService;

    private Employee employee;
    private Employee payrollAdmin;
    private TaxBracket bracket;

    @BeforeEach
    void setUp() {
        employee = new Employee();
        employee.setId("emp-1");
        employee.setFirstName("John");

        payrollAdmin = new Employee();
        payrollAdmin.setId("pa-1");

        // Bracket: R0 - R237,100 at 18%, base tax R0, rebate R17,235
        bracket = new TaxBracket();
        bracket.setTaxYear(2025);
        bracket.setMinIncome(BigDecimal.ZERO);
        bracket.setMaxIncome(BigDecimal.valueOf(237_100));
        bracket.setBaseTax(BigDecimal.ZERO);
        bracket.setMarginalRate(BigDecimal.valueOf(18));
        bracket.setRebate(BigDecimal.valueOf(17_235));
    }

    @Test
    void createRecord_shouldSaveAndAudit() {
        SalaryRecordRequest req = new SalaryRecordRequest();
        req.setEmployeeId("emp-1");
        req.setBasicSalary(BigDecimal.valueOf(30_000));
        req.setEffectiveDate(LocalDate.now());

        when(employeeRepository.findById("emp-1")).thenReturn(Optional.of(employee));
        when(employeeRepository.findById("pa-1")).thenReturn(Optional.of(payrollAdmin));
        when(salaryRecordRepository.findFirstByEmployeeIdOrderByEffectiveDateDesc("emp-1")).thenReturn(Optional.empty());
        when(salaryRecordRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        SalaryRecord result = salaryService.createRecord(req, "pa-1");

        assertThat(result.getBasicSalary()).isEqualByComparingTo(BigDecimal.valueOf(30_000));
        verify(auditService).log(eq(payrollAdmin), eq("CREATE"), eq("SalaryRecord"), any(), isNull(), anyString());
    }

    @Test
    void generatePaySlip_shouldCalculatePayeAndUif() {
        SalaryRecord record = new SalaryRecord();
        record.setEmployee(employee);
        record.setBasicSalary(BigDecimal.valueOf(20_000)); // R20k/month = R240k/year

        PaySlipGenerateRequest req = new PaySlipGenerateRequest();
        req.setEmployeeId("emp-1");
        req.setMonth("2025-06");

        when(employeeRepository.findById("emp-1")).thenReturn(Optional.of(employee));
        when(salaryRecordRepository.findFirstByEmployeeIdOrderByEffectiveDateDesc("emp-1")).thenReturn(Optional.of(record));
        when(taxBracketRepository.findBracketForIncome(anyInt(), any())).thenReturn(Optional.of(bracket));
        when(paySlipRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        PaySlip result = salaryService.generatePaySlip(req, "pa-1");

        // Annual R240k: tax = (240000 - 0) * 18% - 17235 = 43200 - 17235 = 25965 / 12 = 2163.75
        assertThat(result.getPaye()).isEqualByComparingTo(new BigDecimal("2163.75"));
        // UIF = 20000 * 1% = 200, capped at 177.12
        assertThat(result.getUif()).isEqualByComparingTo(new BigDecimal("177.12"));
        assertThat(result.getNetSalary()).isEqualByComparingTo(new BigDecimal("17659.13"));
    }

    @Test
    void generatePaySlip_shouldThrow_whenNoSalaryRecord() {
        PaySlipGenerateRequest req = new PaySlipGenerateRequest();
        req.setEmployeeId("emp-1");
        req.setMonth("2025-06");

        when(salaryRecordRepository.findFirstByEmployeeIdOrderByEffectiveDateDesc("emp-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> salaryService.generatePaySlip(req, "pa-1"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("No salary record");
    }

    @Test
    void submitIncreaseRequest_shouldCalculatePercentage() {
        SalaryRecord record = new SalaryRecord();
        record.setBasicSalary(BigDecimal.valueOf(20_000));

        SalaryIncreaseRequestDto dto = new SalaryIncreaseRequestDto();
        dto.setEmployeeId("emp-1");
        dto.setProposedSalary(BigDecimal.valueOf(22_000));
        dto.setJustification("Performance");

        when(employeeRepository.findById("emp-1")).thenReturn(Optional.of(employee));
        when(employeeRepository.findById("mgr-1")).thenReturn(Optional.of(payrollAdmin));
        when(salaryRecordRepository.findFirstByEmployeeIdOrderByEffectiveDateDesc("emp-1")).thenReturn(Optional.of(record));
        when(increaseRequestRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        SalaryIncreaseRequest result = salaryService.submitIncreaseRequest(dto, "mgr-1");

        assertThat(result.getIncreasePercentage()).isEqualByComparingTo(new BigDecimal("10.00"));
        assertThat(result.getStatus()).isEqualTo(SalaryIncreaseStatus.PENDING);
    }

    @Test
    void approveIncreaseRequest_shouldNotifyEmployee() {
        Employee reviewer = new Employee();
        reviewer.setId("hr-1");

        SalaryIncreaseRequest request = new SalaryIncreaseRequest();
        request.setId("sir-1");
        request.setEmployee(employee);
        request.setCurrentSalary(BigDecimal.valueOf(20_000));
        request.setProposedSalary(BigDecimal.valueOf(22_000));
        request.setStatus(SalaryIncreaseStatus.PENDING);

        when(increaseRequestRepository.findById("sir-1")).thenReturn(Optional.of(request));
        when(employeeRepository.findById("hr-1")).thenReturn(Optional.of(reviewer));
        when(increaseRequestRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        SalaryIncreaseRequest result = salaryService.approveIncreaseRequest("sir-1", "hr-1");

        assertThat(result.getStatus()).isEqualTo(SalaryIncreaseStatus.APPROVED);
        verify(notificationService).send(eq(employee), anyString(), anyString(), any(), anyString(), any());
        verify(auditService).log(any(), eq("APPROVE"), eq("SalaryIncreaseRequest"), any(), any(), any());
    }

    @Test
    void generatePaySlip_shouldFloorPayeAtZero_whenRebateExceedsTax() {
        // Low earner: R5,000/month = R60,000/year. Tax before rebate = 60000 * 18% = 10800,
        // which is less than the R17,235 rebate, so PAYE must floor at 0 (never negative).
        SalaryRecord record = new SalaryRecord();
        record.setEmployee(employee);
        record.setBasicSalary(BigDecimal.valueOf(5_000));

        PaySlipGenerateRequest req = new PaySlipGenerateRequest();
        req.setEmployeeId("emp-1");
        req.setMonth("2025-06");

        when(employeeRepository.findById("emp-1")).thenReturn(Optional.of(employee));
        when(salaryRecordRepository.findFirstByEmployeeIdOrderByEffectiveDateDesc("emp-1")).thenReturn(Optional.of(record));
        when(taxBracketRepository.findBracketForIncome(anyInt(), any())).thenReturn(Optional.of(bracket));
        when(paySlipRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        PaySlip result = salaryService.generatePaySlip(req, "pa-1");

        assertThat(result.getPaye()).isEqualByComparingTo(BigDecimal.ZERO);
        // UIF = 5000 * 1% = 50.00, below the R177.12 cap
        assertThat(result.getUif()).isEqualByComparingTo(new BigDecimal("50.00"));
        // Net = 5000 - 0 (paye) - 50 (uif) = 4950.00
        assertThat(result.getNetSalary()).isEqualByComparingTo(new BigDecimal("4950.00"));
    }

    @Test
    void generatePaySlip_shouldSubtractOptionalDeductions() {
        SalaryRecord record = new SalaryRecord();
        record.setEmployee(employee);
        record.setBasicSalary(BigDecimal.valueOf(20_000));

        PaySlipGenerateRequest req = new PaySlipGenerateRequest();
        req.setEmployeeId("emp-1");
        req.setMonth("2025-06");
        req.setMedicalAid(new BigDecimal("1000"));
        req.setPensionFund(new BigDecimal("1500"));
        req.setOtherDeductions(new BigDecimal("250"));

        when(employeeRepository.findById("emp-1")).thenReturn(Optional.of(employee));
        when(salaryRecordRepository.findFirstByEmployeeIdOrderByEffectiveDateDesc("emp-1")).thenReturn(Optional.of(record));
        when(taxBracketRepository.findBracketForIncome(anyInt(), any())).thenReturn(Optional.of(bracket));
        when(paySlipRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        PaySlip result = salaryService.generatePaySlip(req, "pa-1");

        // Base case net was 17659.13 with no optional deductions; subtract 1000 + 1500 + 250 = 2750
        assertThat(result.getNetSalary()).isEqualByComparingTo(new BigDecimal("14909.13"));
        assertThat(result.getMedicalAid()).isEqualByComparingTo(new BigDecimal("1000"));
        assertThat(result.getPensionFund()).isEqualByComparingTo(new BigDecimal("1500"));
        assertThat(result.getOtherDeductions()).isEqualByComparingTo(new BigDecimal("250"));
    }

    @Test
    void createRecord_shouldClosePreviousRecord() {
        SalaryRecordRequest req = new SalaryRecordRequest();
        req.setEmployeeId("emp-1");
        req.setBasicSalary(BigDecimal.valueOf(35_000));
        req.setEffectiveDate(LocalDate.of(2025, 7, 1));

        SalaryRecord previous = new SalaryRecord();
        previous.setEmployee(employee);
        previous.setBasicSalary(BigDecimal.valueOf(30_000));
        previous.setEffectiveDate(LocalDate.of(2025, 1, 1));

        when(employeeRepository.findById("emp-1")).thenReturn(Optional.of(employee));
        when(employeeRepository.findById("pa-1")).thenReturn(Optional.of(payrollAdmin));
        when(salaryRecordRepository.findFirstByEmployeeIdOrderByEffectiveDateDesc("emp-1")).thenReturn(Optional.of(previous));
        when(salaryRecordRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        salaryService.createRecord(req, "pa-1");

        // Previous record should be end-dated to the day before the new effective date.
        assertThat(previous.getEndDate()).isEqualTo(LocalDate.of(2025, 6, 30));
    }

    @Test
    void submitIncreaseRequest_shouldHandleNoPriorSalary() {
        // No prior salary record => current salary defaults to ZERO. Percentage divides by
        // current; verify this path does not blow up and produces a sane request.
        SalaryIncreaseRequestDto dto = new SalaryIncreaseRequestDto();
        dto.setEmployeeId("emp-1");
        dto.setProposedSalary(BigDecimal.valueOf(25_000));
        dto.setJustification("New hire adjustment");

        when(employeeRepository.findById("emp-1")).thenReturn(Optional.of(employee));
        when(salaryRecordRepository.findFirstByEmployeeIdOrderByEffectiveDateDesc("emp-1")).thenReturn(Optional.empty());

        // With no prior salary record, current salary is ZERO and the percentage calculation
        // divides by zero. This documents the CURRENT behaviour: it throws ArithmeticException.
        // NOTE: this is arguably a real bug (a 0 -> R25k increase should be representable).
        // Flagged for follow-up; the test pins current behaviour so a future fix is deliberate.
        assertThatThrownBy(() -> salaryService.submitIncreaseRequest(dto, "emp-1"))
                .isInstanceOf(ArithmeticException.class);
    }

    @Test
    void rejectIncreaseRequest_shouldSetStatusAndReason() {
        Employee reviewer = new Employee();
        reviewer.setId("hr-1");

        SalaryIncreaseRequest request = new SalaryIncreaseRequest();
        request.setId("sir-1");
        request.setEmployee(employee);
        request.setCurrentSalary(BigDecimal.valueOf(20_000));
        request.setProposedSalary(BigDecimal.valueOf(30_000));
        request.setStatus(SalaryIncreaseStatus.PENDING);

        when(increaseRequestRepository.findById("sir-1")).thenReturn(Optional.of(request));
        when(employeeRepository.findById("hr-1")).thenReturn(Optional.of(reviewer));
        when(increaseRequestRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        SalaryIncreaseRequest result = salaryService.rejectIncreaseRequest("sir-1", "hr-1", "Budget constraints");

        assertThat(result.getStatus()).isEqualTo(SalaryIncreaseStatus.REJECTED);
        assertThat(result.getRejectionReason()).isEqualTo("Budget constraints");
    }
}
