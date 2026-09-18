package employeehub.web;

import employeehub.controller.HealthController;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HealthController.class)
class HealthControllerTest extends WebMvcTestSupport {

    @Test
    @WithMockUser
    void health_whenAuthenticated_returnsUp() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.service").value("EmployeeHub"));
    }

    @Test
    void health_whenUnauthenticated_isForbidden() throws Exception {
        // /health (custom endpoint) is NOT in permitAll — only /actuator/health is public.
        // It therefore falls under anyRequest().authenticated() → 403 when unauthenticated.
        mockMvc.perform(get("/health"))
                .andExpect(status().isForbidden());
    }
}
