import axios from 'axios';
import { getToken } from './AuthService';

/**
 * @typedef {import('./api-types').components['schemas']['EmployeeResponse']} EmployeeResponse
 *
 * AuditLogResponse is hand-typed, not sourced from api-types.d.ts: Springdoc's
 * OpenAPI generation collapses every Page<T> onto a single generic PageObject
 * schema keyed by whichever generic instantiation it renders first (currently
 * EmployeeResponse), so the generated type for GET /audit-logs's page content
 * is wrong (claims EmployeeResponse[], the real shape is below, matching
 * employeeapi/src/main/java/employeehub/dto/AuditLogResponse.java). Re-check
 * this by hand if that DTO changes — codegen can't catch drift here.
 * @typedef {{
 *   id?: string,
 *   performedBy?: { id?: string, firstName?: string, lastName?: string, employeeNumber?: string },
 *   action?: string,
 *   entityType?: string,
 *   entityId?: string,
 *   oldValue?: string,
 *   newValue?: string,
 *   timestamp?: string,
 *   ipAddress?: string,
 * }} AuditLogResponse
 */

const BASE_URL = process.env.REACT_APP_API_URL || 'http://localhost:8080';

const auth = () => ({ headers: { Authorization: `Bearer ${getToken()}` } });

// ── Employees ────────────────────────────────────────────────────────────────

/**
 * @param {number} [page]
 * @param {number} [size]
 * @returns {Promise<{data: {success?: boolean, message?: string, data?: {content?: EmployeeResponse[], totalElements?: number, totalPages?: number}}}>}
 */
export async function getEmployees(page = 0, size = 10) {
  return axios.get(`${BASE_URL}/employees?page=${page}&size=${size}`, auth());
}

// ── Leave ────────────────────────────────────────────────────────────────────

export async function getAllLeaveRequests() {
  return axios.get(`${BASE_URL}/leave/requests`, auth());
}

export async function approveLeave(id) {
  return axios.patch(`${BASE_URL}/leave/requests/${id}/approve`, {}, auth());
}

export async function rejectLeave(id, reason) {
  return axios.patch(`${BASE_URL}/leave/requests/${id}/reject`, { reason }, auth());
}

export async function getLeaveCalendar(year, month, filters = {}) {
  const params = new URLSearchParams({ year, month });
  Object.entries(filters).forEach(([key, value]) => {
    if (value) params.append(key, value);
  });
  return axios.get(`${BASE_URL}/leave/calendar?${params}`, auth());
}

export async function getDepartments() {
  return axios.get(`${BASE_URL}/departments`, auth());
}

export async function getTeams(departmentId) {
  const query = departmentId ? `?departmentId=${departmentId}` : '';
  return axios.get(`${BASE_URL}/teams${query}`, auth());
}

export async function getLeaveTypes() {
  return axios.get(`${BASE_URL}/leave/types`, auth());
}

// ── Timesheets ───────────────────────────────────────────────────────────────

export async function getAllTimesheets() {
  return axios.get(`${BASE_URL}/timesheets`, auth());
}

export async function approveTimesheet(id) {
  return axios.patch(`${BASE_URL}/timesheets/${id}/approve`, {}, auth());
}

export async function rejectTimesheet(id, reason) {
  return axios.patch(`${BASE_URL}/timesheets/${id}/reject`, { reason }, auth());
}

// ── Documents ────────────────────────────────────────────────────────────────

export async function getAllDocuments() {
  return axios.get(`${BASE_URL}/documents`, auth());
}

export async function verifyDocument(id) {
  return axios.patch(`${BASE_URL}/documents/${id}/verify`, {}, auth());
}

// ── Audit Logs ───────────────────────────────────────────────────────────────

/**
 * @param {number} [page]
 * @param {number} [size]
 * @returns {Promise<{data: {success?: boolean, message?: string, data?: {content?: AuditLogResponse[], totalElements?: number, totalPages?: number}}}>}
 */
export async function getAuditLogs(page = 0, size = 20) {
  return axios.get(`${BASE_URL}/audit-logs?page=${page}&size=${size}`, auth());
}

// ── Salary ────────────────────────────────────────────────────────────────────

export async function getAllIncreaseRequests() {
  return axios.get(`${BASE_URL}/salary/increase-requests`, auth());
}

export async function approveIncreaseRequest(id) {
  return axios.patch(`${BASE_URL}/salary/increase-requests/${id}/approve`, {}, auth());
}

export async function rejectIncreaseRequest(id, reason) {
  return axios.patch(`${BASE_URL}/salary/increase-requests/${id}/reject`, { reason }, auth());
}

export async function getAllSalaryRecords(employeeId) {
  return axios.get(`${BASE_URL}/salary/records/${employeeId}`, auth());
}

// ── Dashboard Stats ───────────────────────────────────────────────────────────

export async function getDashboardStats() {
  const [empRes, leaveRes, tsRes, docRes] = await Promise.allSettled([
    getEmployees(0, 1),
    getAllLeaveRequests(),
    getAllTimesheets(),
    getAllDocuments(),
  ]);

  const empData    = empRes.value?.data?.data ?? empRes.value?.data;
  const employees  = empRes.status === 'fulfilled' ? (empData?.totalElements ?? '—') : '—';
  const leaveData  = leaveRes.status === 'fulfilled' ? (leaveRes.value.data?.data ?? []) : [];
  const tsData     = tsRes.status    === 'fulfilled' ? (tsRes.value.data?.data ?? []) : [];
  const docData    = docRes.status   === 'fulfilled' ? (docRes.value.data?.data ?? []) : [];

  return {
    employees,
    pendingLeave:       leaveData.filter(r => r.status === 'PENDING').length,
    pendingTimesheets:  tsData.filter(t => t.status === 'SUBMITTED').length,
    pendingDocuments:   docData.filter(d => d.status === 'PENDING').length,
  };
}
