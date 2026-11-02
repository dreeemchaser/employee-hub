package employeehub.service;

import employeehub.domain.LeavePolicyVersion;
import employeehub.domain.LeaveType;
import employeehub.domain.enums.PolicyStatus;
import employeehub.dto.LeavePolicyRequest;
import employeehub.exception.BusinessRuleException;
import employeehub.exception.ResourceNotFoundException;
import employeehub.repository.LeavePolicyVersionRepository;
import employeehub.repository.LeaveTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class LeavePolicyService {

    private final LeavePolicyVersionRepository policyRepository;
    private final LeaveTypeRepository leaveTypeRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public Page<LeavePolicyVersion> getActive(Pageable pageable) {
        return policyRepository.findByStatus(PolicyStatus.ACTIVE, pageable);
    }

    @Transactional(readOnly = true)
    public Page<LeavePolicyVersion> getAll(Pageable pageable) {
        return policyRepository.findAll(pageable);
    }

    @Transactional
    public LeavePolicyVersion create(LeavePolicyRequest request, employeehub.domain.Employee actor) {
        validateDates(request);
        LeaveType leaveType = leaveTypeRepository.findById(request.getLeaveTypeId())
                .orElseThrow(() -> new ResourceNotFoundException("Leave type not found: " + request.getLeaveTypeId()));
        rejectOverlap(leaveType.getId(), request, null);
        int nextVersion = policyRepository.findByLeaveTypeIdOrderByVersionNumberDesc(leaveType.getId())
                .stream().mapToInt(LeavePolicyVersion::getVersionNumber).max().orElse(0) + 1;
        LeavePolicyVersion policy = map(new LeavePolicyVersion(), leaveType, request);
        policy.setVersionNumber(nextVersion);
        policy.setStatus(PolicyStatus.DRAFT);
        LeavePolicyVersion saved = policyRepository.save(policy);
        auditService.log(actor, "CREATE", "LeavePolicyVersion", saved.getId(), null, saved.getName());
        return saved;
    }

    @Transactional
    public LeavePolicyVersion update(String id, LeavePolicyRequest request, employeehub.domain.Employee actor) {
        validateDates(request);
        LeavePolicyVersion policy = find(id);
        if (policy.getStatus() != PolicyStatus.DRAFT) {
            throw new BusinessRuleException("Only draft leave policies can be edited");
        }
        LeaveType leaveType = leaveTypeRepository.findById(request.getLeaveTypeId())
                .orElseThrow(() -> new ResourceNotFoundException("Leave type not found: " + request.getLeaveTypeId()));
        rejectOverlap(leaveType.getId(), request, id);
        LeavePolicyVersion saved = policyRepository.save(map(policy, leaveType, request));
        auditService.log(actor, "UPDATE", "LeavePolicyVersion", id, null, saved.getName());
        return saved;
    }

    @Transactional
    public LeavePolicyVersion activate(String id, employeehub.domain.Employee actor) {
        LeavePolicyVersion policy = find(id);
        if (policy.getStatus() == PolicyStatus.RETIRED) {
            throw new BusinessRuleException("Retired leave policies cannot be activated");
        }
        policyRepository.findEffective(policy.getLeaveType().getId(), PolicyStatus.ACTIVE, policy.getEffectiveFrom())
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    existing.setEffectiveTo(policy.getEffectiveFrom().minusDays(1));
                    policyRepository.save(existing);
                });
        policy.setStatus(PolicyStatus.ACTIVE);
        LeavePolicyVersion saved = policyRepository.save(policy);
        auditService.log(actor, "ACTIVATE", "LeavePolicyVersion", id, "DRAFT", "ACTIVE");
        return saved;
    }

    @Transactional
    public LeavePolicyVersion retire(String id, employeehub.domain.Employee actor) {
        LeavePolicyVersion policy = find(id);
        if (policy.getStatus() == PolicyStatus.RETIRED) {
            return policy;
        }
        policy.setStatus(PolicyStatus.RETIRED);
        LeavePolicyVersion saved = policyRepository.save(policy);
        auditService.log(actor, "RETIRE", "LeavePolicyVersion", id, "ACTIVE", "RETIRED");
        return saved;
    }

    @Transactional(readOnly = true)
    public LeavePolicyVersion effective(Long leaveTypeId, LocalDate date) {
        return policyRepository.findEffective(leaveTypeId, PolicyStatus.ACTIVE, date)
                .orElseThrow(() -> new BusinessRuleException("No active leave policy exists for this leave type"));
    }

    private LeavePolicyVersion find(String id) {
        return policyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Leave policy not found: " + id));
    }

    private LeavePolicyVersion map(LeavePolicyVersion policy, LeaveType leaveType, LeavePolicyRequest request) {
        policy.setLeaveType(leaveType);
        policy.setName(request.getName().trim());
        policy.setPaid(request.getPaid());
        policy.setDefaultEntitlement(request.getDefaultEntitlement());
        policy.setRequiresDocumentation(request.getRequiresDocumentation());
        policy.setNoticePeriodDays(request.getNoticePeriodDays());
        policy.setEffectiveFrom(request.getEffectiveFrom());
        policy.setEffectiveTo(request.getEffectiveTo());
        return policy;
    }

    private void validateDates(LeavePolicyRequest request) {
        if (request.getEffectiveTo() != null && request.getEffectiveTo().isBefore(request.getEffectiveFrom())) {
            throw new IllegalArgumentException("Policy effective end cannot be before its start");
        }
    }

    private void rejectOverlap(Long leaveTypeId, LeavePolicyRequest request, String excludedId) {
        if (policyRepository.hasOverlappingVersion(
                leaveTypeId, request.getEffectiveFrom(), request.getEffectiveTo(), excludedId)) {
            throw new BusinessRuleException("Policy effective dates overlap an existing version");
        }
    }
}
