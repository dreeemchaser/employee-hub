import axios from 'axios';
import { getToken } from './AuthService';

/**
 * @typedef {import('./api-types').components['schemas']['MeResponse']} MeResponse
 * @typedef {import('./api-types').components['schemas']['EmployeeResponse']} EmployeeResponse
 * @typedef {import('./api-types').components['schemas']['ApiResponsePageEmployeeResponse']['data']} EmployeePage
 */

const BASE_URL = process.env.REACT_APP_API_URL || 'http://localhost:8080';

const authHeaders = () => ({ headers: { Authorization: `Bearer ${getToken()}` } });

// Normalise backend Employee shape to the flat shape the UI components expect
function normaliseEmployee(emp) {
    if (!emp) return emp;
    return {
        ...emp,
        name: `${emp.firstName ?? ''} ${emp.lastName ?? ''}`.trim(),
        title: emp.jobTitle,
        status: emp.employmentStatus?.toLowerCase(),
        department: emp.department?.name ?? emp.department,
        team: emp.team?.name ?? emp.team,
        photoURL: emp.profilePhoto,
    };
}

// ── Profile ──────────────────────────────────────────────────────────────────

/** @returns {Promise<{data: {success?: boolean, message?: string, data?: MeResponse}}>} */
export async function getMe() {
    return axios.get(`${BASE_URL}/auth/me`, authHeaders());
}

export async function updateMe(dto) {
    return axios.patch(`${BASE_URL}/auth/me`, dto, authHeaders());
}

export async function changePassword(dto) {
    return axios.post(`${BASE_URL}/auth/change-password`, dto, authHeaders());
}

// ── Departments & Teams ─────────────────────────────────────────────────────

export async function getDepartments() {
    return axios.get(`${BASE_URL}/departments`, authHeaders());
}

export async function getTeams(departmentId) {
    const query = departmentId ? `?departmentId=${departmentId}` : '';
    return axios.get(`${BASE_URL}/teams${query}`, authHeaders());
}

// ── Employees ────────────────────────────────────────────────────────────────

/**
 * @param {number} [page]
 * @param {number} [size]
 * @param {{departmentId?: number, teamId?: number, status?: string}} [filters]
 * @returns {Promise<{data: EmployeePage}>} `content` items are pre-flattened by normaliseEmployee.
 */
export async function getEmployees(page = 0, size = 10, filters = {}) {
    const params = new URLSearchParams({ page, size });
    if (filters.departmentId) params.append('departmentId', filters.departmentId);
    if (filters.teamId) params.append('teamId', filters.teamId);
    if (filters.status) params.append('status', filters.status);
    
    const r = await axios.get(`${BASE_URL}/employees?${params}`, authHeaders());
    const pageData = r.data.data;
    return {
        data: {
            ...pageData,
            content: (pageData.content ?? []).map(normaliseEmployee),
        }
    };
}

/**
 * @param {string} id
 * @returns {Promise<{data: EmployeeResponse}>}
 */
export async function getEmployee(id) {
    const r = await axios.get(`${BASE_URL}/employees/${id}`, authHeaders());
    return { data: normaliseEmployee(r.data.data) };
}

export async function saveEmployee(employee) {
    return axios.post(`${BASE_URL}/employees`, employee, authHeaders());
}

export async function updateEmployee(id, employee) {
    return axios.put(`${BASE_URL}/employees/${id}`, employee, authHeaders());
}

export async function deleteEmployee(id) {
    return axios.delete(`${BASE_URL}/employees/${id}`, authHeaders());
}

export async function updateEmployeePhoto(id, file) {
    const fd = new FormData();
    fd.append('file', file);
    return axios.post(`${BASE_URL}/employees/${id}/photo`, fd, authHeaders());
}

export async function updateEmployeeStatus(id, status) {
    return axios.patch(`${BASE_URL}/employees/${id}/status`, { status }, authHeaders());
}

// Terminates the employee, cancels their pending leave requests, and
// deactivates their active benefit enrollments (see EmployeeService.offboard).
export async function offboardEmployee(id, reason, lastWorkingDay) {
    return axios.post(`${BASE_URL}/employees/${id}/offboard`, { reason, lastWorkingDay }, authHeaders());
}

export function getPhotoUrl(filename) {
    return `${BASE_URL}/employees/photo/${filename}`;
}

// ── Leave ────────────────────────────────────────────────────────────────────

export async function getMyLeaveRequests() {
    return axios.get(`${BASE_URL}/leave/requests/my`, authHeaders());
}

export async function getMyLeaveBalances() {
    return axios.get(`${BASE_URL}/leave/balances/my`, authHeaders());
}

export async function submitLeaveRequest(dto) {
    return axios.post(`${BASE_URL}/leave/requests`, dto, authHeaders());
}

export async function cancelLeaveRequest(id) {
    return axios.delete(`${BASE_URL}/leave/requests/${id}`, authHeaders());
}

// ── Timesheets ───────────────────────────────────────────────────────────────

export async function getMyTimesheets() {
    return axios.get(`${BASE_URL}/timesheets/my`, authHeaders());
}

export async function createTimesheet(dto) {
    return axios.post(`${BASE_URL}/timesheets`, dto, authHeaders());
}

export async function addTimesheetEntry(timesheetId, entry) {
    return axios.post(`${BASE_URL}/timesheets/${timesheetId}/entries`, entry, authHeaders());
}

export async function submitTimesheet(id) {
    return axios.patch(`${BASE_URL}/timesheets/${id}/submit`, {}, authHeaders());
}

// ── Salary ───────────────────────────────────────────────────────────────────

