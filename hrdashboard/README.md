# hrdashboard

React-based HR admin portal for the EmployeeHub system. Used by HR and admin staff (`HR_ADMIN`, `SUPER_ADMIN`, `PAYROLL_ADMIN`) for approvals and oversight. Built with Create React App and served via Nginx in Docker. Runs on port `3001`.

## Features

- **Dashboard** — at-a-glance HR stats
- **Employees** — employee directory
- **Leave Approvals** — approve/reject team & org leave requests (with reject-reason modal)
- **Timesheet Approvals** — approve/reject submitted timesheets
- **Salary** — salary records and increase-request approvals
- **Documents** — verify/reject uploaded employee documents
- **Audit Logs** — paginated, filterable audit trail of write operations

## Project Structure

```
hrdashboard/
├── src/
│   ├── api/
│   │   ├── AuthService.js   # Auth: login, logout, refresh, token/role helpers
│   │   └── HrService.js     # HR API calls: approvals, audit logs, dashboard stats
│   ├── components/
│   │   ├── LoginPage.jsx
│   │   ├── ForgotPasswordPage.jsx
│   │   ├── ResetPasswordPage.jsx
│   │   ├── Sidebar.jsx      # Data-driven nav array
│   │   ├── Spinner.jsx
│   │   └── TopBar.jsx
│   ├── pages/
│   │   ├── DashboardPage.jsx
│   │   ├── EmployeesPage.jsx
│   │   ├── LeaveApprovalsPage.jsx
│   │   ├── TimesheetApprovalsPage.jsx
│   │   ├── SalaryPage.jsx
│   │   ├── DocumentsPage.jsx
│   │   └── AuditLogsPage.jsx
│   ├── App.js               # Auth gate + routes (react-router-dom v6)
│   └── index.js
├── public/
├── Dockerfile               # Multi-stage build: Node builder + Nginx runtime
├── nginx.conf
└── package.json
```

## Running Locally

```bash
npm install
npm start
```

Runs on [http://localhost:3001](http://localhost:3001)

## Running with Docker

See the root `docker-compose.yml`. The dashboard is built and served via Nginx on port `3001`.

## Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `REACT_APP_API_URL` | Backend API base URL | `http://localhost:8080` |

## Usage

1. Open the dashboard at `http://localhost:3001`
2. Sign in on the login page with an HR/admin account (e.g. `admin@employeehub.com` / `Admin@1234`). The app obtains and stores a JWT via `POST /auth/login` and refreshes it silently — you do not paste tokens manually.
3. Use the sidebar to navigate approvals, employees, salary, documents, and audit logs.

## Available Scripts

| Script | Description |
|--------|-------------|
| `npm start` | Start local dev server at http://localhost:3001 |
| `npm run build` | Build production bundle to `build/` |
| `npm test` | Run tests (includes `LeaveApprovalsPage` RTL tests) |
