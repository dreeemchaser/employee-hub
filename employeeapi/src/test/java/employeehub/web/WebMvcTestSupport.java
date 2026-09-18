package employeehub.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import employeehub.security.JwtAuthFilter;
import employeehub.security.JwtUtil;
import employeehub.security.SecurityConfig;
import employeehub.security.UserDetailsServiceImpl;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Shared wiring for {@code @WebMvcTest} slices.
 *
 * <p>{@code @WebMvcTest} does not load the application's {@link SecurityConfig}, and the real
 * {@link JwtAuthFilter} depends on {@link JwtUtil} and {@link UserDetailsServiceImpl}. We import the
 * real security config so the route-level authorization rules are exercised, and mock the filter and
 * its collaborators so the filter chain builds without real JWT parsing. Authentication in tests is
 * driven with {@code @WithMockUser} / {@code SecurityMockMvcRequestPostProcessors}, not bearer tokens —
 * JWT parsing itself is covered by {@code JwtUtilTest} / {@code JwtAuthFilterTest}.
 *
 * <p>A concrete controller test extends this and adds its own {@code @WebMvcTest(XController.class)}
 * annotation plus the service(s) it needs as {@link MockitoBean}.
 */
@Import(SecurityConfig.class)
public abstract class WebMvcTestSupport {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    // Satisfy SecurityConfig's constructor dependencies. The mocked filter is a no-op pass-through,
    // so authorization is decided by SecurityConfig's rules against the @WithMockUser principal.
    @MockitoBean
    protected JwtAuthFilter jwtAuthFilter;

    @MockitoBean
    protected UserDetailsServiceImpl userDetailsService;

    @MockitoBean
    protected JwtUtil jwtUtil;

    /**
     * The mocked filter must still forward the request down the chain, otherwise every request would
     * hang at the (do-nothing) mock instead of reaching Spring Security's authorization filter.
     */
    @BeforeEach
    void passThroughJwtFilter() throws Exception {
        Mockito.doAnswer(invocation -> {
            ServletRequest request = invocation.getArgument(0);
            ServletResponse response = invocation.getArgument(1);
            FilterChain chain = invocation.getArgument(2);
            chain.doFilter(request, response);
            return null;
        }).when(jwtAuthFilter).doFilter(Mockito.any(), Mockito.any(), Mockito.any());
    }
}
