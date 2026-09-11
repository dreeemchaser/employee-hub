package employeehub.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final UserDetailsServiceImpl userDetailsService;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        var provider = daoAuthenticationProvider();
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> {})
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/auth/**").permitAll()
                        .requestMatchers(
                                "/swagger-ui/**", "/swagger-ui.html",
                                "/v3/api-docs/**", "/actuator/health",
                                "/employees/photo/**"
                        ).permitAll()
                        .requestMatchers(HttpMethod.GET, "/departments/**", "/teams/**").authenticated()
                        .requestMatchers("/departments/**", "/teams/**").hasAnyRole("HR_ADMIN", "SUPER_ADMIN")
                        .requestMatchers(HttpMethod.GET, "/employees/**").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/employees/**").hasAnyRole("HR_ADMIN", "SUPER_ADMIN")
                        .requestMatchers(HttpMethod.POST, "/employees").hasAnyRole("HR_ADMIN", "SUPER_ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/employees/**").hasAnyRole("HR_ADMIN", "SUPER_ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/employees/**").hasAnyRole("HR_ADMIN", "SUPER_ADMIN")
                        .requestMatchers(HttpMethod.GET, "/leave/requests").hasAnyRole("HR_ADMIN", "SUPER_ADMIN", "MANAGER")
                        .requestMatchers(HttpMethod.PATCH, "/leave/requests/*/approve", "/leave/requests/*/reject").hasAnyRole("HR_ADMIN", "SUPER_ADMIN", "MANAGER")
                        .requestMatchers(HttpMethod.GET, "/timesheets").hasAnyRole("HR_ADMIN", "SUPER_ADMIN", "MANAGER")
                        .requestMatchers(HttpMethod.PATCH, "/timesheets/*/approve").hasAnyRole("HR_ADMIN", "SUPER_ADMIN", "MANAGER")
                        .requestMatchers(HttpMethod.GET, "/documents").hasAnyRole("HR_ADMIN", "SUPER_ADMIN")
                        .requestMatchers("/documents/{id}/verify", "/documents/{id}/reject").hasAnyRole("HR_ADMIN", "SUPER_ADMIN")
                        .requestMatchers("/audit-logs/**").hasAnyRole("HR_ADMIN", "SUPER_ADMIN")
                        .requestMatchers("/dashboard/**").hasAnyRole("HR_ADMIN", "SUPER_ADMIN")
                        .requestMatchers(HttpMethod.GET, "/salary/payslips/my").authenticated()
                        .requestMatchers(HttpMethod.GET, "/salary/increase-requests/my").authenticated()
                        .requestMatchers(HttpMethod.POST, "/salary/increase-requests").authenticated()
                        .requestMatchers("/salary/**").hasAnyRole("PAYROLL_ADMIN", "HR_ADMIN", "SUPER_ADMIN")
                        .anyRequest().authenticated()
                )
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authenticationProvider(provider)
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    // NOT a @Bean — keeps it out of Spring's global AuthenticationManager auto-config
    private DaoAuthenticationProvider daoAuthenticationProvider() {
        var provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager() {
        return new ProviderManager(daoAuthenticationProvider());
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
