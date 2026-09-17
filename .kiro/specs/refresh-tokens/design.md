# Refresh Tokens (B.5b) — Design

Grounded in the current auth code:
- `AuthController` (`/auth`) — login returns `ApiResponse.ok(Map.of("token", token))`; already wires `JwtUtil`, `LoginAttemptService`, `PasswordResetService`.
- `JwtUtil` — HS256 access tokens, `generateToken(email, role)`, `${jwt.secret}` / `${jwt.expiration}`.
- `JwtAuthFilter` — validates the access token per request (unchanged by this work).
- `SecurityConfig` — `/auth/**` is `permitAll()`, so new `/auth/*` endpoints need no new rule.
- B.5a pattern to mirror: `PasswordResetService` + a persisted token entity + repository. Refresh follows the same shape.

## Contract changes

### Login response (R2)
`POST /auth/login` success body changes from `{ "token": "<jwt>" }` to:
```json
{ "accessToken": "<jwt>", "refreshToken": "<opaque>" }
```
Both frontends read `res.data.data.token` today → they must be updated together (R6). This is the one breaking contract change; keep it in the same feature so backend and both apps move as a unit.

### New endpoints (all under existing `/auth/**` permitAll)
- `POST /auth/refresh` — body `{ "refreshToken": "<opaque>" }` → `200 { accessToken, refreshToken }` (rotated) or `401`.
- `POST /auth/logout` — body `{ "refreshToken": "<opaque>" }` → `200 ApiResponse.ok("Logged out", null)` (idempotent).

All responses wrapped in `ApiResponse<T>` per controller conventions.

## Backend components

### 1. `RefreshToken` entity — `domain/RefreshToken.java`
Follows entity conventions (UUID id, `@CreationTimestamp`, `@EqualsAndHashCode(of = "id")`).
- `String id` — `@GeneratedValue(strategy = GenerationType.UUID)`
- `String tokenHash` — SHA-256 of the raw token, unique index. **Never store the raw token.**
- `@ManyToOne(fetch = LAZY) Employee employee` — with `@JsonIgnoreProperties({"hibernateLazyInitializer","handler","password","manager"})` (matches self-ref convention). Entity is never serialized directly, but annotate defensively.
- `LocalDateTime expiresAt`
- `boolean revoked = false`
- `@CreationTimestamp LocalDateTime createdAt`
- `@Table(name = "refresh_tokens")`

### 2. `RefreshTokenRepository` — `repository/RefreshTokenRepository.java`
`JpaRepository<RefreshToken, String>`:
- `Optional<RefreshToken> findByTokenHash(String tokenHash)`
- `List<RefreshToken> findAllByEmployeeIdAndRevokedFalse(String employeeId)` (for revoke-all / reuse response)
- `@Modifying @Query` update to revoke all active tokens for an employee (bulk revoke on reuse detection).

### 3. `RefreshTokenService` — `service/RefreshTokenService.java`
`@Service @RequiredArgsConstructor`. Deps: `RefreshTokenRepository`, `JwtUtil`.
- `String issue(Employee employee)` [`@Transactional`] — generate 256-bit random via `SecureRandom` → Base64URL raw token; persist SHA-256 hash + expiry (config TTL). Return the RAW token (only place it exists in plaintext).
- `Optional<TokenPair> rotate(String rawRefreshToken)` [`@Transactional`] — hash + look up. If not found or expired → return `Optional.empty()`. If revoked → **reuse detected**: bulk-revoke all of that employee's active tokens, return `Optional.empty()`. Else: revoke the presented token, issue a new refresh token, mint a new access token via `jwtUtil.generateToken(email, role)`, return `Optional.of(pair)`. (See "Error handling — DECIDED: Option A".)
- `void revoke(String rawRefreshToken)` [`@Transactional`] — hash + look up; mark revoked if present. Idempotent (no-op if absent/already revoked).
- Hashing helper: `sha256Base64(raw)` — deterministic, no salt (lookup by hash requires determinism; entropy comes from the 256-bit token, not a KDF).

`TokenPair` — small record/DTO `(String accessToken, String refreshToken)`.

