# API Reference

Base URL: `http://localhost:8080`

> All endpoints require `Authorization: Bearer <token>` unless marked `[PUBLIC]`.  
> See [../../docs/api-contract.md](../../docs/api-contract.md) for the full API contract.

## Authentication

### POST /auth/register `[PUBLIC]`
Register a new user.

**Request Body:**
```json
{
  "username": "jane@example.com",
  "password": "password123"
}
```

**Response (200 OK):**
```json
{
  "token": "eyJhbGci...",
  "username": "jane@example.com"
}
```

### POST /auth/login `[PUBLIC]`
Login and receive a JWT token.

**Request Body:**
```json
{
  "username": "jane@example.com",
  "password": "password123"
}
```

**Response (200 OK):**
```json
{
  "token": "eyJhbGci...",
  "username": "jane@example.com"
}
```

**Response (423 Locked):** returned when the account is temporarily locked after
too many failed logins. Includes a `Retry-After` header (seconds) and:
```json
{
  "success": false,
  "message": "Account is temporarily locked due to repeated failed login attempts. Try again later.",
  "data": { "retryAfterSeconds": 900 }
}
```

---

## Employees

### GET /employees
Get all employees (paginated).

**Query Parameters:**
- `page` (optional): Page number, 0-indexed (default: `0`)
- `size` (optional): Page size (default: `10`)

**Response (200 OK):**
```json
{
  "content": [
    {
      "id": "uuid-string",
      "employeeNumber": "EMP-001",
      "firstName": "Jane",
      "lastName": "Doe",
      "email": "jane@example.com",
      "jobTitle": "Software Engineer",
      "employmentStatus": "ACTIVE",
      "departmentId": 1,
      "department": "Human Resources",
      "teamId": 1,
      "team": "Management",
      "managerId": "uuid-string-or-null",
      "manager": "Alex Smith"
    }
  ],
  "page": {
    "size": 10,
    "number": 0,
    "totalElements": 25,
    "totalPages": 3
  }
}
```

### GET /employees/{id}
Get a single employee by ID.

**Response (200 OK):** Employee object  
**Response (404 Not Found):** Employee not found

### POST /employees
Create a new employee. Requires `HR_ADMIN` or `SUPER_ADMIN` role.

### PUT /employees/{id}
Update an employee. Requires `HR_ADMIN` or `SUPER_ADMIN` role.

### PUT /employees/{id}/photo
Upload an employee profile photo.

**Form Parameters (multipart/form-data):**
- `file`: Image file (JPEG, PNG, GIF)

**Response (200 OK):** Photo URL string

---

## Error Responses

```json
{
  "timestamp": "2024-01-01T12:00:00.000+00:00",
  "status": 404,
  "error": "Not Found",
  "message": "Employee not found",
  "path": "/employees/uuid"
}
```

**401 Unauthorized** — missing or invalid JWT token  
**403 Forbidden** — authenticated but insufficient role
