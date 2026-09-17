import axios from 'axios';
import { getRole, isManager, isHrOrAdmin, isLoggedIn, logout, login } from './AuthService';

jest.mock('axios');

// Build a fake JWT: only the payload segment matters to these helpers.
function tokenWithRole(role) {
  const payload = btoa(JSON.stringify({ sub: 'user@test.com', role }));
  return `header.${payload}.signature`;
}

beforeEach(() => {
  localStorage.clear();
});

test('getRole returns null when no token is stored', () => {
  expect(getRole()).toBeNull();
});

test('getRole reads the role claim from the token', () => {
  localStorage.setItem('token', tokenWithRole('MANAGER'));
  expect(getRole()).toBe('MANAGER');
});

test('isManager is true only for the MANAGER role', () => {
  localStorage.setItem('token', tokenWithRole('MANAGER'));
  expect(isManager()).toBe(true);

  localStorage.setItem('token', tokenWithRole('EMPLOYEE'));
  expect(isManager()).toBe(false);
});

test('isHrOrAdmin is true for HR_ADMIN, SUPER_ADMIN and PAYROLL_ADMIN only', () => {
  for (const r of ['HR_ADMIN', 'SUPER_ADMIN', 'PAYROLL_ADMIN']) {
    localStorage.setItem('token', tokenWithRole(r));
    expect(isHrOrAdmin()).toBe(true);
  }
  for (const r of ['EMPLOYEE', 'MANAGER']) {
    localStorage.setItem('token', tokenWithRole(r));
    expect(isHrOrAdmin()).toBe(false);
  }
});

test('getRole returns null for a malformed token instead of throwing', () => {
  localStorage.setItem('token', 'not-a-jwt');
  expect(getRole()).toBeNull();
});

test('isLoggedIn reflects token presence, and logout clears it', async () => {
  expect(isLoggedIn()).toBe(false);
  localStorage.setItem('token', tokenWithRole('EMPLOYEE'));
  expect(isLoggedIn()).toBe(true);
  await logout(); // no refresh token stored → clears locally without a network call
  expect(isLoggedIn()).toBe(false);
});

test('logout revokes the refresh token server-side and clears both tokens', async () => {
  localStorage.setItem('token', tokenWithRole('EMPLOYEE'));
  localStorage.setItem('refreshToken', 'refresh-abc');
  axios.post.mockResolvedValueOnce({ data: { message: 'Logged out successfully' } });

  await logout();

  expect(axios.post).toHaveBeenCalledWith(
    expect.stringContaining('/auth/logout'),
    { refreshToken: 'refresh-abc' }
  );
  expect(localStorage.getItem('token')).toBeNull();
  expect(localStorage.getItem('refreshToken')).toBeNull();
});

test('login stores the access + refresh token pair on success', async () => {
  axios.post.mockResolvedValueOnce({
    data: { data: { accessToken: 'abc.def.ghi', refreshToken: 'refresh-xyz' } },
  });
  const token = await login('user@test.com', 'pw');
  expect(token).toBe('abc.def.ghi');
  expect(localStorage.getItem('token')).toBe('abc.def.ghi');
  expect(localStorage.getItem('refreshToken')).toBe('refresh-xyz');
});

test('login surfaces the lock message and retryAfterSeconds on a 423 response', async () => {
  axios.post.mockRejectedValueOnce({
    response: {
      status: 423,
      data: { message: 'Account is temporarily locked. Try again later.', data: { retryAfterSeconds: 900 } },
    },
  });
  await expect(login('user@test.com', 'pw')).rejects.toMatchObject({
    message: expect.stringMatching(/temporarily locked/i),
    retryAfterSeconds: 900,
  });
});

test('login falls back to the Retry-After header for the countdown', async () => {
  axios.post.mockRejectedValueOnce({
    response: { status: 423, data: { message: 'Locked' }, headers: { 'retry-after': '120' } },
  });
  await expect(login('user@test.com', 'pw')).rejects.toMatchObject({ retryAfterSeconds: 120 });
});

test('login shows a generic message on a 401 response', async () => {
  axios.post.mockRejectedValueOnce({ response: { status: 401, data: { message: 'Authentication failed' } } });
  await expect(login('user@test.com', 'pw')).rejects.toThrow(/incorrect email or password/i);
});
