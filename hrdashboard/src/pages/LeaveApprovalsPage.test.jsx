import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import LeaveApprovalsPage from './LeaveApprovalsPage';
import * as hr from '../api/HrService';

jest.mock('../api/HrService');

const row = (over = {}) => ({
  id: 'lr-1',
  employee: { firstName: 'Kim', lastName: 'Lee' },
  leaveType: { name: 'Annual Leave' },
  startDate: '2026-10-01',
  endDate: '2026-10-05',
  totalDays: 5,
  reason: 'Holiday',
  status: 'PENDING',
  ...over,
});

beforeEach(() => {
  jest.clearAllMocks();
  hr.getAllLeaveRequests.mockResolvedValue({ data: { data: [row()] } });
  hr.approveLeave.mockResolvedValue({ data: { data: {} } });
  hr.rejectLeave.mockResolvedValue({ data: { data: {} } });
});

test('lists leave requests and shows the pending count', async () => {
  render(<LeaveApprovalsPage />);
  expect(await screen.findByText('Kim Lee')).toBeInTheDocument();
  expect(screen.getByText(/1 pending/)).toBeInTheDocument();
});

test('approve calls the HR service', async () => {
  const user = userEvent.setup();
  render(<LeaveApprovalsPage />);
  await screen.findByText('Kim Lee');

  await user.click(screen.getByRole('button', { name: /Approve/ }));
  await waitFor(() => expect(hr.approveLeave).toHaveBeenCalledWith('lr-1'));
});

test('reject requires a reason via the modal', async () => {
  const user = userEvent.setup();
  render(<LeaveApprovalsPage />);
  await screen.findByText('Kim Lee');

  await user.click(screen.getByRole('button', { name: /Reject/ }));
  const heading = await screen.findByText(/Reject Leave Request/);
  const modal = heading.closest('.card');
  const modalRejectBtn = within(modal).getByRole('button', { name: /^Reject$/ });

  expect(modalRejectBtn).toBeDisabled();
  await user.type(within(modal).getByPlaceholderText(/Provide a reason/), 'Insufficient cover');
  expect(modalRejectBtn).toBeEnabled();

  await user.click(modalRejectBtn);
  await waitFor(() => expect(hr.rejectLeave).toHaveBeenCalledWith('lr-1', 'Insufficient cover'));
});

test('status filter narrows the visible rows', async () => {
  const user = userEvent.setup();
  hr.getAllLeaveRequests.mockResolvedValue({
    data: { data: [row(), row({ id: 'lr-2', employee: { firstName: 'Jane', lastName: 'Doe' }, status: 'APPROVED' })] },
  });
  render(<LeaveApprovalsPage />);
  await screen.findByText('Kim Lee');
  expect(screen.getByText('Jane Doe')).toBeInTheDocument();

  // Filter to PENDING only -> the APPROVED row (Jane) disappears.
  await user.click(screen.getByRole('button', { name: 'PENDING' }));
  await waitFor(() => expect(screen.queryByText('Jane Doe')).not.toBeInTheDocument());
  expect(screen.getByText('Kim Lee')).toBeInTheDocument();
});
