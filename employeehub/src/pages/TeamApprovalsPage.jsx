import { useState, useEffect, useCallback } from 'react';
import TopBar from '../components/TopBar';
import {
  getTeamLeaveRequests, approveTeamLeave, rejectTeamLeave,
  getTeamTimesheets, approveTeamTimesheet, rejectTeamTimesheet,
  getTeamAttendance,
} from '../api/EmployeeService';

const LEAVE_STATUS_COLOR = { APPROVED: 'approved', REJECTED: 'rejected', PENDING: 'pending', CANCELLED: 'inactive' };
const TS_STATUS_COLOR    = { APPROVED: 'approved', REJECTED: 'rejected', SUBMITTED: 'pending', DRAFT: 'inactive' };

const empName = e => e ? `${e.firstName ?? ''} ${e.lastName ?? ''}`.trim() : '—';

export default function TeamApprovalsPage() {
  const [tab, setTab]             = useState('leave');
  const [leave, setLeave]         = useState([]);
  const [timesheets, setTimesheets] = useState([]);
  const [attendance, setAttendance] = useState([]);
  const [attendanceLoading, setAttendanceLoading] = useState(false);
  const [loading, setLoading]     = useState(true);
  const [acting, setActing]       = useState(null);
  const [feedback, setFeedback]   = useState(null);

  // Reject modal: { kind: 'leave'|'timesheet', id }
  const [rejectModal, setRejectModal] = useState(null);
  const [rejectReason, setRejectReason] = useState('');
  const [rejecting, setRejecting]     = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [lRes, tRes] = await Promise.allSettled([getTeamLeaveRequests(), getTeamTimesheets()]);
      if (lRes.status === 'fulfilled') setLeave(lRes.value.data?.data ?? []);
      if (tRes.status === 'fulfilled') setTimesheets(tRes.value.data?.data ?? []);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { load(); }, [load]);

  const loadAttendance = useCallback(async () => {
    setAttendanceLoading(true);
    try {
      const res = await getTeamAttendance(0, 50);
      setAttendance(res.data?.data?.content ?? []);
    } catch {
      setAttendance([]);
    } finally {
      setAttendanceLoading(false);
    }
  }, []);

  useEffect(() => {
    if (tab === 'attendance') loadAttendance();
  }, [tab, loadAttendance]);

  const pendingLeave = leave.filter(r => r.status === 'PENDING').length;
  const pendingTs    = timesheets.filter(t => t.status === 'SUBMITTED').length;

  // ── Leave actions ──
  const handleApproveLeave = async id => {
    setActing(id); setFeedback(null);
    try {
      await approveTeamLeave(id);
      setLeave(prev => prev.map(r => r.id === id ? { ...r, status: 'APPROVED' } : r));
      setFeedback({ type: 'success', msg: 'Leave request approved.' });
    } catch (err) {
      setFeedback({ type: 'error', msg: err.response?.data?.message ?? 'Failed to approve.' });
    } finally { setActing(null); }
  };

  // ── Timesheet actions ──
  const handleApproveTs = async id => {
    setActing(id); setFeedback(null);
    try {
      await approveTeamTimesheet(id);
      setTimesheets(prev => prev.map(t => t.id === id ? { ...t, status: 'APPROVED' } : t));
      setFeedback({ type: 'success', msg: 'Timesheet approved.' });
    } catch (err) {
      setFeedback({ type: 'error', msg: err.response?.data?.message ?? 'Failed to approve.' });
    } finally { setActing(null); }
  };

  // ── Reject modal ──
  const openReject  = (kind, id) => { setRejectModal({ kind, id }); setRejectReason(''); };
  const closeReject = () => { setRejectModal(null); setRejectReason(''); };

  const handleReject = async () => {
    if (!rejectReason.trim() || !rejectModal) return;
    setRejecting(true); setFeedback(null);
    try {
      if (rejectModal.kind === 'leave') {
        await rejectTeamLeave(rejectModal.id, rejectReason);
        setLeave(prev => prev.map(r => r.id === rejectModal.id ? { ...r, status: 'REJECTED', rejectionReason: rejectReason } : r));
      } else {
        await rejectTeamTimesheet(rejectModal.id, rejectReason);
        setTimesheets(prev => prev.map(t => t.id === rejectModal.id ? { ...t, status: 'REJECTED', rejectionReason: rejectReason } : t));
      }
      setFeedback({ type: 'success', msg: 'Request rejected.' });
      closeReject();
    } catch (err) {
      setFeedback({ type: 'error', msg: err.response?.data?.message ?? 'Failed to reject.' });
    } finally { setRejecting(false); }
  };

  return (
    <>
      <TopBar title='Team Approvals' breadcrumb='Employee Hub / Team Approvals' />
      <div className='page'>

        {/* Reject modal */}
        {rejectModal && (
          <div style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.45)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000 }}>
            <div className='card' style={{ width: 440, padding: '1.5rem' }}>
              <h3 style={{ fontSize: '1rem', fontWeight: 700, marginBottom: '1rem' }}>
                Reject {rejectModal.kind === 'leave' ? 'Leave Request' : 'Timesheet'}
              </h3>
              <div className='form-group' style={{ marginBottom: '1.25rem' }}>
                <label className='form-label'>Reason for rejection <span style={{ color: 'var(--red)' }}>*</span></label>
                <textarea className='form-control' rows={3} placeholder='Provide a reason...'
                  value={rejectReason} onChange={e => setRejectReason(e.target.value)} autoFocus />
              </div>
              <div style={{ display: 'flex', gap: '0.5rem', justifyContent: 'flex-end' }}>
                <button className='btn btn-ghost' onClick={closeReject}>Cancel</button>
                <button className='btn' style={{ background: 'var(--red)' }} onClick={handleReject} disabled={rejecting || !rejectReason.trim()}>
                  {rejecting ? 'Rejecting...' : 'Reject'}
                </button>
              </div>
            </div>
          </div>
        )}

        <div className='profile-tabs' style={{ marginBottom: '1.5rem' }}>
          {[['leave', `Leave${pendingLeave ? ` (${pendingLeave})` : ''}`], ['timesheets', `Timesheets${pendingTs ? ` (${pendingTs})` : ''}`], ['attendance', 'Attendance']].map(([key, label]) => (
            <button key={key} className={`profile-tab${tab === key ? ' active' : ''}`} onClick={() => { setTab(key); setFeedback(null); }}>
              {label}
            </button>
          ))}
        </div>

        {feedback && (
          <p className={`feedback feedback--${feedback.type}`} style={{ marginBottom: '1rem' }}>
            <i className={`bi ${feedback.type === 'success' ? 'bi-check-circle' : 'bi-exclamation-circle'}`}></i> {feedback.msg}
          </p>
        )}

        {loading && <p style={{ color: 'var(--text-muted)', fontSize: '0.85rem' }}>Loading...</p>}

        {/* ── Leave ── */}
        {!loading && tab === 'leave' && (
          <div className='card'>
            <div className='card__header'><span className='card__title'>Team Leave Requests</span></div>
            <div className='table-wrap'>
              <table>
                <thead><tr><th>Employee</th><th>Type</th><th>From</th><th>To</th><th>Days</th><th>Reason</th><th>Status</th><th></th></tr></thead>
                <tbody>
                  {leave.length === 0 ? (
                    <tr><td colSpan={8} style={{ textAlign: 'center', color: 'var(--text-muted)', padding: '2rem' }}>No team leave requests.</td></tr>
                  ) : leave.map(r => (
                    <tr key={r.id}>
                      <td style={{ fontWeight: 500 }}>{empName(r.employee)}</td>
                      <td style={{ color: 'var(--text-secondary)' }}>{r.leaveType?.name ?? '—'}</td>
                      <td style={{ color: 'var(--text-muted)' }}>{r.startDate}</td>
                      <td style={{ color: 'var(--text-muted)' }}>{r.endDate}</td>
                      <td>{r.totalDays}</td>
                      <td style={{ color: 'var(--text-secondary)', maxWidth: 160, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }} title={r.reason}>{r.reason || '—'}</td>
                      <td><span className={`badge badge--${LEAVE_STATUS_COLOR[r.status] ?? 'pending'}`}>{r.status}</span></td>
                      <td>
                        {r.status === 'PENDING' ? (
                          <div style={{ display: 'flex', gap: '0.4rem' }}>
                            <button className='btn btn-sm' disabled={acting === r.id} onClick={() => handleApproveLeave(r.id)}>
                              <i className='bi bi-check-lg'></i> Approve
                            </button>
                            <button className='btn btn-ghost btn-sm' style={{ color: 'var(--red)' }} disabled={acting === r.id} onClick={() => openReject('leave', r.id)}>
                              <i className='bi bi-x-lg'></i> Reject
                            </button>
                          </div>
                        ) : r.status === 'REJECTED' && r.rejectionReason ? (
                          <span style={{ fontSize: '0.75rem', color: 'var(--red)' }} title={r.rejectionReason}>
                            <i className='bi bi-info-circle'></i> {r.rejectionReason.slice(0, 24)}{r.rejectionReason.length > 24 ? '…' : ''}
                          </span>
                        ) : null}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}

        {/* ── Timesheets ── */}
        {!loading && tab === 'timesheets' && (
          <div className='card'>
            <div className='card__header'><span className='card__title'>Team Timesheets</span></div>
            <div className='table-wrap'>
              <table>
                <thead><tr><th>Employee</th><th>Week Starting</th><th>Week Ending</th><th>Total Hours</th><th>Status</th><th></th></tr></thead>
                <tbody>
                  {timesheets.length === 0 ? (
                    <tr><td colSpan={6} style={{ textAlign: 'center', color: 'var(--text-muted)', padding: '2rem' }}>No team timesheets.</td></tr>
                  ) : timesheets.map(t => (
                    <tr key={t.id}>
                      <td style={{ fontWeight: 500 }}>{empName(t.employee)}</td>
                      <td style={{ color: 'var(--text-muted)' }}>{t.weekStartDate}</td>
                      <td style={{ color: 'var(--text-muted)' }}>{t.weekEndDate}</td>
                      <td>{t.totalHours ?? 0}h</td>
                      <td><span className={`badge badge--${TS_STATUS_COLOR[t.status] ?? 'pending'}`}>{t.status}</span></td>
                      <td>
                        {t.status === 'SUBMITTED' ? (
                          <div style={{ display: 'flex', gap: '0.4rem' }}>
                            <button className='btn btn-sm' disabled={acting === t.id} onClick={() => handleApproveTs(t.id)}>
                              <i className='bi bi-check-lg'></i> Approve
                            </button>
                            <button className='btn btn-ghost btn-sm' style={{ color: 'var(--red)' }} disabled={acting === t.id} onClick={() => openReject('timesheet', t.id)}>
                              <i className='bi bi-x-lg'></i> Reject
                            </button>
                          </div>
                        ) : t.status === 'REJECTED' && t.rejectionReason ? (
                          <span style={{ fontSize: '0.75rem', color: 'var(--red)' }} title={t.rejectionReason}>
                            <i className='bi bi-info-circle'></i> {t.rejectionReason.slice(0, 24)}{t.rejectionReason.length > 24 ? '…' : ''}
                          </span>
                        ) : null}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}

        {/* ── Attendance ── */}
        {tab === 'attendance' && (
          <div className='card'>
            <div className='card__header'><span className='card__title'>Team Attendance</span></div>
            <div className='table-wrap'>
              <table>
                <thead><tr><th>Employee</th><th>Date</th><th>Clock In</th><th>Clock Out</th><th>Status</th><th>Notes</th></tr></thead>
                <tbody>
                  {attendanceLoading ? (
                    <tr><td colSpan={6} style={{ textAlign: 'center', color: 'var(--text-muted)', padding: '2rem' }}>Loading...</td></tr>
                  ) : attendance.length === 0 ? (
                    <tr><td colSpan={6} style={{ textAlign: 'center', color: 'var(--text-muted)', padding: '2rem' }}>No attendance records.</td></tr>
                  ) : attendance.map(a => (
                    <tr key={a.id}>
                      <td style={{ fontWeight: 500 }}>{empName(a.employee)}</td>
                      <td style={{ color: 'var(--text-muted)' }}>{a.workDate}</td>
                      <td>{a.clockInAt ? new Date(a.clockInAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : '—'}</td>
                      <td>{a.clockOutAt ? new Date(a.clockOutAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : '—'}</td>
                      <td><span className={`badge badge--${a.status === 'OPEN' ? 'pending' : 'approved'}`}>{a.status}</span></td>
                      <td style={{ color: 'var(--text-secondary)', maxWidth: 160, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }} title={a.notes}>{a.notes || '—'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}

      </div>
    </>
  );
}
