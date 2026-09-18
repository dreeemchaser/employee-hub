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
