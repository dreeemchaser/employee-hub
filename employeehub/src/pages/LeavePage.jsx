import { useState, useEffect, useCallback } from 'react';
import TopBar from '../components/TopBar';
import {
  getMyLeaveBalances, getMyLeaveRequests,
  submitLeaveRequest, cancelLeaveRequest,
} from '../api/EmployeeService';

const EMPTY_FORM = { leaveTypeId: '', startDate: '', endDate: '', reason: '' };

const STATUS_COLOR = { APPROVED: 'active', REJECTED: 'inactive', PENDING: 'pending', CANCELLED: 'inactive' };

export default function LeavePage() {
  const [tab, setTab]         = useState('overview');
  const [balances, setBalances] = useState([]);
  const [requests, setRequests] = useState([]);
  const [leaveTypes, setLeaveTypes] = useState([]);
  const [form, setForm]       = useState(EMPTY_FORM);
  const [submitting, setSubmitting] = useState(false);
  const [feedback, setFeedback] = useState(null);

  const load = useCallback(async () => {
    try {
      const [balRes, reqRes] = await Promise.all([getMyLeaveBalances(), getMyLeaveRequests()]);
      const bal = balRes.data?.data ?? [];
      setBalances(bal);
      setLeaveTypes(bal.map(b => ({ id: b.leaveType?.id, name: b.leaveType?.name })));
      setRequests(reqRes.data?.data ?? []);
    } catch {
      // silently fail — user sees empty state
    }
  }, []);

  useEffect(() => { load(); }, [load]);

  const set = e => setForm({ ...form, [e.target.name]: e.target.value });

  const calcDays = () => {
    if (!form.startDate || !form.endDate) return 0;
    const diff = (new Date(form.endDate) - new Date(form.startDate)) / 86400000;
    return diff < 0 ? 0 : diff + 1;
  };

  const handleSubmit = async e => {
    e.preventDefault();
    setSubmitting(true);
    setFeedback(null);
    try {
      await submitLeaveRequest(form);
      setFeedback({ type: 'success', msg: 'Leave request submitted successfully.' });
      setForm(EMPTY_FORM);
      await load();
      setTimeout(() => setTab('history'), 1200);
    } catch (err) {
      setFeedback({ type: 'error', msg: err.response?.data?.message ?? 'Failed to submit request.' });
    } finally {
      setSubmitting(false);
    }
  };

  const handleCancel = async (id) => {
    try {
      await cancelLeaveRequest(id);
      await load();
    } catch {
      // ignore
    }
  };

  const COLORS = ['blue', 'red', 'amber', 'green', 'brand', 'amber'];

  return (
    <>
      <TopBar title='Leave Management' breadcrumb='Employee Hub / Leave' />
      <div className='page'>

        <div className='profile-tabs' style={{ marginBottom: '1.5rem' }}>
          {[['overview', 'Overview'], ['apply', 'Apply for Leave'], ['history', 'History']].map(([key, label]) => (
            <button key={key} className={`profile-tab${tab === key ? ' active' : ''}`} onClick={() => setTab(key)}>
              {label}
            </button>
          ))}
        </div>

        {/* ── Overview ── */}
        {tab === 'overview' && (
          <>
            <div className='stat-grid' style={{ marginBottom: '1.5rem' }}>
              {balances.map((b, i) => {
                const remaining = parseFloat(b.remainingDays ?? 0);
                const total = parseFloat(b.totalDays ?? 1);
                const pct = Math.round((remaining / total) * 100);
                const color = COLORS[i % COLORS.length];
                return (
                  <div className='card' key={b.id} style={{ padding: '1.25rem' }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '0.75rem' }}>
                      <div>
                        <p style={{ fontSize: '0.8rem', color: 'var(--text-muted)', marginBottom: '0.2rem' }}>{b.leaveType?.name}</p>
                        <p style={{ fontSize: '1.6rem', fontWeight: 700, lineHeight: 1 }}>{remaining}</p>
                        <p style={{ fontSize: '0.72rem', color: 'var(--text-muted)' }}>of {total} days remaining</p>
                      </div>
                      <div className={`stat-card__icon stat-card__icon--${color}`}>
                        <i className='bi bi-calendar-check'></i>
                      </div>
                    </div>
                    <div style={{ height: 6, background: 'var(--border)', borderRadius: 'var(--radius-full)', overflow: 'hidden' }}>
                      <div style={{ height: '100%', width: `${pct}%`, background: `var(--${color === 'blue' ? 'brand' : color})`, borderRadius: 'var(--radius-full)', transition: 'width 0.4s ease' }}></div>
                    </div>
                  </div>
                );
              })}
            </div>

            <div className='card'>
              <div className='card__header'>
                <span className='card__title'>Recent Requests</span>
                <button className='btn btn-sm' onClick={() => setTab('apply')}><i className='bi bi-plus-lg'></i> Apply</button>
              </div>
              <div className='table-wrap'>
                <table>
                  <thead><tr><th>Type</th><th>From</th><th>To</th><th>Days</th><th>Status</th></tr></thead>
                  <tbody>
                    {requests.length === 0 ? (
                      <tr><td colSpan={5} style={{ textAlign: 'center', color: 'var(--text-muted)', padding: '2rem' }}>No leave requests yet.</td></tr>
                    ) : requests.slice(0, 3).map(r => (
                      <tr key={r.id}>
                        <td>{r.leaveType?.name}</td>
                        <td>{r.startDate}</td>
                        <td>{r.endDate}</td>
                        <td>{r.totalDays}</td>
                        <td><span className={`badge badge--${STATUS_COLOR[r.status] ?? 'pending'}`}>{r.status}</span></td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          </>
        )}

        {/* ── Apply ── */}
        {tab === 'apply' && (
          <div className='card' style={{ maxWidth: 600 }}>
            <div className='card__header'><span className='card__title'>Apply for Leave</span></div>
            <div className='card__body'>
              {feedback && (
                <p className={`feedback feedback--${feedback.type}`}>
                  <i className={`bi ${feedback.type === 'success' ? 'bi-check-circle' : 'bi-exclamation-circle'}`}></i> {feedback.msg}
                </p>
              )}
              <form onSubmit={handleSubmit}>
                <div className='form-grid'>
                  <div className='form-group' style={{ gridColumn: '1 / -1' }}>
                    <label className='form-label'>Leave Type</label>
                    <select className='form-control' name='leaveTypeId' value={form.leaveTypeId} onChange={set} required>
                      <option value=''>— Select type —</option>
                      {leaveTypes.map(t => <option key={t.id} value={t.id}>{t.name}</option>)}
                    </select>
                  </div>
                  <div className='form-group'>
                    <label className='form-label'>Start Date</label>
                    <input className='form-control' type='date' name='startDate' value={form.startDate} onChange={set} required />
                  </div>
                  <div className='form-group'>
                    <label className='form-label'>End Date</label>
                    <input className='form-control' type='date' name='endDate' value={form.endDate} onChange={set} required />
                  </div>
                  {calcDays() > 0 && (
                    <div className='form-group' style={{ gridColumn: '1 / -1' }}>
                      <p style={{ fontSize: '0.83rem', color: 'var(--brand)', fontWeight: 600 }}>
                        <i className='bi bi-info-circle'></i> {calcDays()} day{calcDays() !== 1 ? 's' : ''}
                      </p>
                    </div>
                  )}
                  <div className='form-group' style={{ gridColumn: '1 / -1' }}>
                    <label className='form-label'>Reason</label>
                    <textarea className='form-control' name='reason' value={form.reason} onChange={set} rows={3} placeholder='Brief reason for leave...' />
                  </div>
                </div>
                <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.5rem', marginTop: '1.25rem' }}>
                  <button type='button' className='btn btn-ghost' onClick={() => setForm(EMPTY_FORM)}>Clear</button>
                  <button type='submit' className='btn' disabled={submitting}><i className='bi bi-send'></i> {submitting ? 'Submitting...' : 'Submit Request'}</button>
                </div>
              </form>
            </div>
          </div>
        )}

        {/* ── History ── */}
        {tab === 'history' && (
          <div className='card'>
            <div className='card__header'>
              <span className='card__title'>Leave History</span>
              <button className='btn btn-sm' onClick={() => setTab('apply')}><i className='bi bi-plus-lg'></i> Apply</button>
            </div>
            <div className='table-wrap'>
              <table>
                <thead><tr><th>Type</th><th>From</th><th>To</th><th>Days</th><th>Reason</th><th>Status</th><th></th></tr></thead>
                <tbody>
                  {requests.length === 0 ? (
                    <tr><td colSpan={7} style={{ textAlign: 'center', color: 'var(--text-muted)', padding: '2rem' }}>No leave requests yet.</td></tr>
                  ) : requests.map(r => (
                    <tr key={r.id}>
                      <td>{r.leaveType?.name}</td>
                      <td>{r.startDate}</td>
                      <td>{r.endDate}</td>
                      <td>{r.totalDays}</td>
                      <td style={{ color: 'var(--text-secondary)' }}>{r.reason}</td>
                      <td><span className={`badge badge--${STATUS_COLOR[r.status] ?? 'pending'}`}>{r.status}</span></td>
                      <td>
                        {r.status === 'PENDING' && (
                          <button className='btn btn-ghost btn-sm' onClick={() => handleCancel(r.id)}>Cancel</button>
                        )}
                      </td>
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
