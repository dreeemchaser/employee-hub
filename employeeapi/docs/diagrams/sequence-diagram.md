# Sequence Diagrams

## Login Flow

```mermaid
sequenceDiagram
    participant Client
    participant AuthController
    participant AuthenticationManager
    participant JwtUtil
    participant Database

    Client->>AuthController: POST /auth/login (username, password)
    AuthController->>AuthenticationManager: authenticate(username, password)
    AuthenticationManager->>Database: SELECT * FROM employees WHERE email = ?
    Database-->>AuthenticationManager: Employee
    AuthenticationManager-->>AuthController: Authentication
    AuthController->>JwtUtil: generateToken(email, role)
    JwtUtil-->>AuthController: access token (JWT)
    AuthController->>RefreshTokenService: issue(employee)
    RefreshTokenService-->>AuthController: refresh token (opaque)
    AuthController-->>Client: 200 OK { accessToken, refreshToken }
```

## Create Employee Flow

```mermaid
sequenceDiagram
    participant Client
    participant JwtAuthFilter
    participant EmployeeController
    participant EmployeeService
    participant EmployeeRepository
    participant Database

    Client->>JwtAuthFilter: POST /employees (Bearer token)
    JwtAuthFilter->>JwtAuthFilter: validate token, set SecurityContext
    JwtAuthFilter->>EmployeeController: forward request
    EmployeeController->>EmployeeService: createEmployee(EmployeeRequest)
    EmployeeService->>EmployeeRepository: save(Employee)
    EmployeeRepository->>Database: INSERT INTO employees
    Database-->>EmployeeRepository: Employee saved
    EmployeeRepository-->>EmployeeService: Employee
    EmployeeService-->>EmployeeController: Employee
    EmployeeController-->>Client: 201 Created (Employee)
```

## Get Employees Flow

```mermaid
sequenceDiagram
    participant Client
    participant JwtAuthFilter
    participant EmployeeController
    participant EmployeeService
    participant EmployeeRepository
    participant Database

    Client->>JwtAuthFilter: GET /employees?page=0&size=10 (Bearer token)
    JwtAuthFilter->>JwtAuthFilter: validate token
    JwtAuthFilter->>EmployeeController: forward request
    EmployeeController->>EmployeeService: getAllEmployees(0, 10)
    EmployeeService->>EmployeeRepository: findAll(PageRequest)
    EmployeeRepository->>Database: SELECT * FROM employees LIMIT 10
    Database-->>EmployeeRepository: Employee list
    EmployeeRepository-->>EmployeeService: Page~Employee~
    EmployeeService-->>EmployeeController: Page~Employee~
    EmployeeController-->>Client: 200 OK (Employee page)
```

## Upload Photo Flow

```mermaid
sequenceDiagram
    participant Client
    participant JwtAuthFilter
    participant EmployeeController
    participant EmployeeService
    participant EmployeeRepository
    participant FileSystem
    participant Database

    Client->>JwtAuthFilter: PUT /employees/{id}/photo (Bearer token, file)
    JwtAuthFilter->>JwtAuthFilter: validate token
    JwtAuthFilter->>EmployeeController: forward request
    EmployeeController->>EmployeeService: uploadPhoto(id, file)
    EmployeeService->>EmployeeRepository: findById(id)
    EmployeeRepository->>Database: SELECT * FROM employees WHERE id = ?
    Database-->>EmployeeRepository: Employee
    EmployeeRepository-->>EmployeeService: Employee
    EmployeeService->>FileSystem: Save file to upload directory (app.upload.directory)
    FileSystem-->>EmployeeService: File saved
    EmployeeService->>EmployeeRepository: save(Employee with profilePhoto)
    EmployeeRepository->>Database: UPDATE employees SET profile_photo = ? WHERE id = ?
    Database-->>EmployeeRepository: Employee updated
    EmployeeRepository-->>EmployeeService: Employee
    EmployeeService-->>EmployeeController: photoURL
    EmployeeController-->>Client: 200 OK (photoURL)
```

These sequence diagrams illustrate the main interaction flows in the Employee API. The JWT filter runs on every request to authenticate the caller before the request reaches the controller.
