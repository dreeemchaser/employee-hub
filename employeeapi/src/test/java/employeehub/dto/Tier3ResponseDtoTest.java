package employeehub.dto;

import employeehub.domain.*;
import employeehub.domain.enums.DocumentStatus;
import employeehub.domain.enums.DocumentType;
import employeehub.domain.enums.LeaveStatus;
import employeehub.domain.enums.NotificationType;
import employeehub.domain.enums.TimesheetStatus;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the Tier 3 response DTOs project a safe view of their entities.
 * Unlike Tier 1/2 (which flatten to id + name), Tier 3 preserves the nested
 * {@code employee}/{@code leaveType} shape the frontends consume — but via the
 * safe {@link EmployeeSummary}/{@link LeaveTypeSummary} mini-DTOs, never the raw
 * entity. The guarantee tested here: no DTO (nested or top-level) exposes the
 * Employee entity, so password / idNumber can never serialize through them.
 */
class Tier3ResponseDtoTest {

    private Employee employee(String id, String first, String last) {
        Employee e = new Employee();
        e.setId(id);
        e.setFirstName(first);
        e.setLastName(last);
        e.setEmployeeNumber("EMP-999");
        e.setPassword("$2b$10$secret-hash");
        e.setIdNumber("9001015800086"); // SA ID — must never leak
        return e;
    }

    private LeaveType leaveType(Long id, String name) {
        LeaveType t = new LeaveType();
        t.setId(id);
        t.setName(name);
        t.setDefaultDays(15);
        t.setRequiresDocumentation(true);
        t.setIsPaid(true);
        return t;
    }

    /** No field of the DTO (or its nested summaries) may be typed as Employee. */
    private void assertNoEmployeeTyped(Object dto) {
        for (Field f : dto.getClass().getDeclaredFields()) {
            assertThat(f.getType())
                    .as("DTO field '%s' must not expose the Employee entity", f.getName())
                    .isNotEqualTo(Employee.class);
        }
    }

    /** EmployeeSummary must carry identity/display only — never password/idNumber. */
    private void assertSummaryIsSafe(EmployeeSummary s) {
        List<String> fieldNames = java.util.Arrays.stream(EmployeeSummary.class.getDeclaredFields())
                .map(Field::getName).toList();
        assertThat(fieldNames).doesNotContain("password", "idNumber");
        assertThat(s).extracting("firstName").isNotNull();
    }

    @Test
    void leaveRequestResponse_nestsSafeSummaries_andExposesNoEntity() {
        Employee emp = employee("emp-1", "Kim", "Lee");
        Employee approver = employee("mgr-1", "Hanna", "Reed");
        LeaveRequest r = new LeaveRequest();
        r.setId("lr-1");
        r.setEmployee(emp);
        r.setLeaveType(leaveType(2L, "Sick Leave"));
        r.setApprovedBy(approver);
        r.setStartDate(LocalDate.of(2026, 3, 2));
        r.setEndDate(LocalDate.of(2026, 3, 6));
        r.setTotalDays(new BigDecimal("5"));
        r.setStatus(LeaveStatus.APPROVED);

        LeaveRequestResponse dto = new LeaveRequestResponse(r);

        assertThat(dto.getEmployee().getId()).isEqualTo("emp-1");
        assertThat(dto.getEmployee().getFirstName()).isEqualTo("Kim");
        assertThat(dto.getLeaveType().getName()).isEqualTo("Sick Leave");
        assertThat(dto.getLeaveType().getRequiresDocumentation()).isTrue();
        assertThat(dto.getApprovedBy().getLastName()).isEqualTo("Reed");
        assertThat(dto.getStatus()).isEqualTo(LeaveStatus.APPROVED);
        assertNoEmployeeTyped(dto);
        assertSummaryIsSafe(dto.getEmployee());
    }

    @Test
    void leaveBalanceResponse_nestsSafeSummaries() {
        LeaveBalance b = new LeaveBalance();
        b.setId(7L);
        b.setEmployee(employee("emp-2", "Sam", "Ncube"));
        b.setLeaveType(leaveType(1L, "Annual Leave"));
        b.setTotalDays(new BigDecimal("21"));
        b.setUsedDays(new BigDecimal("5"));
        b.setRemainingDays(new BigDecimal("16"));

        LeaveBalanceResponse dto = new LeaveBalanceResponse(b);

        assertThat(dto.getId()).isEqualTo(7L);
        assertThat(dto.getEmployee().getFirstName()).isEqualTo("Sam");
        assertThat(dto.getLeaveType().getName()).isEqualTo("Annual Leave");
        assertThat(dto.getRemainingDays()).isEqualByComparingTo("16");
        assertNoEmployeeTyped(dto);
    }

    @Test
    void timesheetResponse_projectsNestedEntries_andExposesNoEntity() {
        Timesheet t = new Timesheet();
        t.setId("ts-1");
        t.setEmployee(employee("emp-3", "Jo", "Daniels"));
        t.setStatus(TimesheetStatus.SUBMITTED);
        t.setTotalHours(new BigDecimal("8"));

        TimesheetEntry e = new TimesheetEntry();
        e.setId(11L);
        e.setDate(LocalDate.of(2026, 3, 2));
        e.setHoursWorked(new BigDecimal("8"));
        e.setDescription("Feature work");
        t.getEntries().add(e);

        TimesheetResponse dto = new TimesheetResponse(t);

        assertThat(dto.getEmployee().getFirstName()).isEqualTo("Jo");
        assertThat(dto.getEntries()).hasSize(1);
        assertThat(dto.getEntries().get(0).getHoursWorked()).isEqualByComparingTo("8");
        assertThat(dto.getEntries().get(0).getDescription()).isEqualTo("Feature work");
        // The nested entry must not carry a back-reference to the parent timesheet.
        List<String> entryFields = java.util.Arrays.stream(TimesheetEntryResponse.class.getDeclaredFields())
                .map(Field::getName).toList();
        assertThat(entryFields).doesNotContain("timesheet");
        assertNoEmployeeTyped(dto);
    }

