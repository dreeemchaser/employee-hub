package employeehub.service;

import employeehub.domain.Employee;
import employeehub.domain.TaxBracket;
import employeehub.dto.TaxBracketRequest;
import employeehub.exception.BusinessRuleException;
import employeehub.repository.TaxBracketRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaxBracketServiceTest {

    @Mock TaxBracketRepository taxBracketRepository;
    @Mock EmployeeService employeeService;
    @Mock AuditService auditService;

    @InjectMocks TaxBracketService taxBracketService;

    private TaxBracketRequest request(BigDecimal min, BigDecimal max) {
        TaxBracketRequest request = new TaxBracketRequest();
        request.setTaxYear(2026);
        request.setMinIncome(min);
        request.setMaxIncome(max);
        request.setBaseTax(BigDecimal.ZERO);
        request.setMarginalRate(BigDecimal.valueOf(18));
        request.setRebate(BigDecimal.valueOf(17235));
        return request;
    }

    @Test
    void create_shouldPersistAndAuditValidBracket() {
        Employee actor = new Employee();
        actor.setId("hr-1");
        when(employeeService.getById("hr-1")).thenReturn(actor);
        when(taxBracketRepository.findAllByTaxYearOrderByMinIncomeAsc(2026)).thenReturn(List.of());
        when(taxBracketRepository.save(any())).thenAnswer(invocation -> {
            TaxBracket saved = invocation.getArgument(0);
            saved.setId(1L);
            return saved;
        });

        TaxBracket result = taxBracketService.create(request(BigDecimal.ZERO, BigDecimal.valueOf(237100)), "hr-1");

        assertThat(result.getId()).isEqualTo(1L);
        verify(auditService).log(eq(actor), eq("CREATE"), eq("TaxBracket"), eq("1"), isNull(), anyString());
    }

    @Test
    void create_shouldRejectOverlappingBracket() {
        Employee actor = new Employee();
        actor.setId("hr-1");
        TaxBracket existing = new TaxBracket();
        existing.setId(1L);
        existing.setTaxYear(2026);
        existing.setMinIncome(BigDecimal.ZERO);
        existing.setMaxIncome(BigDecimal.valueOf(237100));
        when(employeeService.getById("hr-1")).thenReturn(actor);
        when(taxBracketRepository.findAllByTaxYearOrderByMinIncomeAsc(2026)).thenReturn(List.of(existing));

        assertThatThrownBy(() -> taxBracketService.create(
                request(BigDecimal.valueOf(200000), BigDecimal.valueOf(300000)), "hr-1"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("overlaps");
        verify(taxBracketRepository, never()).save(any());
    }

    @Test
    void update_shouldAllowEditingTheSameBracket() {
        Employee actor = new Employee();
        actor.setId("hr-1");
        TaxBracket existing = new TaxBracket();
        existing.setId(1L);
        existing.setTaxYear(2026);
        existing.setMinIncome(BigDecimal.ZERO);
        existing.setMaxIncome(BigDecimal.valueOf(237100));
        when(employeeService.getById("hr-1")).thenReturn(actor);
        when(taxBracketRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(taxBracketRepository.findAllByTaxYearOrderByMinIncomeAsc(2026)).thenReturn(List.of(existing));
        when(taxBracketRepository.save(existing)).thenReturn(existing);

        TaxBracket result = taxBracketService.update(
                1L, request(BigDecimal.ZERO, BigDecimal.valueOf(237100)), "hr-1");

        assertThat(result).isSameAs(existing);
        verify(auditService).log(eq(actor), eq("UPDATE"), eq("TaxBracket"), eq("1"),
                anyString(), anyString());
    }
}
