package employeehub.web;

import employeehub.controller.DashboardController;
import employeehub.domain.enums.LeaveStatus;
import employeehub.repository.DocumentRepository;
import employeehub.repository.EmployeeRepository;
import employeehub.repository.LeaveRequestRepository;
import employeehub.repository.TimesheetRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code DashboardController} is a documented architectural exception: it queries repositories
 * directly (read-only aggregation). These tests exercise its route rule (HR/admin only) and the
 * stats envelope.
 */
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

    @Test
    @WithMockUser(username = "hr@employeehub.com", roles = "HR_ADMIN")
    void getStats_asHrAdmin_returnsAggregatedCounts() throws Exception {
        when(employeeRepository.count()).thenReturn(10L);
        when(leaveRequestRepository.findAllFiltered(isNull(), eq(LeaveStatus.PENDING))).thenReturn(List.of());
        when(timesheetRepository.findAllFiltered(isNull())).thenReturn(List.of());
        when(documentRepository.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/dashboard/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.employees").value(10));
    }

    @Test
    @WithMockUser(username = "user@employeehub.com", roles = "EMPLOYEE")
    void getStats_asEmployee_isForbidden() throws Exception {
        mockMvc.perform(get("/dashboard/stats"))
                .andExpect(status().isForbidden());
    }
}
