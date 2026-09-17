# Security Implementation Guide

## Overview

This document covers the JWT (JSON Web Token) based authentication implemented in the Employee API using Spring Security. All endpoints except `/auth/register` and `/auth/login` are protected — only users who have registered and logged in with a valid token can access them.

### What is JWT?

JWT is a compact, self-contained token format used to securely transmit information between parties. When a user logs in, the server generates a signed token containing the user's identity. The client stores this token and sends it with every request. The server verifies the token's signature — no database lookup needed on every request.

A JWT has three parts separated by dots:
```
header.payload.signature
```
- **Header** — algorithm used to sign the token (e.g. HS256)
- **Payload** — claims (e.g. username, expiry time)
- **Signature** — cryptographic proof the token hasn't been tampered with

### What is Spring Security?

Spring Security is a framework that plugs into the Spring request lifecycle. It intercepts every incoming HTTP request through a chain of filters before it reaches your controllers. You configure rules that define which endpoints are public and which require authentication. When a request comes in, Spring Security checks those rules and either allows or rejects the request.

### How They Work Together

```
Incoming Request
      ↓
JwtAuthenticationFilter  ← your custom filter
      ↓ (extracts + validates token)
SecurityContextHolder    ← stores authenticated user for this request
      ↓
Controller               ← only reached if authentication passed
```

---

## What You Are Building

You are adding:
1. A `User` table in the database to store registered users
2. A `/auth/register` endpoint to create new users
3. A `/auth/login` endpoint that returns a JWT token
4. A filter that intercepts every request, reads the token, and authenticates the user
5. Security rules that protect all employee endpoints behind authentication

---

## Step 1 — Add Dependencies (`pom.xml`)

### What and Why

You need four new dependencies:

- **`spring-boot-starter-security`** — the core Spring Security library. Adding this to your project immediately activates security. By default it locks down everything, which is why you need to configure it in Step 8.
- **`jjwt-api`** — the JJWT library API for creating and parsing JWTs.
- **`jjwt-impl`** — the runtime implementation of the JJWT API. Marked as `runtime` scope because you only need it at runtime, not compile time.
- **`jjwt-jackson`** — handles JSON serialization/deserialization of JWT payloads using Jackson (which Spring Boot already uses).

### What to Add

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.12.6</version>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-impl</artifactId>
    <version>0.12.6</version>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-jackson</artifactId>
    <version>0.12.6</version>
    <scope>runtime</scope>
</dependency>
```

### Important Note

The moment you add `spring-boot-starter-security` and restart, **every endpoint will return 401**. This is expected. You will configure the rules in Step 8 to open up the auth endpoints and protect the rest.

---

## Step 2 — Create the User Entity and Repository

### What and Why

You need a place to store users. This means a new `User` JPA entity that maps to a `users` table in PostgreSQL. Hibernate will create this table automatically on startup (since `ddl-auto: update` is already configured).

The `User` class must implement Spring Security's `UserDetails` interface. This is the contract Spring Security uses to understand a user — it expects methods like `getUsername()`, `getPassword()`, and `getAuthorities()`. By implementing it on your entity, Spring Security can work directly with your database users.

### `domain/User.java`

Fields:
- `id` — UUID primary key, auto-generated
- `username` — unique, used for login
- `password` — stored as a bcrypt hash (never plain text)
- `role` — e.g. `ROLE_USER` or `ROLE_ADMIN`, used for authorization

Implement all `UserDetails` methods:
- `getAuthorities()` — return the user's role as a `GrantedAuthority`
- `isAccountNonExpired()`, `isAccountNonLocked()`, `isCredentialsNonExpired()`, `isEnabled()` — return `true` for all (you can add logic later)

### `repository/UserRepository.java`

Extend `JpaRepository<User, String>` and add:
```java
Optional<User> findByUsername(String username);
```

Spring Data JPA generates the query automatically from the method name. This is used by `UserDetailsService` in Step 5 to look up users during authentication.

---

## Step 3 — Create the JWT Utility Class

### What and Why

This is the core of your JWT implementation. `JwtUtil` is a service class responsible for everything JWT-related: creating tokens, reading them, and validating them.

Tokens are signed using a secret key stored in `application.yml`. The signature ensures that if anyone tampers with the token payload (e.g. changes the username), the signature will no longer match and the token will be rejected.

### `security/JwtUtil.java`

**Methods to implement:**

`generateToken(UserDetails user)`
- Creates a new JWT
- Sets the subject to the username
- Sets issued-at to now
- Sets expiry based on `jwt.expiration` from config
- Signs it with your secret key using HMAC-SHA256

`extractUsername(String token)`
- Parses the token
- Returns the subject (username) from the payload

`isTokenExpired(String token)`
- Extracts the expiry claim
- Returns true if the expiry is before the current time

`isTokenValid(String token, UserDetails user)`
- Checks the username in the token matches the provided user
- Checks the token is not expired

### Add to `application.yml`

```yaml
jwt:
  secret: <your-256-bit-secret-key>
  access-expiration: 900000     # 15 min — short-lived access token
  refresh-expiration: 604800000 # 7 days — long-lived refresh token
