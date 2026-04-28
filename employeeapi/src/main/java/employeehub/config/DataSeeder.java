package employeehub.config;

import employeehub.domain.*;
import employeehub.domain.enums.EmploymentStatus;
import employeehub.domain.enums.EmploymentType;
import employeehub.domain.enums.Role;
import employeehub.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements ApplicationRunner {

    private final LeaveTypeRepository leaveTypeRepository;
    private final TaxBracketRepository taxBracketRepository;
    private final BenefitTypeRepository benefitTypeRepository;
    private final DepartmentRepository departmentRepository;
    private final TeamRepository teamRepository;
    private final EmployeeRepository employeeRepository;
    private final PasswordEncoder passwordEncoder;

    private static final String ADMIN_EMAIL = "admin@employeehub.com";

    @Override
    public void run(ApplicationArguments args) {
        Department department = seedDepartment();
        Team team = seedTeam(department);
        seedAdminUser(department, team);
        seedLeaveTypes();
        seedTaxBrackets();
        seedBenefitTypes();
        ensureAllEmployeesHavePasswords();
    }

    private Department seedDepartment() {
        return departmentRepository.findAll().stream().findFirst().orElseGet(() -> {
            Department d = new Department();
            d.setName("Human Resources");
            d.setDescription("HR and administration");
            return departmentRepository.save(d);
        });
    }

    private Team seedTeam(Department department) {
        return teamRepository.findAll().stream().findFirst().orElseGet(() -> {
            Team t = new Team();
            t.setName("Management");
            t.setDescription("Leadership and management");
            t.setDepartment(department);
            return teamRepository.save(t);
        });
    }

    private void seedAdminUser(Department department, Team team) {
        if (employeeRepository.existsByEmail(ADMIN_EMAIL)) return;

        Employee admin = new Employee();
        admin.setFirstName("System");
        admin.setLastName("Admin");
        admin.setEmail(ADMIN_EMAIL);
        admin.setPassword(passwordEncoder.encode("Admin@1234"));
        admin.setJobTitle("System Administrator");
        admin.setEmploymentType(EmploymentType.FULL_TIME);
        admin.setEmploymentStatus(EmploymentStatus.ACTIVE);
        admin.setStartDate(LocalDate.now());
        admin.setRole(Role.SUPER_ADMIN);
        admin.setDepartment(department);
        admin.setTeam(team);
        admin.setEmployeeNumber("EMP-001");
        employeeRepository.save(admin);
        log.info("Seeded default admin user: {}", ADMIN_EMAIL);
    }

    /**
     * Any employee whose password is null or blank gets a default password of
     * "Employee@1234" so they can log in immediately.
     */
    private void ensureAllEmployeesHavePasswords() {
        String defaultEncoded = passwordEncoder.encode("Employee@1234");
        employeeRepository.findAll().forEach(emp -> {
            if (emp.getPassword() == null || emp.getPassword().isBlank()) {
                emp.setPassword(defaultEncoded);
                employeeRepository.save(emp);
                log.info("Set default password for: {}", emp.getEmail());
            }
        });
    }

    private void seedLeaveTypes() {
        List<LeaveType> types = List.of(
                leaveType("Annual Leave", 15, 1, false, true),
                leaveType("Sick Leave", 30, 3, true, true),
                leaveType("Family Responsibility", 3, 1, false, true),
                leaveType("Maternity Leave", 120, 1, true, true),
                leaveType("Parental Leave", 10, 1, false, true),
                leaveType("Study Leave", 5, 1, true, false)
        );
        for (LeaveType type : types) {
            if (!leaveTypeRepository.existsByName(type.getName())) {
                leaveTypeRepository.save(type);
            }
        }
    }

    // SA 2025/2026 tax year brackets (annual income)
    // Primary rebate: R17 235 | UIF: 1% employee capped at R177.12/month
    private void seedTaxBrackets() {
        if (taxBracketRepository.existsByTaxYear(2025)) return;

        List<TaxBracket> brackets = List.of(
                taxBracket(2025, 0L,          237_100L,    0L,          18.00, 17_235L),
                taxBracket(2025, 237_101L,    370_500L,    42_678L,     26.00, 17_235L),
                taxBracket(2025, 370_501L,    512_800L,    77_362L,     31.00, 17_235L),
                taxBracket(2025, 512_801L,    673_000L,    121_475L,    36.00, 17_235L),
                taxBracket(2025, 673_001L,    857_900L,    179_147L,    39.00, 17_235L),
                taxBracket(2025, 857_901L,    1_817_000L,  251_258L,    41.00, 17_235L),
                taxBracket(2025, 1_817_001L,  null,        644_489L,    45.00, 17_235L)
        );
        taxBracketRepository.saveAll(brackets);
    }

    private void seedBenefitTypes() {
        List.of(
                benefitType("Medical Aid", "Company medical aid scheme", 1500.00, 2000.00, false),
                benefitType("Pension Fund", "Retirement pension fund", 1000.00, 1500.00, false),
                benefitType("Life Cover", "Group life insurance", 200.00, 500.00, true)
        ).forEach(b -> {
            if (!benefitTypeRepository.existsByName(b.getName())) {
                benefitTypeRepository.save(b);
            }
        });
    }

    private BenefitType benefitType(String name, String desc, double empContrib, double erContrib, boolean optional) {
        BenefitType t = new BenefitType();
        t.setName(name);
        t.setDescription(desc);
        t.setEmployeeContribution(BigDecimal.valueOf(empContrib));
        t.setEmployerContribution(BigDecimal.valueOf(erContrib));
        t.setIsOptional(optional);
        return t;
    }

    private LeaveType leaveType(String name, int days, int cycleYears, boolean requiresDocs, boolean isPaid) {
        LeaveType t = new LeaveType();
        t.setName(name);
        t.setDefaultDays(days);
        t.setCycleYears(cycleYears);
        t.setRequiresDocumentation(requiresDocs);
        t.setIsPaid(isPaid);
        return t;
    }

    private TaxBracket taxBracket(int year, long min, Long max, long base, double rate, long rebate) {
        TaxBracket t = new TaxBracket();
        t.setTaxYear(year);
        t.setMinIncome(BigDecimal.valueOf(min));
        t.setMaxIncome(max != null ? BigDecimal.valueOf(max) : null);
        t.setBaseTax(BigDecimal.valueOf(base));
        t.setMarginalRate(BigDecimal.valueOf(rate));
        t.setRebate(BigDecimal.valueOf(rebate));
        return t;
    }
}
