package employeehub.web;

import employeehub.controller.DashboardController;
import employeehub.domain.enums.LeaveStatus;
import employeehub.dto.DepartmentBreakdownEntry;
import employeehub.repository.DocumentRepository;
import employeehub.repository.EmployeeRepository;
import employeehub.repository.LeaveRequestRepository;
import employeehub.repository.TimesheetRepository;
import employeehub.service.DashboardAggregationService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DashboardController.class)
class DashboardControllerTest extends WebMvcTestSupport {

    @MockitoBean
    EmployeeRepository employeeRepository;
    @MockitoBean
    LeaveRequestRepository leaveRequestRepository;
    @MockitoBean
    TimesheetRepository timesheetRepository;
    @MockitoBean
    DocumentRepository documentRepository;
    @MockitoBean
    DashboardAggregationService dashboardAggregationService;

    @Test
    @WithMockUser(username = "user@employeehub.com", roles = "EMPLOYEE")
    void getStats_asEmployee_isForbidden() throws Exception {
        // /dashboard/** is HR_ADMIN/SUPER_ADMIN only.
        mockMvc.perform(get("/dashboard/stats"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "hr@employeehub.com", roles = "HR_ADMIN")
    void getStats_asHrAdmin_returnsOk() throws Exception {
        when(employeeRepository.count()).thenReturn(0L);
        when(leaveRequestRepository.findAllFiltered(null, LeaveStatus.PENDING)).thenReturn(List.of());
        when(timesheetRepository.findAllFiltered(null)).thenReturn(List.of());
        when(documentRepository.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/dashboard/stats"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "user@employeehub.com", roles = "EMPLOYEE")
    void getDepartmentBreakdown_asEmployee_isForbidden() throws Exception {
        mockMvc.perform(get("/dashboard/department-breakdown"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "hr@employeehub.com", roles = "HR_ADMIN")
    void getDepartmentBreakdown_asHrAdmin_returnsOk() throws Exception {
        when(dashboardAggregationService.getDepartmentBreakdown())
                .thenReturn(List.of(new DepartmentBreakdownEntry("Technology", 2L, 1L, 0L)));

        mockMvc.perform(get("/dashboard/department-breakdown"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "admin@employeehub.com", roles = "SUPER_ADMIN")
    void getDepartmentBreakdown_asSuperAdmin_returnsOk() throws Exception {
        when(dashboardAggregationService.getDepartmentBreakdown()).thenReturn(List.of());

        mockMvc.perform(get("/dashboard/department-breakdown"))
                .andExpect(status().isOk());
    }
}
