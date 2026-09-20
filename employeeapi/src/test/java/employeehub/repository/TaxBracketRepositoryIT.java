package employeehub.repository;

import employeehub.domain.TaxBracket;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class TaxBracketRepositoryIT extends AbstractRepositoryIT {

    @Autowired
    TestEntityManager em;

    @Autowired
    TaxBracketRepository taxBracketRepository;

    @BeforeEach
    void setUp() {
        // Two 2026 brackets; the second is open-ended (maxIncome null).
        persist(2026, "0", "100000", "0", "0.18");
        persist(2026, "100001", null, "18000", "0.26");
        // A different tax year that must not be matched.
        persist(2025, "0", "999999", "0", "0.20");
        em.flush();
    }

    @Test
    void findBracketForIncome_picksTheBracketContainingTheIncome() {
        var bracket = taxBracketRepository.findBracketForIncome(2026, new BigDecimal("50000"));
        assertThat(bracket).isPresent();
        assertThat(bracket.get().getMarginalRate()).isEqualByComparingTo("0.18");
    }

    @Test
    void findBracketForIncome_matchesOpenEndedTopBracket() {
        // maxIncome IS NULL branch — income above the top threshold falls in the open bracket.
        var bracket = taxBracketRepository.findBracketForIncome(2026, new BigDecimal("500000"));
        assertThat(bracket).isPresent();
        assertThat(bracket.get().getMarginalRate()).isEqualByComparingTo("0.26");
    }

    @Test
    void findBracketForIncome_isScopedToTheGivenTaxYear() {
        var bracket = taxBracketRepository.findBracketForIncome(2025, new BigDecimal("50000"));
        assertThat(bracket).isPresent();
        assertThat(bracket.get().getTaxYear()).isEqualTo(2025);
    }

    @Test
    void existsByTaxYear_reflectsSeededYears() {
        assertThat(taxBracketRepository.existsByTaxYear(2026)).isTrue();
        assertThat(taxBracketRepository.existsByTaxYear(2099)).isFalse();
    }

    private void persist(int year, String min, String max, String baseTax, String rate) {
        TaxBracket b = new TaxBracket();
        b.setTaxYear(year);
        b.setMinIncome(new BigDecimal(min));
        b.setMaxIncome(max == null ? null : new BigDecimal(max));
        b.setBaseTax(new BigDecimal(baseTax));
        b.setMarginalRate(new BigDecimal(rate));
        b.setRebate(new BigDecimal("17235"));
        em.persist(b);
    }
}
