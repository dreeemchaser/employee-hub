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
Login and receive an access token plus a refresh token.

**Request Body:**
```json
{
  "email": "jane@example.com",
  "password": "password123"
}
```

**Response (200 OK):** the access token is short-lived (~15 min); the refresh
token is long-lived (~7 days) and rotated on each use.
```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGci...",
    "refreshToken": "8xK3...opaque..."
  }
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

### POST /auth/refresh `[PUBLIC]`
Exchange a valid refresh token for a new access + refresh token pair. The
presented refresh token is revoked (rotation). Presenting an already-revoked
token is treated as compromise and revokes all of the user's active sessions.

**Request Body:**
```json
{ "refreshToken": "8xK3...opaque..." }
```

**Response (200 OK):**
```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGci...",
    "refreshToken": "9pL7...newOpaque..."
  }
}
```

**Response (401 Unauthorized):** the refresh token is unknown, expired, or
revoked. The client should route to login.
```json
{ "success": false, "message": "Invalid or expired refresh token" }
```

---

### POST /auth/logout `[PUBLIC]`
Revoke a refresh token (logout). Idempotent — revoking an unknown or
already-revoked token still returns success.

**Request Body:**
```json
{ "refreshToken": "8xK3...opaque..." }
```

**Response (200 OK):**
```json
{ "success": true, "message": "Logged out successfully" }
```

---

### POST /auth/forgot-password `[PUBLIC]`
Request a password reset link. Always returns the same generic message whether or
not the account exists (no account enumeration). When configured, a reset email
is sent to a known address.

**Request Body:**
```json
{ "email": "jane@example.com" }
```

**Response (200 OK):**
```json
{
  "success": true,
  "message": "If an account exists for that email, a password reset link has been sent."
}
```

---

### POST /auth/reset-password `[PUBLIC]`
Set a new password using a valid reset token from the reset email.

**Request Body:**
```json
{ "token": "the-token-from-the-email", "newPassword": "MyNew@Pass1" }
```

**Response (200 OK):** `{ "success": true, "message": "Password has been reset successfully. You can now sign in." }`

**Errors:** `404` unknown token; `400` expired/used token or password shorter than 8 characters.

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