```

- `secret` — must be at least 256 bits (32 characters) for HMAC-SHA256. Use a long random string. Keep this private — anyone with this key can forge tokens.
- `access-expiration` — access-token lifetime in milliseconds. `900000` = 15 minutes. Falls back to the legacy `JWT_EXPIRATION` env override if set.
- `refresh-expiration` — refresh-token lifetime in milliseconds. `604800000` = 7 days.

### Refresh tokens (B.5b)

The access token is deliberately short-lived; clients renew it silently using a
**refresh token**:

- On login, the API returns `{ accessToken, refreshToken }`. The refresh token is
  an opaque, high-entropy random string — **not** a JWT.
- Only the **SHA-256 hash** of the refresh token is stored (`refresh_tokens`
  table). A database leak therefore does not expose usable tokens.
- `POST /auth/refresh` validates the presented token, **revokes it, and issues a
  new pair** (rotation). Presenting an already-revoked token is treated as
  compromise and revokes all of that user's active tokens.
- `POST /auth/logout` revokes the refresh token server-side.
- Handled by `RefreshTokenService`; the raw token value only ever exists in the
  login/refresh response body and is never logged.

---

## Step 4 — Create the JWT Authentication Filter

### What and Why

Spring Security processes requests through a filter chain. You need to insert your own filter into this chain that runs on every request. Its job is to:

1. Look for a JWT in the `Authorization` header
2. If found, validate it
3. If valid, tell Spring Security who the user is

Once Spring Security knows who the user is (via `SecurityContextHolder`), it can apply your access rules from Step 8.

If no token is present or the token is invalid, the filter does nothing — Spring Security will then apply its default behaviour (reject the request with 401 if the endpoint requires authentication).

### `security/JwtAuthenticationFilter.java`

Extend `OncePerRequestFilter` — this guarantees the filter runs exactly once per request.

**Logic inside `doFilterInternal`:**

1. Read the `Authorization` header from the request
2. If it's null or doesn't start with `Bearer `, skip to the next filter (do nothing)
3. Extract the token (everything after `Bearer `)
4. Extract the username from the token using `JwtUtil`
5. Check that `SecurityContextHolder` doesn't already have an authentication (avoid re-processing)
6. Load the user from `UserDetailsService` using the username
7. Call `isTokenValid()` — if valid, create a `UsernamePasswordAuthenticationToken` and set it in `SecurityContextHolder`
8. Call `filterChain.doFilter()` to continue the chain

---

## Step 5 — Implement UserDetailsService

### What and Why

`UserDetailsService` is a Spring Security interface with one method: `loadUserByUsername`. Spring Security calls this internally during authentication to fetch the user from your data source.

You need to implement it so it loads users from your PostgreSQL database via `UserRepository`.

### `security/UserDetailsServiceImpl.java`

Implement `UserDetailsService`:

```
loadUserByUsername(String username)
  → call userRepository.findByUsername(username)
  → if not found, throw UsernameNotFoundException
  → return the User entity (it implements UserDetails)
