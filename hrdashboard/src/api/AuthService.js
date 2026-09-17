import axios from 'axios';

const BASE_URL = process.env.REACT_APP_API_URL || 'http://localhost:8080';

export async function login(email, password) {
  try {
    const res = await axios.post(`${BASE_URL}/auth/login`, { email, password });
    localStorage.setItem('token', res.data.data.token);
    return res.data.data.token;
  } catch (err) {
    throw toLoginError(err);
  }
}

// Build a user-facing Error from an auth failure. A locked account (HTTP 423)
// surfaces the backend's explanatory message and carries `retryAfterSeconds` so
// the UI can show a countdown; anything else stays generic so we don't reveal
// whether the email exists.
function toLoginError(err) {
  const status = err.response?.status;
  if (status === 423) {
    const error = new Error(
      err.response?.data?.message
      || 'Your account is temporarily locked due to repeated failed sign-in attempts. Please try again later.'
    );
    error.retryAfterSeconds = lockRetrySeconds(err);
    return error;
  }
  return new Error('Invalid email or password.');
}

// Prefer the retryAfterSeconds field in the body; fall back to the Retry-After
// header. Returns 0 when neither is present or parseable.
function lockRetrySeconds(err) {
  const fromBody = err.response?.data?.data?.retryAfterSeconds;
  if (Number.isFinite(fromBody)) return fromBody;
  const fromHeader = Number(err.response?.headers?.['retry-after']);
  return Number.isFinite(fromHeader) ? fromHeader : 0;
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
