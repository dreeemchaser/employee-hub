package employeehub.web;

import employeehub.controller.NotificationController;
import employeehub.domain.Employee;
import employeehub.domain.NotificationPreference;
import employeehub.domain.enums.NotificationType;
import employeehub.service.EmployeeService;
import employeehub.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationController.class)
class NotificationControllerTest extends WebMvcTestSupport {

    @MockitoBean
    NotificationService notificationService;
    @MockitoBean
    EmployeeService employeeService;

    private void stubCaller() {
        Employee e = new Employee();
        e.setId("emp-1");
        when(employeeService.getByEmail(any())).thenReturn(e);
    }

    @Test
    @WithMockUser(username = "user@employeehub.com", roles = "EMPLOYEE")
    void getMy_asEmployee_returnsOk() throws Exception {
        stubCaller();
        when(notificationService.getMy(any())).thenReturn(List.of());

        mockMvc.perform(get("/notifications/my"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "user@employeehub.com", roles = "EMPLOYEE")
    void markAllAsRead_asEmployee_returnsOk() throws Exception {
        stubCaller();

        mockMvc.perform(patch("/notifications/read-all").with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "user@employeehub.com", roles = "EMPLOYEE")
    void getPreferences_asEmployee_returnsOk() throws Exception {
        stubCaller();
        NotificationPreference preference = new NotificationPreference();
        preference.setNotificationType(NotificationType.LEAVE);
        when(notificationService.getMyPreferences("emp-1")).thenReturn(List.of(preference));

        mockMvc.perform(get("/notifications/preferences"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "user@employeehub.com", roles = "EMPLOYEE")
    void updatePreference_asEmployee_returnsOk() throws Exception {
        stubCaller();
        NotificationPreference preference = new NotificationPreference();
        preference.setNotificationType(NotificationType.LEAVE);
        preference.setEmailEnabled(true);
        when(notificationService.updatePreference("emp-1", NotificationType.LEAVE, true))
                .thenReturn(preference);

        mockMvc.perform(patch("/notifications/preferences/LEAVE")
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"emailEnabled\":true}"))
                .andExpect(status().isOk());
    }

    @Test
    void getMy_whenUnauthenticated_isForbidden() throws Exception {
        mockMvc.perform(get("/notifications/my"))
                .andExpect(status().isForbidden());
    }
}
