# Class Diagram

```mermaid
classDiagram
    class Application {
        +main(String[] args)
    }

    class Employee {
        -UUID id
        -String employeeNumber
        -String firstName
        -String lastName
        -String email
        -String jobTitle
        -EmploymentType employmentType
        -EmploymentStatus employmentStatus
        -Role role
        -String profilePhoto
        -UUID departmentId
        -UUID teamId
        -UUID managerId
        +getId()
        +getEmail()
        +getRole()
        +getEmploymentStatus()
    }

    class EmployeeRepository {
        +findAll(Pageable) Page~Employee~
        +findById(UUID) Optional~Employee~
        +findByEmail(String) Optional~Employee~
        +save(Employee) Employee
        +deleteById(UUID)
    }

    class EmployeeService {
        -EmployeeRepository employeeRepository
        +getAllEmployees(int, int) Page~Employee~
        +getEmployee(UUID) Employee
        +createEmployee(EmployeeRequest) Employee
        +updateEmployee(UUID, EmployeeRequest) Employee
        +uploadPhoto(UUID, MultipartFile) String
    }

    class EmployeeController {
        -EmployeeService employeeService
        +createEmployee(EmployeeRequest) ResponseEntity~Employee~
        +getEmployees(int, int) ResponseEntity~Page~Employee~~
        +getEmployee(UUID) ResponseEntity~Employee~
        +uploadPhoto(UUID, MultipartFile) ResponseEntity~String~
    }

    class AuthController {
        -AuthenticationManager authManager
        -JwtUtil jwtUtil
        -RefreshTokenService refreshTokenService
        +login(Map) ResponseEntity~ApiResponse~
        +refresh(RefreshRequest) ResponseEntity~ApiResponse~
        +logout(RefreshRequest) ResponseEntity~ApiResponse~
        +me(UserDetails) ResponseEntity~ApiResponse~
    }

    class JwtUtil {
        +generateToken(String email, String role) String
        +extractUsername(String) String
        +isTokenValid(String, UserDetails) boolean
    }

    class SecurityConfig {
        +securityFilterChain(HttpSecurity) SecurityFilterChain
        +passwordEncoder() BCryptPasswordEncoder
        +authenticationManager() AuthenticationManager
    }

    Application --> EmployeeService
    EmployeeService --> EmployeeRepository
    EmployeeRepository --> Employee
    EmployeeController --> EmployeeService
    AuthController --> JwtUtil
    SecurityConfig --> JwtUtil
```

This diagram shows the core classes and their relationships in the Employee API. The application uses Spring Boot's dependency injection to wire components together. The `Employee` entity is annotated with JPA annotations for database persistence. `JwtUtil` and `SecurityConfig` handle authentication and authorization.
