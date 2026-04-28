import axios from 'axios';
import { getToken } from './AuthService';

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

// ── Departments & Teams ─────────────────────────────────────────────────────

export async function getDepartments() {
    return axios.get(`${BASE_URL}/departments`, authHeaders());
}

export async function getTeams(departmentId) {
    const query = departmentId ? `?departmentId=${departmentId}` : '';
    return axios.get(`${BASE_URL}/teams${query}`, authHeaders());
}

// ── Employees ────────────────────────────────────────────────────────────────

export async function getEmployees(page = 0, size = 10) {
    const r = await axios.get(`${BASE_URL}/employees?page=${page}&size=${size}`, authHeaders());
    const pageData = r.data.data;
    return {
        data: {
            ...pageData,
            content: (pageData.content ?? []).map(normaliseEmployee),
        }
    };
}

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

export async function uploadDocument(type, file) {
    const fd = new FormData();
    fd.append('file', file);
    return axios.post(`${BASE_URL}/documents/upload?type=${type}`, fd, authHeaders());
}

// ── Notifications ────────────────────────────────────────────────────────────

export async function getMyNotifications() {
    return axios.get(`${BASE_URL}/notifications/my`, authHeaders());
}

export async function markNotificationRead(id) {
    return axios.patch(`${BASE_URL}/notifications/${id}/read`, {}, authHeaders());
}
