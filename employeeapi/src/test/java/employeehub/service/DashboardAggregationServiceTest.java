package employeehub.service;

import employeehub.domain.Department;
import employeehub.domain.enums.DocumentStatus;
import employeehub.domain.enums.LeaveStatus;
import employeehub.domain.enums.TimesheetStatus;
import employeehub.dto.DepartmentBreakdownEntry;
import employeehub.repository.DepartmentRepository;
import employeehub.repository.DocumentRepository;
import employeehub.repository.LeaveRequestRepository;
import employeehub.repository.TimesheetRepository;
import employeehub.repository.support.DepartmentCountProjection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardAggregationServiceTest {

    @Mock
    DepartmentRepository departmentRepository;
    @Mock
    LeaveRequestRepository leaveRequestRepository;
    @Mock
    TimesheetRepository timesheetRepository;
    @Mock
    DocumentRepository documentRepository;

    private DashboardAggregationService dashboardAggregationService;

    @BeforeEach
    void setUp() {
        dashboardAggregationService = new DashboardAggregationService(
                departmentRepository, leaveRequestRepository, timesheetRepository, documentRepository);
    }

    @Test
    void getDepartmentBreakdown_returnsOneEntryPerDepartment_regardlessOfActivity() {
        Department technology = department("Technology");
        Department hr = department("HR");
        when(departmentRepository.findAll()).thenReturn(List.of(technology, hr));
        when(leaveRequestRepository.countByDepartmentAndStatus(LeaveStatus.PENDING)).thenReturn(List.of());
        when(timesheetRepository.countByDepartmentAndStatus(TimesheetStatus.SUBMITTED)).thenReturn(List.of());
        when(documentRepository.countByDepartmentAndStatus(DocumentStatus.PENDING)).thenReturn(List.of());

        List<DepartmentBreakdownEntry> breakdown = dashboardAggregationService.getDepartmentBreakdown();

        assertThat(breakdown).hasSize(2);
        assertThat(breakdown).extracting(DepartmentBreakdownEntry::getDepartmentName)
                .containsExactlyInAnyOrder("Technology", "HR");
    }

    @Test
    void getDepartmentBreakdown_whenDepartmentHasNoDataInACategory_defaultsThatCategoryToZero() {
        Department technology = department("Technology");
        Department hr = department("HR");
        when(departmentRepository.findAll()).thenReturn(List.of(technology, hr));
        // Technology only has pending leave; HR has nothing anywhere.
        when(leaveRequestRepository.countByDepartmentAndStatus(LeaveStatus.PENDING))
                .thenReturn(List.of(projection("Technology", 3L)));
        when(timesheetRepository.countByDepartmentAndStatus(TimesheetStatus.SUBMITTED)).thenReturn(List.of());
        when(documentRepository.countByDepartmentAndStatus(DocumentStatus.PENDING)).thenReturn(List.of());

        List<DepartmentBreakdownEntry> breakdown = dashboardAggregationService.getDepartmentBreakdown();

        DepartmentBreakdownEntry technologyEntry = breakdown.stream()
                .filter(e -> e.getDepartmentName().equals("Technology")).findFirst().orElseThrow();
        assertThat(technologyEntry.getPendingLeave()).isEqualTo(3L);
        assertThat(technologyEntry.getPendingTimesheets()).isZero();
        assertThat(technologyEntry.getPendingDocuments()).isZero();

        DepartmentBreakdownEntry hrEntry = breakdown.stream()
                .filter(e -> e.getDepartmentName().equals("HR")).findFirst().orElseThrow();
        assertThat(hrEntry.getPendingLeave()).isZero();
        assertThat(hrEntry.getPendingTimesheets()).isZero();
        assertThat(hrEntry.getPendingDocuments()).isZero();
    }

    @Test
    void getDepartmentBreakdown_mergesAllThreeCategoriesForTheSameDepartment() {
        Department technology = department("Technology");
        when(departmentRepository.findAll()).thenReturn(List.of(technology));
        when(leaveRequestRepository.countByDepartmentAndStatus(LeaveStatus.PENDING))
                .thenReturn(List.of(projection("Technology", 2L)));
        when(timesheetRepository.countByDepartmentAndStatus(TimesheetStatus.SUBMITTED))
                .thenReturn(List.of(projection("Technology", 5L)));
        when(documentRepository.countByDepartmentAndStatus(DocumentStatus.PENDING))
                .thenReturn(List.of(projection("Technology", 1L)));

        List<DepartmentBreakdownEntry> breakdown = dashboardAggregationService.getDepartmentBreakdown();

        assertThat(breakdown).hasSize(1);
        DepartmentBreakdownEntry entry = breakdown.get(0);
        assertThat(entry.getDepartmentName()).isEqualTo("Technology");
        assertThat(entry.getPendingLeave()).isEqualTo(2L);
        assertThat(entry.getPendingTimesheets()).isEqualTo(5L);
        assertThat(entry.getPendingDocuments()).isEqualTo(1L);
    }

    @Test
    void getDepartmentBreakdown_whenNoDepartmentsExist_returnsEmptyList() {
        when(departmentRepository.findAll()).thenReturn(List.of());
        when(leaveRequestRepository.countByDepartmentAndStatus(LeaveStatus.PENDING)).thenReturn(List.of());
        when(timesheetRepository.countByDepartmentAndStatus(TimesheetStatus.SUBMITTED)).thenReturn(List.of());
        when(documentRepository.countByDepartmentAndStatus(DocumentStatus.PENDING)).thenReturn(List.of());

        assertThat(dashboardAggregationService.getDepartmentBreakdown()).isEmpty();
    }

    private Department department(String name) {
        Department d = new Department();
        d.setName(name);
        return d;
    }

    private DepartmentCountProjection projection(String departmentName, long count) {
        return new DepartmentCountProjection() {
            @Override
            public String getDepartmentName() {
                return departmentName;
            }

            @Override
            public long getCount() {
                return count;
            }
        };
    }
}
