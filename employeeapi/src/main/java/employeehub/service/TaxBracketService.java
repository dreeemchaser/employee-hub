package employeehub.service;

import employeehub.domain.Employee;
import employeehub.domain.TaxBracket;
import employeehub.dto.TaxBracketRequest;
import employeehub.dto.TaxBracketResponse;
import employeehub.exception.BusinessRuleException;
import employeehub.exception.ResourceNotFoundException;
import employeehub.repository.TaxBracketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TaxBracketService {

    private final TaxBracketRepository taxBracketRepository;
    private final EmployeeService employeeService;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public Page<TaxBracketResponse> getAll(Integer taxYear, Pageable pageable) {
        return taxYear == null
                ? taxBracketRepository.findAll(pageable).map(TaxBracketResponse::new)
                : taxBracketRepository.findByTaxYear(taxYear, pageable).map(TaxBracketResponse::new);
    }

    @Transactional
    public TaxBracket create(TaxBracketRequest request, String actorId) {
        Employee actor = employeeService.getById(actorId);
        validate(request, null);
        TaxBracket bracket = new TaxBracket();
        apply(bracket, request);
        TaxBracket saved = taxBracketRepository.save(bracket);
        auditService.log(actor, "CREATE", "TaxBracket", saved.getId().toString(),
                null, describe(saved));
        return saved;
    }

    @Transactional
    public TaxBracket update(Long id, TaxBracketRequest request, String actorId) {
        Employee actor = employeeService.getById(actorId);
        TaxBracket bracket = taxBracketRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tax bracket not found: " + id));
        String before = describe(bracket);
        validate(request, id);
        apply(bracket, request);
        TaxBracket saved = taxBracketRepository.save(bracket);
        auditService.log(actor, "UPDATE", "TaxBracket", saved.getId().toString(), before, describe(saved));
        return saved;
    }

    private void validate(TaxBracketRequest request, Long ignoredId) {
        if (request.getMaxIncome() != null && request.getMinIncome().compareTo(request.getMaxIncome()) > 0) {
            throw new IllegalArgumentException("Minimum income cannot exceed maximum income");
        }
        List<TaxBracket> existing = taxBracketRepository
                .findAllByTaxYearOrderByMinIncomeAsc(request.getTaxYear());
        for (TaxBracket bracket : existing) {
            if (bracket.getId().equals(ignoredId) || overlaps(request, bracket)) {
                if (bracket.getId().equals(ignoredId)) {
                    continue;
                }
                throw new BusinessRuleException("Tax bracket overlaps an existing bracket for tax year "
                        + request.getTaxYear());
            }
        }
    }

    private boolean overlaps(TaxBracketRequest request, TaxBracket existing) {
        boolean newStartsBeforeExistingEnds = existing.getMaxIncome() == null
                || request.getMinIncome().compareTo(existing.getMaxIncome()) <= 0;
        boolean existingStartsBeforeNewEnds = request.getMaxIncome() == null
                || existing.getMinIncome().compareTo(request.getMaxIncome()) <= 0;
        return newStartsBeforeExistingEnds && existingStartsBeforeNewEnds;
    }

    private void apply(TaxBracket bracket, TaxBracketRequest request) {
        bracket.setTaxYear(request.getTaxYear());
        bracket.setMinIncome(request.getMinIncome());
        bracket.setMaxIncome(request.getMaxIncome());
        bracket.setBaseTax(request.getBaseTax());
        bracket.setMarginalRate(request.getMarginalRate());
        bracket.setRebate(request.getRebate());
    }

    private String describe(TaxBracket bracket) {
        return bracket.getTaxYear() + "|" + bracket.getMinIncome() + "|" + bracket.getMaxIncome()
                + "|" + bracket.getBaseTax() + "|" + bracket.getMarginalRate() + "|" + bracket.getRebate();
    }
}
