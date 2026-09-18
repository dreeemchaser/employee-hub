package employeehub.web;

import employeehub.controller.TeamController;
import employeehub.domain.Team;
import employeehub.service.TeamService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TeamController.class)
class TeamControllerTest extends WebMvcTestSupport {

    @MockitoBean
    TeamService teamService;

    private Team team(Long id, String name) {
        Team t = new Team();
        t.setId(id);
        t.setName(name);
        return t;
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void getAll_asAuthenticated_returnsOk() throws Exception {
        when(teamService.getAll(null)).thenReturn(List.of(team(1L, "Cashiers")));

        mockMvc.perform(get("/teams"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("Cashiers"));
    }

    @Test
    void getAll_whenUnauthenticated_isForbidden() throws Exception {
        mockMvc.perform(get("/teams"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "HR_ADMIN")
    void create_asHrAdmin_returnsCreated() throws Exception {
        when(teamService.create(eq(1L), any())).thenReturn(team(2L, "Support"));

        mockMvc.perform(post("/teams").with(csrf())
                        .param("departmentId", "1")
                        .contentType("application/json")
                        .content("{\"name\":\"Support\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("Support"));
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void create_asEmployee_isForbidden() throws Exception {
        mockMvc.perform(post("/teams").with(csrf())
                        .param("departmentId", "1")
                        .contentType("application/json")
                        .content("{\"name\":\"Support\"}"))
                .andExpect(status().isForbidden());
    }
}
