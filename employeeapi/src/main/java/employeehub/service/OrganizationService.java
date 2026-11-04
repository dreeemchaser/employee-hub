package employeehub.service;

import employeehub.domain.Employee;
import employeehub.domain.enums.EmploymentStatus;
import employeehub.domain.enums.Role;
import employeehub.dto.OrganizationNodeResponse;
import employeehub.exception.ResourceNotFoundException;
import employeehub.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrganizationService {

    private final EmployeeRepository employeeRepository;

    @Transactional(readOnly = true)
    public Page<OrganizationNodeResponse> getTree(Employee caller, Long departmentId, Long teamId,
                                                   EmploymentStatus status, Pageable pageable) {
        Page<Employee> roots;
        if (isOrganizationAdmin(caller)) {
            roots = employeeRepository.findOrganizationRoots(departmentId, teamId, status, pageable);
        } else {
            roots = employeeRepository.findById(caller.getId())
                    .map(employee -> new org.springframework.data.domain.PageImpl<>(
                            List.of(employee), pageable, 1))
                    .orElseThrow(() -> new ResourceNotFoundException("Employee not found: " + caller.getId()));
        }

        return addChildren(roots);
    }

    @Transactional(readOnly = true)
    public Page<OrganizationNodeResponse> search(Employee caller, String query, Long departmentId, Long teamId,
                                                 EmploymentStatus status, Pageable pageable) {
        String term = query == null || query.isBlank() ? null : "%" + query.trim().toLowerCase() + "%";
        Page<Employee> results;
        if (isOrganizationAdmin(caller)) {
            results = employeeRepository.searchOrganization(term, departmentId, teamId, status, pageable);
        } else if (caller.getRole() == Role.MANAGER) {
            results = employeeRepository.searchDescendants(caller.getId(), term, departmentId, teamId, status, pageable);
        } else {
            results = employeeRepository.findEmployeeContext(caller.getId(), term, departmentId, teamId, status, pageable);
        }
        return addChildren(results);
    }

    @Transactional(readOnly = true)
    public OrganizationNodeResponse getNode(Employee caller, String employeeId) {
        Employee target = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Organization node not found: " + employeeId));

        if (!isVisible(caller, target)) {
            throw new ResourceNotFoundException("Organization node not found: " + employeeId);
        }
        return mapWithChildren(target);
    }

    private boolean isVisible(Employee caller, Employee target) {
        if (isOrganizationAdmin(caller) || caller.getId().equals(target.getId())) {
            return true;
        }
        if (caller.getRole() == Role.MANAGER) {
            return employeeRepository.existsInManagerScope(caller.getId(), target.getId());
        }
        return target.getId().equals(caller.getManager() != null ? caller.getManager().getId() : null)
                || caller.getId().equals(target.getManager() != null ? target.getManager().getId() : null);
    }

    private Page<OrganizationNodeResponse> addChildren(Page<Employee> employees) {
        List<Employee> parents = employees.getContent();
        Map<String, List<Employee>> childrenByManager = childrenByManager(parents);
        return employees.map(employee -> map(employee, childrenByManager.getOrDefault(employee.getId(), List.of())));
    }

    private OrganizationNodeResponse mapWithChildren(Employee employee) {
        List<Employee> children = employeeRepository.findByManager_IdOrderByLastNameAscFirstNameAsc(employee.getId());
        return map(employee, children);
    }

    private OrganizationNodeResponse map(Employee employee, Collection<Employee> children) {
        List<OrganizationNodeResponse> childResponses = children.stream()
                .sorted(Comparator.comparing(Employee::getLastName).thenComparing(Employee::getFirstName))
                .map(child -> new OrganizationNodeResponse(child, List.of()))
                .toList();
        return new OrganizationNodeResponse(employee, childResponses);
    }

    private Map<String, List<Employee>> childrenByManager(List<Employee> parents) {
        if (parents.isEmpty()) {
            return Map.of();
        }
        return employeeRepository.findByManager_IdIn(
                        parents.stream().map(Employee::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(employee -> employee.getManager().getId()));
    }

    private boolean isOrganizationAdmin(Employee employee) {
        return employee.getRole() == Role.HR_ADMIN || employee.getRole() == Role.SUPER_ADMIN;
    }
}