```

Annotate the class with `@Service` so Spring picks it up automatically.

---

## Step 6 — Create Auth DTOs

### What and Why

DTOs (Data Transfer Objects) are simple classes that represent the shape of request and response bodies for your auth endpoints. Keeping them separate from your domain entities is good practice — you don't want to expose your full `User` entity over the API.

### Files to Create

**`dto/LoginRequest.java`**
```
String username
String password
```

**`dto/RegisterRequest.java`**
```
String username
String password
```

**`dto/AuthResponse.java`**
```
String token
String username
```

Use Lombok's `@Data` and `@AllArgsConstructor` to keep them concise.

---

## Step 7 — Create the Auth Controller

### What and Why

This controller exposes the two public endpoints users interact with to get a token. These endpoints must be publicly accessible (no token required) — you will configure that in Step 8.

### `controller/AuthController.java`

**POST `/auth/register`**

What it does:
1. Receives a `RegisterRequest` (username + password)
2. Checks if the username is already taken — return `400` if so
3. Hashes the password using `BCryptPasswordEncoder` — never store plain text passwords
4. Creates and saves a new `User` entity
5. Generates a JWT token for the new user
6. Returns an `AuthResponse` with the token and username

**POST `/auth/login`**

What it does:
1. Receives a `LoginRequest` (username + password)
2. Uses Spring Security's `AuthenticationManager` to authenticate — this internally calls `UserDetailsService` to load the user and `BCryptPasswordEncoder` to verify the password
3. If authentication fails, Spring Security throws `BadCredentialsException` — catch it and return `401`
4. If successful, generate a JWT token
5. Return an `AuthResponse` with the token and username

---

## Step 8 — Configure Spring Security

### What and Why

This is the most important step. `SecurityConfig` is where you define the rules for your entire API:
- Which endpoints are public
- Which require authentication
- How requests are processed (stateless, no sessions)
- Which filters run and in what order

Since REST APIs are stateless, you disable sessions entirely. Every request must carry its own token — the server never stores session state.

### `config/SecurityConfig.java`

Annotate with `@Configuration` and `@EnableWebSecurity`.

**Security filter chain configuration:**

```
Disable CSRF
  → REST APIs don't use browser cookies for auth, so CSRF attacks don't apply

Session management = STATELESS
  → No HttpSession created or used. Every request is independent.

