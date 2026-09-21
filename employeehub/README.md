# EmployeeHub — React Frontend

React frontend for the EmployeeHub HR management system, built with Create React App and served via Nginx in Docker.

## Project Structure

```
employeehub/
├── src/
│   ├── api/
│   │   ├── AuthService.js      # Auth: login, logout, refresh, token/role helpers
│   │   └── EmployeeService.js  # All backend API calls (employees, leave, timesheets, docs, salary, benefits, performance, notifications)
│   ├── components/
│   │   ├── EmployeeCard.jsx
│   │   ├── NewEmployeeModal.jsx
│   │   ├── LoginPage.jsx
│   │   ├── ForgotPasswordPage.jsx
│   │   ├── ResetPasswordPage.jsx
│   │   ├── Sidebar.jsx
│   │   ├── Spinner.jsx
│   │   └── TopBar.jsx
│   ├── pages/
│   │   ├── DashboardPage.jsx
│   │   ├── EmployeesPage.jsx
│   │   ├── EmployeeDetailsPage.jsx
│   │   ├── LeavePage.jsx
│   │   ├── TimesheetsPage.jsx
│   │   ├── SalaryPage.jsx
│   │   ├── BenefitsPage.jsx
│   │   ├── PerformancePage.jsx
│   │   ├── DocumentsPage.jsx
│   │   ├── NotificationsPage.jsx
│   │   ├── ProfilePage.jsx
│   │   └── TeamApprovalsPage.jsx   # Manager team leave/timesheet approvals (role-gated)
│   ├── App.js
│   └── index.js
├── public/
├── Dockerfile                  # Multi-stage build: Node builder + Nginx runtime
├── nginx.conf                  # Nginx SPA routing (the /api/ proxy block is present but unused — the app calls REACT_APP_API_URL directly)
└── package.json
```

## Running with Docker (Recommended)

From the project root:

```bash
docker-compose up --build
```

Frontend will be available at: **http://localhost:3000**

> Any code changes require a rebuild: `docker-compose up --build`

## Running Locally (Development)

```bash
npm install
npm start
```

App will be available at: **http://localhost:3000**

> Hot reload is active in local dev mode. Changes reflect automatically without restart.

## Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `REACT_APP_API_URL` | Backend API base URL | Falls back to `http://localhost:8080` |

In Docker, this is injected at build time via `docker-compose.yml`:
```yaml
args:
  REACT_APP_API_URL: http://localhost:8080
```

## Available Scripts

| Script | Description |
|--------|-------------|
| `npm start` | Start local dev server at http://localhost:3000 |
| `npm run build` | Build production bundle to `build/` |
| `npm test` | Run tests in interactive watch mode |
| `npm run spec:fetch` | Refresh `src/api/openapi.json` from a running backend (`REACT_APP_API_URL`, defaults to `localhost:8080`) |
| `npm run spec:types` | Regenerate `src/api/api-types.d.ts` from the committed `openapi.json` |
| `npm run spec:update` | Both of the above, in order |

## API types (drift detection, not a generated client)

`src/api/api-types.d.ts` is generated from the backend's live OpenAPI spec via
[`openapi-typescript`](https://openapi-typescript.pages.dev/) and committed to
git. `src/api/EmployeeService.js` and `AuthService.js` still make hand-written
`axios` calls through the same shared instance (this is required — the module
registers a global response interceptor for silent token refresh; a generated
client with its own HTTP layer would bypass it). What the generated file adds
is JSDoc type annotations on top of those calls:

```js
/** @returns {Promise<{data: {success?: boolean, message?: string, data?: MeResponse}}>} */
export async function getMe() { ... }
```

This is types-only — no runtime code is generated, no new dependency ships to
the browser, and `.d.ts` files have zero effect on the CRA build (`react-scripts
build` output is unaffected). The payoff is at edit time: VS Code (or `npx tsc
--allowJs --checkJs --noEmit src/api/*.js` in CI/pre-commit if you want it
enforced) will flag a call site that references a field the backend doesn't
actually have, or a field that got renamed — the exact kind of drift this
pattern exists to catch.

**When the backend's DTOs change:** run `npm run spec:update` (with the API
running) and commit the regenerated `openapi.json` + `api-types.d.ts` alongside
your frontend change.

**Known limitation — paginated non-`Employee` endpoints:** Springdoc collapses
every `Page<T>` response onto one generic `PageObject` schema, keyed by
whichever `T` it renders first (currently `EmployeeResponse`). So
`api-types.d.ts` will claim every paginated endpoint's `content` is
`EmployeeResponse[]`, which is wrong for endpoints paginating anything else.
Don't trust the generated type for a paginated field unless you've confirmed
which DTO it actually renders — hand-write the JSDoc `@typedef` from the real
backend DTO instead (see `hrdashboard/src/api/HrService.js`'s `AuditLogResponse`
typedef for the pattern).
