package employeehub.service;

import employeehub.domain.LeavePolicyVersion;
import employeehub.domain.LeaveType;
import employeehub.domain.enums.PolicyStatus;
import employeehub.repository.LeaveBalanceRepository;
import employeehub.repository.LeavePolicyVersionRepository;
import employeehub.repository.LeaveRequestRepository;
import employeehub.repository.LeaveTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class LeavePolicyBootstrap {

    private final LeaveTypeRepository leaveTypeRepository;
    private final LeavePolicyVersionRepository policyRepository;
    private final LeaveBalanceRepository balanceRepository;
    private final LeaveRequestRepository requestRepository;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void createInitialVersionsAndBackfillHistory() {
        Map<Long, LeavePolicyVersion> policies = leaveTypeRepository.findAll().stream()
                .collect(Collectors.toMap(LeaveType::getId, this::initialPolicy));

        balanceRepository.findAll().stream()
                .filter(balance -> balance.getPolicyVersion() == null)
                .forEach(balance -> balance.setPolicyVersion(policies.get(balance.getLeaveType().getId())));
        requestRepository.findAll().stream()
                .filter(request -> request.getPolicyVersion() == null)
                .forEach(request -> request.setPolicyVersion(policies.get(request.getLeaveType().getId())));
    }

    private LeavePolicyVersion initialPolicy(LeaveType leaveType) {
        return policyRepository.findByLeaveTypeIdOrderByVersionNumberDesc(leaveType.getId())
                .stream().findFirst().orElseGet(() -> {
                    LeavePolicyVersion policy = new LeavePolicyVersion();
                    policy.setLeaveType(leaveType);
                    policy.setVersionNumber(1);
                    policy.setName(leaveType.getName());
                    policy.setPaid(leaveType.getIsPaid());
                    policy.setDefaultEntitlement(leaveType.getDefaultDays());
                    policy.setRequiresDocumentation(leaveType.getRequiresDocumentation());
                    policy.setNoticePeriodDays(leaveType.getName().equalsIgnoreCase("Annual Leave") ? 14 : 0);
                    policy.setEffectiveFrom(LocalDate.of(2000, 1, 1));
                    policy.setStatus(PolicyStatus.ACTIVE);
                    return policyRepository.save(policy);
                });
    }
}
