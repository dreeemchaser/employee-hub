# Refresh Tokens (B.5b) — Requirements

**Epic:** B.5 Authentication operational gaps (`docs/next-gen-features.md` → Gap Analysis B.5)
**Status:** Not started
**Predecessor:** B.5a (account lockout + password reset + config-gated email) — merged to master.

## Problem

The API issues a single access token on login with a 24h expiry (`jwt.expiration`, default `86400000`ms) and no way to renew it without re-entering credentials. A long-lived token is both a security liability (stolen token is valid for a full day, no revocation) and a UX problem (users are logged out abruptly at expiry). There is no logout that actually invalidates anything server-side.

## Goal

Introduce short-lived access tokens plus long-lived, server-tracked refresh tokens with rotation and revocation, so sessions can be renewed silently and terminated on demand — without loosening any existing security rule.

## Requirements

### R1 — Access token becomes short-lived
- Access token expiry drops to a short window (target: 15 minutes) via a new config key, leaving `jwt.expiration` semantics intact or renaming clearly.
- Access token contents are unchanged (`sub` = email, `role` claim). `JwtUtil.generateToken` stays the source of truth.

### R2 — Refresh token issuance
- On successful `POST /auth/login`, the response returns BOTH an access token and a refresh token.
- The refresh token is an opaque, high-entropy random string (NOT a JWT) — it is looked up server-side, so it must be storable and revocable.
- A `RefreshToken` record persists: id, the token (hashed at rest — never store the raw value), the owning employee, issued-at, expires-at (target: 7 days via config), and revoked flag.

### R3 — Refresh endpoint
- `POST /auth/refresh` accepts a refresh token and returns a new access token AND a new refresh token (rotation).
- On rotation, the presented refresh token is revoked and a fresh one issued. A revoked/expired/unknown refresh token yields 401 and issues nothing.
- Reuse detection: presenting an already-revoked token is treated as a compromise signal — revoke all of that employee's refresh tokens (defensive; see design for exact scope).

### R4 — Logout / revocation
- `POST /auth/logout` accepts a refresh token and revokes it server-side (idempotent — revoking an already-revoked/unknown token still returns success).
- Optional: a way to revoke all sessions for the current user (deferred unless trivial).

### R5 — Security posture preserved
- `/auth/**` is already `permitAll()` in `SecurityConfig` — refresh/logout live under `/auth` and need NO new authorize rule. Do not loosen or reorder existing rules.
- Refresh tokens are never logged. Raw refresh token values are never stored (hash at rest) and never returned except in the login/refresh response body.
- Access-token validation via `JwtAuthFilter` is unchanged.

### R6 — Frontend silent refresh (full-stack scope)
- Both React apps (`employeehub` v7 router, `hrdashboard` v6 router) transparently refresh: on a 401 from an expired access token, use the stored refresh token to get a new pair and retry the original request once.
- `AuthService.login` stores both tokens; `logout` calls `POST /auth/logout` then clears local storage.
- A single in-flight refresh is shared across concurrent 401s (no refresh stampede). If refresh fails, clear tokens and route to login.
- Each app keeps its own router version — do not upgrade one to match the other (tech.md constraint #4).

### R7 — Tests
- Service-layer unit tests (Mockito + AssertJ) for `RefreshTokenService`: issue, rotate, revoke, reuse-detection, expiry, unknown-token. No `@SpringBootTest`, per test conventions.
- Full suite (`mvnw verify`) stays green.

### R8 — Config & env
- New config keys documented with sane defaults: access-token TTL (15m), refresh-token TTL (7d). Wired through `application.yml` and overridable via env in docker-compose, following the existing `${JWT_SECRET}` / `${JWT_EXPIRATION}` pattern.

## Out of Scope

- Redis / distributed session store (DB-backed refresh tokens are sufficient at current scale).
- SSO / OAuth2 (separate roadmap item, Feature 21).
- Sliding-session cookies / httpOnly cookie storage (frontends currently use localStorage bearer tokens; keep that model for this slice).

## Acceptance Criteria

1. Login returns `{ accessToken, refreshToken }` (or documented shape); access token expires in ~15m.
2. `POST /auth/refresh` with a valid token returns a new pair and revokes the old refresh token.
3. `POST /auth/refresh` with a revoked/expired/unknown token returns 401 and issues nothing.
4. `POST /auth/logout` revokes the refresh token; a subsequent refresh with it returns 401.
5. Raw refresh tokens are not stored in the DB (hashed) and never appear in logs.
6. Both frontends silently refresh on access-token expiry and route to login when refresh fails.
7. `RefreshTokenService` unit tests cover issue/rotate/revoke/reuse/expiry; `mvnw verify` green.
8. No existing `SecurityConfig` rule changed except adding new endpoints under the existing `/auth/**` permit.