Authorisation rules:
  → Permit all: POST /auth/register
  → Permit all: POST /auth/login
  → Permit all: GET /actuator/health
  → Permit all: GET /swagger-ui/**
  → Permit all: GET /v3/api-docs/**
  → All other requests → authenticated (role-based rules per endpoint)

Add JwtAuthenticationFilter before UsernamePasswordAuthenticationFilter
  → Your filter runs first, sets the authentication context
  → Spring Security's built-in filter then sees the context is already set
```

**Beans to expose:**

`BCryptPasswordEncoder` — used in `AuthController` to hash passwords and in `AuthenticationManager` to verify them. Must be a Spring bean so it can be injected.

`AuthenticationManager` — Spring Security's built-in manager that orchestrates authentication. You expose it as a bean so `AuthController` can call it directly in the login flow.

---

## Step 9 — Update CORS Configuration

### What and Why

CORS (Cross-Origin Resource Sharing) controls which domains can make requests to your API from a browser. Your existing `Config.java` likely allows all origins (`*`). Now that you have security in place, you should restrict this to only your frontend's URL.

Also ensure the `Authorization` header is in the allowed headers list — browsers need permission to send it in cross-origin requests.

### Update `config/Config.java`

```
allowedOrigins  → "http://localhost:3000", "http://localhost:3001" (frontend URLs)
allowedMethods  → "GET", "POST", "PUT", "DELETE", "OPTIONS"
allowedHeaders  → "Authorization", "Content-Type"
allowCredentials → true
```

---

## Step 10 — Update `application.yml`

### What and Why

Add the JWT config values and tighten error responses so your API doesn't leak internal information in error messages.

```yaml
jwt:
  secret: <your-secret-key>
  expiration: 86400000

server:
  error:
    include-message: never
    include-binding-errors: never
```

`include-message: never` prevents Spring from including exception messages in error responses — these can expose internal implementation details to attackers.

---

## Step 11 — Update Swagger for JWT

### What and Why

Once security is active, Swagger UI won't be able to test protected endpoints unless it can send the `Authorization` header. You need to add a security scheme to your OpenAPI config so Swagger shows an **Authorize** button where you can paste your token.

### Update `config/OpenApiConfiguration.java`

Add a `SecurityScheme` of type `HTTP` with scheme `bearer` and bearer format `JWT`.

Apply it globally using `addSecurityItem` so every endpoint in Swagger UI shows the lock icon and uses the token you provide.

---

## Step 12 — Test the Full Flow

### Manual Testing via Swagger UI

1. Open http://localhost:8080/swagger-ui.html
2. Call `POST /auth/register` with a username and password
3. Call `POST /auth/login` with the same credentials — copy the token from the response
4. Click the **Authorize** button (top right of Swagger UI)
5. Enter `Bearer <your-token>` and click Authorize
6. Now test any employee endpoint — it should return data
7. Try an employee endpoint without authorizing — it should return `401`

### Manual Testing via curl

```bash
# Register
curl -X POST http://localhost:8080/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username": "admin", "password": "password123"}'

# Login — copy the token from the response
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username": "admin", "password": "password123"}'

# Access protected endpoint with token
curl http://localhost:8080/employees \
  -H "Authorization: Bearer <your-token>"

# Access without token — should return 401
curl http://localhost:8080/employees
```

---

## File Summary

| File | Action | Purpose |
|------|--------|---------|
| `pom.xml` | Update | Add Spring Security and JJWT dependencies |
| `domain/User.java` | New | UserDetails entity mapped to `users` table |
| `repository/UserRepository.java` | New | DB access for users, findByUsername |
| `security/JwtUtil.java` | New | Token generation, parsing, and validation |
| `security/JwtAuthenticationFilter.java` | New | Intercepts requests and authenticates via token |
| `security/UserDetailsServiceImpl.java` | New | Loads users from DB for Spring Security |
| `dto/LoginRequest.java` | New | Request body for /auth/login |
| `dto/RegisterRequest.java` | New | Request body for /auth/register |
| `dto/AuthResponse.java` | New | Response body containing token |
| `controller/AuthController.java` | New | /auth/register and /auth/login endpoints |
| `config/SecurityConfig.java` | New | Security rules, filter chain, session policy |
| `config/Config.java` | Update | Restrict CORS to frontend origin |
| `config/OpenApiConfiguration.java` | Update | Add Bearer token scheme to Swagger |
| `application.yml` | Update | Add jwt.secret, jwt.expiration, tighten errors |

---

## Key Concepts Summary

| Concept | What It Does |
|---------|-------------|
| `UserDetails` | Spring Security's interface for representing a user |
| `UserDetailsService` | Tells Spring Security how to load a user by username |
| `BCryptPasswordEncoder` | Hashes passwords — never store plain text |
| `AuthenticationManager` | Orchestrates the login authentication process |
| `OncePerRequestFilter` | A filter that runs exactly once per HTTP request |
| `SecurityContextHolder` | Stores the authenticated user for the current request |
| `JWT` | A signed token the client sends with every request to prove identity |
| `STATELESS` session | No server-side session — every request is self-contained |
