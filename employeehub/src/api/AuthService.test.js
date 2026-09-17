import { getRole, isManager, isHrOrAdmin, isLoggedIn, logout } from './AuthService';

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

test('isLoggedIn reflects token presence, and logout clears it', () => {
  expect(isLoggedIn()).toBe(false);
  localStorage.setItem('token', tokenWithRole('EMPLOYEE'));
  expect(isLoggedIn()).toBe(true);
  logout();
  expect(isLoggedIn()).toBe(false);
});
