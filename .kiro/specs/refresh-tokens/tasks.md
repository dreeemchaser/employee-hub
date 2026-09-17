# Refresh Tokens (B.5b) — Tasks

Work top-to-bottom. Branch: `feature/auth-refresh-tokens` off `master`. Verify with `mvnw verify` before marking backend done. Do not commit/push unless asked.

## Pre-flight
- [x] 1. DECIDED with user: **Option A** — `rotate()` returns `Optional<TokenPair>`, controller returns **401** on empty (correct semantics, no new exception type, mirrors `AuthController.login`'s `lockedResponse` precedent). Scope confirmed **full-stack** (backend + both frontends).

## Backend
- [ ] 2. Add config keys: `jwt.access-expiration` (900000) and `jwt.refresh-expiration` (604800000) in `application.yml`; repoint `JwtUtil` expiry to access TTL. Add env passthrough in `docker-compose.yml` / `docker-compose.prod.yml` mirroring the `JWT_EXPIRATION` pattern.
- [ ] 3. Create `RefreshToken` entity (`domain/RefreshToken.java`) per design (UUID id, unique `tokenHash`, lazy `Employee`, `expiresAt`, `revoked`, `createdAt`).
- [ ] 4. Create `RefreshTokenRepository` with `findByTokenHash`, `findAllByEmployeeIdAndRevokedFalse`, and bulk-revoke `@Modifying` query.
- [ ] 5. Create `RefreshTokenService` (`issue`, `rotate`, `revoke`, SHA-256 hash helper, `SecureRandom` token gen, reuse detection, `@Transactional` on writes).
- [ ] 6. Add request DTO `RefreshRequest { @NotBlank String refreshToken }` (`@Data`, Bean Validation) in `dto/`.
- [ ] 7. Wire `AuthController`: inject `RefreshTokenService`; login returns `{accessToken, refreshToken}`; add `POST /auth/refresh` and `POST /auth/logout` with `@Operation` summaries. No try/catch.
- [ ] 8. Confirm `SecurityConfig` needs no change (`/auth/**` already permitAll). Do not touch other rules.

## Backend tests
- [ ] 9. `RefreshTokenServiceTest` (Mockito + AssertJ, no `@SpringBootTest`): issue persists a hash not raw; rotate revokes-old + issues-new; expired → fails; revoked (reuse) → bulk-revoke + fails; unknown → fails; logout/revoke is idempotent.
- [ ] 10. Run `mvnw verify` — full suite green (was 78 tests; expect additions). Record the count.

## Frontend — employeehub (react-router v7)
- [ ] 11. `AuthService.js`: store both tokens on login; `refresh()`; `logout()` calls `POST /auth/logout` then clears both keys.
- [ ] 12. axios response interceptor: single-flight refresh on 401 + retry once; on refresh failure clear tokens and route to login (v7 navigation).

## Frontend — hrdashboard (react-router v6)
- [ ] 13. Mirror AuthService changes (uses `auth()` alias style, not `authHeaders()`).
- [ ] 14. Mirror interceptor; use v6 navigation. Do NOT change router version.

## Docs & wrap-up
- [ ] 15. Update `employeeapi/docs/security*.md`, `api.md`, `docs/api-contract.md`, `docs/data-model-uml.md` for the new endpoints, login response, and entity.
- [ ] 16. Mark B.5 fully DONE in `docs/next-gen-features.md` (section B item 5 + Progress Log row).
- [ ] 17. Open PR `feat(auth): short-lived access + rotating refresh tokens (B.5b)` — summary, what was tested, the login-response contract change called out for reviewers.

## Definition of Done
All acceptance criteria in `requirements.md` met; `mvnw verify` green; both frontends silently refresh and fall back to login; no existing security rule loosened; docs + progress log updated.
