package employeehub.web;

import employeehub.controller.AttendanceController;
import employeehub.domain.Employee;
import employeehub.exception.BusinessRuleException;
import employeehub.service.AttendanceService;
import employeehub.service.EmployeeService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AttendanceController.class)
class AttendanceControllerTest extends WebMvcTestSupport {

    @MockitoBean
    AttendanceService attendanceService;
    @MockitoBean
    EmployeeService employeeService;

    private void stubCaller() {
        Employee e = new Employee();
        e.setId("emp-1");
        when(employeeService.getByEmail(any())).thenReturn(e);
    }

    @Test
    @WithMockUser(username = "user@employeehub.com", roles = "EMPLOYEE")
    void clockIn_asEmployee_returnsCreated() throws Exception {
        stubCaller();
        employeehub.domain.AttendanceRecord record = new employeehub.domain.AttendanceRecord();
        record.setEmployee(new Employee());
        when(attendanceService.clockIn(any())).thenReturn(record);

        mockMvc.perform(post("/attendance/clock-in").with(csrf()))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(username = "user@employeehub.com", roles = "EMPLOYEE")
    void clockIn_asEmployee_whenAlreadyClockedIn_returnsConflict() throws Exception {
        stubCaller();
        when(attendanceService.clockIn(any())).thenThrow(new BusinessRuleException("Already clocked in since ..."));

        mockMvc.perform(post("/attendance/clock-in").with(csrf()))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(username = "user@employeehub.com", roles = "EMPLOYEE")
    void clockOut_asEmployee_returnsOk() throws Exception {
        stubCaller();
        employeehub.domain.AttendanceRecord record = new employeehub.domain.AttendanceRecord();
        record.setEmployee(new Employee());
        when(attendanceService.clockOut(any(), any())).thenReturn(record);

        mockMvc.perform(patch("/attendance/clock-out").with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "user@employeehub.com", roles = "EMPLOYEE")
    void getMy_asEmployee_returnsOk() throws Exception {
        stubCaller();
        when(attendanceService.getMy(any(), any())).thenReturn(Page.empty());

        mockMvc.perform(get("/attendance/my"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "user@employeehub.com", roles = "EMPLOYEE")
    void getAll_asEmployee_isForbidden() throws Exception {
        // GET /attendance is HR_ADMIN/SUPER_ADMIN/MANAGER only.
        mockMvc.perform(get("/attendance"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "mgr@employeehub.com", roles = "MANAGER")
    void getAll_asManager_returnsOk() throws Exception {
        stubCaller();
        when(attendanceService.getAll(any(), any(), any(), any(), any())).thenReturn(Page.empty());

        mockMvc.perform(get("/attendance"))
                .andExpect(status().isOk());
    }
}
