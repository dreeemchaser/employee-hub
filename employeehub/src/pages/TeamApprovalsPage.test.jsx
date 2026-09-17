import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import TeamApprovalsPage from './TeamApprovalsPage';
import * as api from '../api/EmployeeService';

// Mock the whole service layer — these tests exercise the page's behaviour,
// not the network.
jest.mock('../api/EmployeeService');

const leaveRow = (over = {}) => ({
  id: 'lr-1',
  employee: { firstName: 'Jake', lastName: 'Turner' },
  leaveType: { name: 'Sick Leave' },
  startDate: '2026-10-01',
  endDate: '2026-10-02',
  totalDays: 2,
  reason: 'Flu',
  status: 'PENDING',
  ...over,
});

const tsRow = (over = {}) => ({
  id: 'ts-1',
  employee: { firstName: 'Kim', lastName: 'Lee' },
  weekStartDate: '2026-09-14',
  weekEndDate: '2026-09-20',
  totalHours: 40,
  status: 'SUBMITTED',
  ...over,
});

beforeEach(() => {
  jest.clearAllMocks();
  api.getTeamLeaveRequests.mockResolvedValue({ data: { data: [leaveRow()] } });
  api.getTeamTimesheets.mockResolvedValue({ data: { data: [tsRow()] } });
  api.approveTeamLeave.mockResolvedValue({ data: { data: {} } });
  api.rejectTeamLeave.mockResolvedValue({ data: { data: {} } });
  api.approveTeamTimesheet.mockResolvedValue({ data: { data: {} } });
  api.rejectTeamTimesheet.mockResolvedValue({ data: { data: {} } });
});

test('renders team leave requests from the API', async () => {
  render(<TeamApprovalsPage />);
  expect(await screen.findByText('Jake Turner')).toBeInTheDocument();
  expect(screen.getByText('Sick Leave')).toBeInTheDocument();
  expect(screen.getByRole('button', { name: /Leave \(1\)/ })).toBeInTheDocument();
});

test('approving a pending leave request calls the API', async () => {
  const user = userEvent.setup();
  render(<TeamApprovalsPage />);
  await screen.findByText('Jake Turner');

  await user.click(screen.getByRole('button', { name: /Approve/ }));

  await waitFor(() => expect(api.approveTeamLeave).toHaveBeenCalledWith('lr-1'));
  expect(await screen.findByText(/Leave request approved/)).toBeInTheDocument();
});

test('rejecting requires a reason and sends it to the API', async () => {
  const user = userEvent.setup();
  render(<TeamApprovalsPage />);
  await screen.findByText('Jake Turner');

  // Open the reject modal from the row action.
  await user.click(screen.getByRole('button', { name: /Reject/ }));

  // The modal appears with its heading and a reason textarea.
  const heading = await screen.findByText(/Reject Leave Request/);
  const modal = heading.closest('.card');
  const modalRejectBtn = within(modal).getByRole('button', { name: /^Reject$/ });

  // Reject is disabled until a reason is entered.
  expect(modalRejectBtn).toBeDisabled();

  await user.type(within(modal).getByPlaceholderText(/Provide a reason/), 'Not enough cover');
  expect(modalRejectBtn).toBeEnabled();

  await user.click(modalRejectBtn);
  await waitFor(() => expect(api.rejectTeamLeave).toHaveBeenCalledWith('lr-1', 'Not enough cover'));
});

test('switching to the Timesheets tab shows team timesheets', async () => {
  const user = userEvent.setup();
  render(<TeamApprovalsPage />);
  await screen.findByText('Jake Turner');

  await user.click(screen.getByRole('button', { name: /Timesheets/ }));
  expect(await screen.findByText('Kim Lee')).toBeInTheDocument();
  expect(screen.getByText('40h')).toBeInTheDocument();
});
