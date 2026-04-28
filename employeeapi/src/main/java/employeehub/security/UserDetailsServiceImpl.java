package employeehub.security;

import employeehub.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final EmployeeRepository employeeRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return employeeRepository.findByEmail(email)
                .map(emp -> new User(
                        emp.getEmail(),
                        emp.getPassword() != null ? emp.getPassword() : "",
                        List.of(new SimpleGrantedAuthority("ROLE_" + emp.getRole().name()))
                ))
                .orElseThrow(() -> new UsernameNotFoundException("Employee not found: " + email));
    }
}
