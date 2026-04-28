import axios from 'axios';

const BASE_URL = process.env.REACT_APP_API_URL || 'http://localhost:8080';

export async function login(email, password) {
  const res = await axios.post(`${BASE_URL}/auth/login`, { email, password });
  localStorage.setItem('token', res.data.data.token);
  return res.data.data.token;
}

export function logout() {
  localStorage.removeItem('token');
}

export function getToken() {
  return localStorage.getItem('token');
}

export function isLoggedIn() {
  return !!getToken();
}

export function getRole() {
  const token = getToken();
  if (!token) return null;
  try {
    const payload = JSON.parse(atob(token.split('.')[1]));
    return payload.role ?? null;
  } catch {
    return null;
  }
}

export function isHrOrAdmin() {
  const role = getRole();
  return role === 'HR_ADMIN' || role === 'SUPER_ADMIN' || role === 'PAYROLL_ADMIN';
}
