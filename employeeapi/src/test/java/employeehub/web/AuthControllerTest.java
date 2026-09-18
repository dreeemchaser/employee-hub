package employeehub.web;

import employeehub.controller.AuthController;
import employeehub.domain.Employee;
import employeehub.domain.enums.Role;
import employeehub.service.EmployeeService;
import employeehub.service.LoginAttemptService;
import employeehub.service.PasswordResetService;
import employeehub.service.RefreshTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code /auth/**} is {@code permitAll} at the route level, so these tests focus on the response
 * contract rather than authorization. Login/refresh currently return a raw {@code Map}
 * ({@code accessToken}/{@code refreshToken}) — pinned here so a future DTO conversion is a
 * deliberate, test-visible change.
 */
@WebMvcTest(AuthController.class)
class AuthControllerTest extends WebMvcTestSupport {

    @MockitoBean
    EmployeeService employeeService;
    @MockitoBean
    AuthenticationManager authenticationManager;
    @MockitoBean
    LoginAttemptService loginAttemptService;
    @MockitoBean
    PasswordResetService passwordResetService;
    @MockitoBean
    RefreshTokenService refreshTokenService;

    @Test
    void login_withValidCredentials_returnsTokenPair() throws Exception {
        Employee e = new Employee();
        e.setEmail("admin@employeehub.com");
        e.setRole(Role.SUPER_ADMIN);
        when(loginAttemptService.isLocked(anyString())).thenReturn(false);
        when(employeeService.getByEmail("admin@employeehub.com")).thenReturn(e);
        when(jwtUtil.generateToken(anyString(), anyString())).thenReturn("access-jwt");
        when(refreshTokenService.issue(any())).thenReturn("refresh-opaque");

        mockMvc.perform(post("/auth/login").with(csrf())
                        .contentType("application/json")
                        .content("{\"email\":\"admin@employeehub.com\",\"password\":\"Admin@1234\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("access-jwt"))
                .andExpect(jsonPath("$.data.refreshToken").value("refresh-opaque"));
    }

    @Test
    void login_whenAccountLocked_returns423WithRetryAfter() throws Exception {
        when(loginAttemptService.isLocked("locked@employeehub.com")).thenReturn(true);
        when(loginAttemptService.secondsUntilUnlock("locked@employeehub.com")).thenReturn(300L);

        mockMvc.perform(post("/auth/login").with(csrf())
                        .contentType("application/json")
                        .content("{\"email\":\"locked@employeehub.com\",\"password\":\"whatever\"}"))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.data.retryAfterSeconds").value(300));
    }

    @Test
    void refresh_whenTokenInvalid_isUnauthorized() throws Exception {
        when(refreshTokenService.rotate("bad-token")).thenReturn(java.util.Optional.empty());

        mockMvc.perform(post("/auth/refresh").with(csrf())
                        .contentType("application/json")
                        .content("{\"refreshToken\":\"bad-token\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }
}
