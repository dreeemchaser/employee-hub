# Security

## Current Security Status

JWT-based authentication and role-based access control are implemented. All endpoints except `/auth/register` and `/auth/login` require a valid Bearer token.

## Roles

| Role | Description |
|------|-------------|
| `SUPER_ADMIN` | Full system access |
| `HR_ADMIN` | Manages all employees and approvals |
| `PAYROLL_ADMIN` | Salary, tax and benefits management |
| `MANAGER` | Manages their team |
| `EMPLOYEE` | Self-service access to own data |

## Authentication Flow

```
POST /auth/login → JWT token returned
      ↓
Authorization: Bearer <token> on every request
      ↓
JwtAuthFilter validates token
      ↓
SecurityContextHolder stores authenticated employee
      ↓
Controller enforces role-based access
```

## Account Lockout

Repeated failed logins temporarily lock an account to slow brute-force attempts.

- After a configurable number of consecutive failed logins (`AUTH_LOCKOUT_MAX_ATTEMPTS`, default **5**), the account is locked for a cooldown window (`AUTH_LOCKOUT_DURATION_MINUTES`, default **15**).
- While locked, `POST /auth/login` returns **423 Locked** — even with the correct password — with a `Retry-After` header (seconds) and a `retryAfterSeconds` field in the response body.
- A successful login resets the failed-attempt counter and clears any lock.
- Tracking lives on the `Employee` entity (`failedLoginAttempts`, `lockedUntil`) and is applied only in the login flow (`LoginAttemptService`), so already-issued valid tokens are unaffected.

Configuration (`application.yml` → `app.security.lockout`), overridable via environment:

| Env var | Default | Meaning |
|---------|---------|---------|
| `AUTH_LOCKOUT_MAX_ATTEMPTS` | 5 | Consecutive failures before lock |
| `AUTH_LOCKOUT_DURATION_MINUTES` | 15 | Lock duration once triggered |

## Security Implementation

See [security-implementation.md](security-implementation.md) for the full step-by-step implementation guide.

Key components:
- `JwtUtil` — token generation, parsing, and validation
- `JwtAuthFilter` — intercepts every request and validates the Bearer token
- `SecurityConfig` — role-based access rules per endpoint
- `UserDetailsServiceImpl` — loads employees from DB for Spring Security

## Security Checklist

### Implemented
- ✅ JWT authentication
- ✅ Password hashing with bcrypt
- ✅ Role-based access control per endpoint
- ✅ Stateless session management
- ✅ CORS configuration
- ✅ Global exception handler (no internal details leaked)
- ✅ Audit logging across all modules
- ✅ Account lockout after repeated failed logins

### Pre-Production Recommendations
- [ ] HTTPS/TLS configured
- [ ] File upload type and size validation hardened
- [ ] Rate limiting configured
- [ ] Security headers added (X-Content-Type-Options, X-Frame-Options, HSTS)
- [ ] Database credentials secured via secrets manager
- [ ] Dependencies scanned for vulnerabilities (`./mvnw org.owasp:dependency-check-maven:check`)

## CORS Configuration

Configured in `Config.java` to allow the frontend origin:
```
allowedOrigins  → "http://localhost:3000"
allowedMethods  → "GET", "POST", "PUT", "DELETE", "OPTIONS"
allowedHeaders  → "Authorization", "Content-Type"
allowCredentials → true
```

## Database Security

JPA automatically handles SQL injection prevention via parameterized queries.

```sql
-- Application DB user (principle of least privilege)
CREATE USER employeehub_app WITH PASSWORD 'secure_password';
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO employeehub_app;
```

## Security Testing

```bash
# Register
curl -X POST http://localhost:8080/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username": "admin", "password": "password123"}'

# Login — copy the token
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username": "admin", "password": "password123"}'

# Access protected endpoint with token
curl http://localhost:8080/employees \
  -H "Authorization: Bearer <your-token>"

# Access without token — should return 401
curl http://localhost:8080/employees
```

## Resources

- [OWASP Top 10](https://owasp.org/www-project-top-ten/)
- [Spring Security Documentation](https://spring.io/projects/spring-security)
- [JWT Best Practices](https://tools.ietf.org/html/rfc8725)
