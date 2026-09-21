package employeehub.repository.support;

/**
 * Spring Data JPA interface projection for a {@code (departmentName, count)} pair. Shared by the
 * per-department GROUP BY queries in {@code LeaveRequestRepository}, {@code TimesheetRepository},
 * and {@code DocumentRepository} — the three queries return the same shape, so this avoids three
 * near-identical single-purpose DTOs.
 */
public interface DepartmentCountProjection {

    String getDepartmentName();

    long getCount();
}