export async function getMyPaySlips() {
    return axios.get(`${BASE_URL}/salary/payslips/my`, authHeaders());
}

// ── Benefits ─────────────────────────────────────────────────────────────────

export async function getBenefitTypes() {
    return axios.get(`${BASE_URL}/benefits`, authHeaders());
}

export async function getMyBenefits() {
    return axios.get(`${BASE_URL}/benefits/my`, authHeaders());
}

export async function applyForBenefit(benefitTypeId) {
    return axios.post(`${BASE_URL}/benefits/apply`, { benefitTypeId }, authHeaders());
}

// ── Performance ──────────────────────────────────────────────────────────────

export async function getMyGoals() {
    return axios.get(`${BASE_URL}/performance/goals/my`, authHeaders());
}

export async function getMyReviews() {
    return axios.get(`${BASE_URL}/performance/reviews/my`, authHeaders());
}

// ── Documents ────────────────────────────────────────────────────────────────

export async function getMyDocuments() {
    return axios.get(`${BASE_URL}/documents/my`, authHeaders());
}

export async function uploadDocument(type, file, expiryDate) {
    const fd = new FormData();
    fd.append('file', file);
    const params = new URLSearchParams({ type });
    if (expiryDate) params.append('expiryDate', expiryDate);
    return axios.post(`${BASE_URL}/documents/upload?${params}`, fd, authHeaders());
}

// ── Notifications ────────────────────────────────────────────────────────────

export async function getMyNotifications() {
    return axios.get(`${BASE_URL}/notifications/my`, authHeaders());
}

export async function markNotificationRead(id) {
    return axios.patch(`${BASE_URL}/notifications/${id}/read`, {}, authHeaders());
}

export async function markAllNotificationsRead() {
    return axios.patch(`${BASE_URL}/notifications/read-all`, {}, authHeaders());
}

// ── Leave Calendar ────────────────────────────────────────────────────────────

export async function getLeaveCalendar(year, month, filters = {}) {
    const params = new URLSearchParams({ year, month });
    if (filters.leaveTypeId) params.append('leaveTypeId', filters.leaveTypeId);
    if (filters.employeeId) params.append('employeeId', filters.employeeId);
    if (filters.departmentId) params.append('departmentId', filters.departmentId);
    if (filters.teamId) params.append('teamId', filters.teamId);
    return axios.get(`${BASE_URL}/leave/calendar?${params}`, authHeaders());
}

export async function getLeaveForecast() {
    return axios.get(`${BASE_URL}/leave/balances/forecast`, authHeaders());
}

export async function getLeaveConflicts(startDate, endDate) {
    const params = new URLSearchParams({ startDate, endDate });
    return axios.get(`${BASE_URL}/leave/conflicts?${params}`, authHeaders());
}

export async function getLeaveTypes() {
    return axios.get(`${BASE_URL}/leave/types`, authHeaders());
}

// ── Salary Increase Requests ──────────────────────────────────────────────────

export async function getMyIncreaseRequests() {
    return axios.get(`${BASE_URL}/salary/increase-requests/my`, authHeaders());
}

export async function submitIncreaseRequest(dto) {
    return axios.post(`${BASE_URL}/salary/increase-requests`, dto, authHeaders());
}

// ── Timesheet entry delete ────────────────────────────────────────────────────

export async function deleteTimesheetEntry(timesheetId, entryId) {
    return axios.delete(`${BASE_URL}/timesheets/${timesheetId}/entries/${entryId}`, authHeaders());
}

// ── Attendance ───────────────────────────────────────────────────────────────

export async function clockIn() {
    return axios.post(`${BASE_URL}/attendance/clock-in`, {}, authHeaders());
}

export async function clockOut(notes) {
    return axios.patch(`${BASE_URL}/attendance/clock-out`, { notes }, authHeaders());
}

export async function getMyAttendance(page = 0, size = 10) {
    return axios.get(`${BASE_URL}/attendance/my?page=${page}&size=${size}`, authHeaders());
}

// Manager/HR: team attendance (backend scopes MANAGER to direct reports, HR/Admin see all).
export async function getTeamAttendance(page = 0, size = 20, filters = {}) {
    const params = new URLSearchParams({ page, size });
    if (filters.employeeId) params.append('employeeId', filters.employeeId);
    if (filters.from) params.append('from', filters.from);
    if (filters.to) params.append('to', filters.to);
    return axios.get(`${BASE_URL}/attendance?${params}`, authHeaders());
}

// ── Manager: Team Approvals ───────────────────────────────────────────────────
// These call the same backend endpoints the HR dashboard uses. The backend
// scopes results to the manager's direct reports and enforces that a MANAGER
// may only approve/reject their own team's requests.

export async function getTeamLeaveRequests() {
    return axios.get(`${BASE_URL}/leave/requests`, authHeaders());
}

export async function approveTeamLeave(id) {
    return axios.patch(`${BASE_URL}/leave/requests/${id}/approve`, {}, authHeaders());
}

export async function rejectTeamLeave(id, reason) {
    return axios.patch(`${BASE_URL}/leave/requests/${id}/reject`, { reason }, authHeaders());
}

export async function getTeamTimesheets() {
    return axios.get(`${BASE_URL}/timesheets`, authHeaders());
}

export async function approveTeamTimesheet(id) {
    return axios.patch(`${BASE_URL}/timesheets/${id}/approve`, {}, authHeaders());
}

export async function rejectTeamTimesheet(id, reason) {
    return axios.patch(`${BASE_URL}/timesheets/${id}/reject`, { reason }, authHeaders());
}