    @Test
    void documentResponse_nestsSafeSummaries_andOmitsFileUrl() {
        Document d = new Document();
        d.setId("doc-1");
        d.setEmployee(employee("emp-4", "Lee", "Adams"));
        d.setUploadedBy(employee("emp-4", "Lee", "Adams"));
        d.setVerifiedBy(employee("hr-1", "Sky", "Roman"));
        d.setDocumentType(DocumentType.ID);
        d.setFileName("id.pdf");
        d.setFileUrl("/app/photos/secret-path.pdf"); // internal — must NOT be projected
        d.setStatus(DocumentStatus.VERIFIED);

        DocumentResponse dto = new DocumentResponse(d);

        assertThat(dto.getEmployee().getFirstName()).isEqualTo("Lee");
        assertThat(dto.getVerifiedBy().getLastName()).isEqualTo("Roman");
        assertThat(dto.getFileName()).isEqualTo("id.pdf");
        List<String> fieldNames = java.util.Arrays.stream(DocumentResponse.class.getDeclaredFields())
                .map(Field::getName).toList();
        assertThat(fieldNames).doesNotContain("fileUrl");
        assertNoEmployeeTyped(dto);
    }

    @Test
    void salaryRecordResponse_keepsMoneyAsBigDecimal_andExposesNoEntity() {
        SalaryRecord s = new SalaryRecord();
        s.setId("sr-1");
        s.setEmployee(employee("emp-5", "Pat", "Vega"));
        s.setCreatedBy(employee("pay-1", "Mo", "Khan"));
        s.setBasicSalary(new BigDecimal("48000.00"));
        s.setEffectiveDate(LocalDate.of(2026, 1, 1));

        SalaryRecordResponse dto = new SalaryRecordResponse(s);

        assertThat(dto.getEmployee().getFirstName()).isEqualTo("Pat");
        assertThat(dto.getCreatedBy().getFirstName()).isEqualTo("Mo");
        assertThat(dto.getBasicSalary()).isEqualByComparingTo("48000.00");
        assertThat(dto.getBasicSalary()).isInstanceOf(BigDecimal.class);
        assertNoEmployeeTyped(dto);
    }

    @Test
    void paySlipResponse_keepsAllMoneyAsBigDecimal_andExposesNoEntity() {
        PaySlip p = new PaySlip();
        p.setId("ps-1");
        p.setEmployee(employee("emp-6", "Ana", "Diaz"));
        p.setMonth("2026-03");
        p.setBasicSalary(new BigDecimal("48000.00"));
        p.setGrossSalary(new BigDecimal("48000.00"));
        p.setUif(new BigDecimal("177.12"));
        p.setPaye(new BigDecimal("8500.00"));
        p.setNetSalary(new BigDecimal("39322.88"));
        p.setTaxYear(2026);

        PaySlipResponse dto = new PaySlipResponse(p);

        assertThat(dto.getEmployee().getFirstName()).isEqualTo("Ana");
        assertThat(dto.getUif()).isEqualByComparingTo("177.12");
        assertThat(dto.getNetSalary()).isEqualByComparingTo("39322.88");
        assertThat(dto.getPaye()).isInstanceOf(BigDecimal.class);
        assertNoEmployeeTyped(dto);
    }

    @Test
    void notificationResponse_exposesRecipientIdOnly() {
        Notification n = new Notification();
        n.setId("n-1");
        n.setRecipient(employee("emp-7", "Ash", "Bell"));
        n.setTitle("Leave Approved");
        n.setMessage("Your leave was approved");
        n.setType(NotificationType.LEAVE);
        n.setIsRead(false);

        NotificationResponse dto = new NotificationResponse(n);

        assertThat(dto.getRecipientId()).isEqualTo("emp-7");
        assertThat(dto.getTitle()).isEqualTo("Leave Approved");
        assertThat(dto.getType()).isEqualTo(NotificationType.LEAVE);
        assertThat(dto.getIsRead()).isFalse();
        assertNoEmployeeTyped(dto);
    }

    @Test
    void auditLogResponse_nestsSafeSummary_andExposesNoEntity() {
        AuditLog log = new AuditLog();
        log.setId("al-1");
        log.setPerformedBy(employee("hr-1", "Sky", "Roman"));
        log.setAction("APPROVE");
        log.setEntityType("LeaveRequest");
        log.setEntityId("lr-1");
        log.setOldValue("PENDING");
        log.setNewValue("APPROVED");

        AuditLogResponse dto = new AuditLogResponse(log);

        assertThat(dto.getPerformedBy().getFirstName()).isEqualTo("Sky");
        assertThat(dto.getAction()).isEqualTo("APPROVE");
        assertThat(dto.getOldValue()).isEqualTo("PENDING");
        assertThat(dto.getNewValue()).isEqualTo("APPROVED");
        assertNoEmployeeTyped(dto);
    }
}
