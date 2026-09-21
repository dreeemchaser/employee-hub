import { isHrCalendarViewer } from './AuthService';

const tokenForRole = role => [
  'header',
  btoa(JSON.stringify({ role })),
  'signature',
].join('.');

afterEach(() => localStorage.clear());

test('only HR and super admins can view the shared leave calendar', () => {
  localStorage.setItem('token', tokenForRole('HR_ADMIN'));
  expect(isHrCalendarViewer()).toBe(true);

  localStorage.setItem('token', tokenForRole('SUPER_ADMIN'));
  expect(isHrCalendarViewer()).toBe(true);

  localStorage.setItem('token', tokenForRole('PAYROLL_ADMIN'));
  expect(isHrCalendarViewer()).toBe(false);
});