### 4. Access-token TTL (R1)
- Add `jwt.access-expiration` (default 900000 = 15m) and `jwt.refresh-expiration` (default 604800000 = 7d) to `application.yml`.
- Simplest path: repoint `JwtUtil.expiration` binding to the access TTL key, keeping `generateToken` untouched. Refresh TTL is read by `RefreshTokenService` via `@Value`. Document the rename so docker-compose env vars are updated in lockstep.

### 5. `AuthController` wiring
- Inject `RefreshTokenService`.
- `login`: after `recordSuccess`, build `accessToken` (existing) + `refreshToken = refreshTokenService.issue(employee)`; return both.
- Add `refresh(@Valid @RequestBody RefreshRequest)`: `refreshTokenService.rotate(req.getRefreshToken())` → `.map(pair -> ResponseEntity.ok(ApiResponse.ok(...))).orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("Invalid or expired refresh token")))`. Mirrors the `lockedResponse` state-based return already in this controller.
- Add `logout(@Valid @RequestBody RefreshRequest)`: `refreshTokenService.revoke(...)` then `ResponseEntity.ok(ApiResponse.ok("Logged out", null))` (idempotent).
- Both with `@Operation` summaries. No try/catch — unexpected errors go to `GlobalExceptionHandler`.

### Error handling / status mapping — DECIDED: Option A
Refresh failures must surface as **401**, so the frontend interceptor can distinguish "refresh dead → go to login".

**Chosen approach (Option A):** `RefreshTokenService.rotate(rawToken)` returns `Optional<TokenPair>` — empty means invalid/expired/revoked. The controller returns **401** on empty and 200 with the pair otherwise. This:
- gives correct 401 semantics (not 400),
- adds **no new exception type** (respects best-practices steering),
- mirrors the existing precedent in `AuthController.login`, which already returns `lockedResponse(...)` (423) directly from the controller based on state — so returning a status from presence/absence is consistent, not new business logic in the controller.

Reuse detection (bulk-revoke all of the employee's active tokens) still happens *inside* `rotate()` before it returns empty. Truly unexpected failures still propagate to `GlobalExceptionHandler`.

Rejected alternatives: throwing `IllegalArgumentException` → 400 (weak semantics for an auth failure); adding a custom 401 exception (violates the no-new-exception-types rule).

## Frontend components (R6)

Shared shape across both apps; adapt to each app's `AuthService`.

### `AuthService.js` (both apps)
- `login()` stores `accessToken` + `refreshToken` (localStorage keys, e.g. `token` for access to minimize churn, `refreshToken` new).
- `getToken()` returns the access token (unchanged callers).
- `logout()` → `POST /auth/logout {refreshToken}`, then clear both keys, then redirect.
- New `refresh()` → `POST /auth/refresh {refreshToken}`, store new pair, return new access token.

### axios interceptor (new, per app)
- Response interceptor: on `401` (and not already a refresh request), call a **single shared** `refresh()` promise (module-level `let refreshing`), await it, retry the original request once with the new access token.
- If refresh rejects: clear tokens, redirect to login.
- `employeehub` uses react-router v7, `hrdashboard` v6 — the redirect differs slightly per app; keep each app's existing navigation approach. Do NOT cross-pin router versions (tech.md #4).

## Data / migration note
DDL-auto is `update` (dev/docker), so `refresh_tokens` is auto-created. When the best-practices track introduces Flyway + `validate`, this table needs a migration script. Note it in that spec; no migration tool exists yet here.

## Docs to update on merge (via auto-doc-update hook or manually)
- `employeeapi/docs/security.md` / `security-implementation.md` — refresh/rotation/revocation model.
- `employeeapi/docs/api.md` + `docs/api-contract.md` — new endpoints + changed login response.
- `docs/data-model-uml.md` — `RefreshToken` entity.
- `docs/next-gen-features.md` — mark B.5 fully DONE + Progress Log row.

## Risks
1. **Login response shape change** breaks both frontends if backend ships alone — ship full-stack in one feature.
2. **401 vs 400 for dead refresh** — resolve before coding (see error handling).
3. **Refresh stampede** on concurrent 401s — mitigated by the shared single-flight refresh promise.
4. **Reuse-detection blast radius** — bulk-revoking all sessions on reuse is correct but logs a user out everywhere; acceptable and expected.
