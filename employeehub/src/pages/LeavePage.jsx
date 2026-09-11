import { useState, useEffect, useCallback } from 'react';
import TopBar from '../components/TopBar';
import {
  getMyLeaveBalances, getMyLeaveRequests,
  submitLeaveRequest, cancelLeaveRequest, getLeaveCalendar,
} from '../api/EmployeeService';

const EMPTY_FORM = { leaveTypeId: '', startDate: '', endDate: '', reason: '' };
const STATUS_COLOR = { APPROVED: 'approved', REJECTED: 'rejected', PENDING: 'pending', CANCELLED: 'inactive' };
const MONTHS = ['January','February','March','April','May','June','July','August','September','October','November','December'];
const COLORS = ['var(--brand)', 'var(--red)', 'var(--amber)', 'var(--green)', 'hsl(270,60%,55%)', 'hsl(190,60%,40%)'];

export default function LeavePage() {
  const [tab, setTab]           = useState('overview');
  const [balances, setBalances] = useState([]);
  const [requests, setRequests] = useState([]);
  const [leaveTypes, setLeaveTypes] = useState([]);
  const [form, setForm]         = useState(EMPTY_FORM);
  const [submitting, setSubmitting] = useState(false);
  const [feedback, setFeedback] = useState(null);

  // Calendar state
  const now = new Date();
  const [calYear, setCalYear]   = useState(now.getFullYear());
  const [calMonth, setCalMonth] = useState(now.getMonth() + 1);
  const [calLeave, setCalLeave] = useState([]);

  const load = useCallback(async () => {
    try {
      const [balRes, reqRes] = await Promise.all([getMyLeaveBalances(), getMyLeaveRequests()]);
      const bal = balRes.data?.data ?? [];
      setBalances(bal);
      setLeaveTypes(bal.map(b => ({ id: b.leaveType?.id, name: b.leaveType?.name })).filter(t => t.id));
      setRequests(reqRes.data?.data ?? []);
    } catch { /* silent */ }
  }, []);

  const loadCalendar = useCallback(async () => {
    try {
      const res = await getLeaveCalendar(calYear, calMonth);
      setCalLeave(res.data?.data ?? []);
    } catch { /* silent */ }
  }, [calYear, calMonth]);

  useEffect(() => { load(); }, [load]);
  useEffect(() => { if (tab === 'calendar') loadCalendar(); }, [tab, loadCalendar]);

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
    } finally { setSubmitting(false); }
  };

  const handleCancel = async id => {
    try {
      await cancelLeaveRequest(id);
      await load();
    } catch { /* ignore */ }
  };

  // Calendar grid helpers
  const daysInMonth = new Date(calYear, calMonth, 0).getDate();
  const firstWeekday = new Date(calYear, calMonth - 1, 1).getDay() || 7; // Mon=1
  const cells = Array.from({ length: firstWeekday - 1 }, () => null)
    .concat(Array.from({ length: daysInMonth }, (_, i) => i + 1));

  const leaveByDay = {};
  calLeave.forEach(r => {
    const start = new Date(r.startDate);
    const end   = new Date(r.endDate);
    for (let d = new Date(start); d <= end; d.setDate(d.getDate() + 1)) {
      if (d.getFullYear() === calYear && d.getMonth() + 1 === calMonth) {
        const key = d.getDate();
        if (!leaveByDay[key]) leaveByDay[key] = [];
        leaveByDay[key].push(r);
      }
    }
  });

  const prevMonth = () => { if (calMonth === 1) { setCalYear(y => y - 1); setCalMonth(12); } else setCalMonth(m => m - 1); };
  const nextMonth = () => { if (calMonth === 12) { setCalYear(y => y + 1); setCalMonth(1); } else setCalMonth(m => m + 1); };

  return (
    <>
      <TopBar title='Leave Management' breadcrumb='Employee Hub / Leave' />
      <div className='page'>

        <div className='profile-tabs' style={{ marginBottom: '1.5rem' }}>
          {[['overview','Overview'],['apply','Apply'],['history','History'],['calendar','Team Calendar']].map(([key, label]) => (
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
                const total     = parseFloat(b.totalDays ?? 1);
                const pct       = Math.round((remaining / total) * 100);
                return (
                  <div className='card' key={b.id} style={{ padding: '1.25rem' }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '0.75rem' }}>
                      <div>
                        <p style={{ fontSize: '0.8rem', color: 'var(--text-muted)', marginBottom: '0.2rem' }}>{b.leaveType?.name}</p>
                        <p style={{ fontSize: '1.6rem', fontWeight: 700, lineHeight: 1 }}>{remaining}</p>
                        <p style={{ fontSize: '0.72rem', color: 'var(--text-muted)' }}>of {total} days remaining</p>
                      </div>
                      <div className='stat-card__icon stat-card__icon--blue'>
                        <i className='bi bi-calendar-check'></i>
                      </div>
                    </div>
                    <div style={{ height: 6, background: 'var(--border)', borderRadius: 'var(--radius-full)', overflow: 'hidden' }}>
                      <div style={{ height: '100%', width: `${pct}%`, background: COLORS[i % COLORS.length], borderRadius: 'var(--radius-full)', transition: 'width 0.4s ease' }} />
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
                    {requests.length === 0
                      ? <tr><td colSpan={5} style={{ textAlign: 'center', color: 'var(--text-muted)', padding: '2rem' }}>No leave requests yet.</td></tr>
                      : requests.slice(0, 5).map(r => (
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
                  <button type='submit' className='btn' disabled={submitting}>
                    <i className='bi bi-send'></i> {submitting ? 'Submitting...' : 'Submit Request'}
                  </button>
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
                  {requests.length === 0
                    ? <tr><td colSpan={7} style={{ textAlign: 'center', color: 'var(--text-muted)', padding: '2rem' }}>No leave requests yet.</td></tr>
                    : requests.map(r => (
                      <tr key={r.id}>
                        <td>{r.leaveType?.name}</td>
                        <td>{r.startDate}</td>
                        <td>{r.endDate}</td>
                        <td>{r.totalDays}</td>
                        <td style={{ color: 'var(--text-secondary)', maxWidth: 180, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{r.reason}</td>
                        <td><span className={`badge badge--${STATUS_COLOR[r.status] ?? 'pending'}`}>{r.status}</span></td>
                        <td>
                          {r.status === 'PENDING' && (
                            <button className='btn btn-ghost btn-sm btn-danger' onClick={() => handleCancel(r.id)}>Cancel</button>
                          )}
                        </td>
                      </tr>
                    ))}
                </tbody>
              </table>
            </div>
          </div>
        )}

        {/* ── Calendar ── */}
        {tab === 'calendar' && (
          <div className='card'>
            <div className='card__header'>
              <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
                <button className='btn btn-ghost btn-sm' onClick={prevMonth}><i className='bi bi-chevron-left'></i></button>
                <span className='card__title'>{MONTHS[calMonth - 1]} {calYear}</span>
                <button className='btn btn-ghost btn-sm' onClick={nextMonth}><i className='bi bi-chevron-right'></i></button>
              </div>
              <span style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>{calLeave.length} approved leave{calLeave.length !== 1 ? 's' : ''}</span>
            </div>
            <div className='card__body'>
              {/* Day headers */}
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(7,1fr)', gap: 4, marginBottom: 4 }}>
                {['Mon','Tue','Wed','Thu','Fri','Sat','Sun'].map(d => (
                  <div key={d} style={{ textAlign: 'center', fontSize: '0.72rem', fontWeight: 600, color: 'var(--text-muted)', padding: '0.3rem 0' }}>{d}</div>
                ))}
              </div>
              {/* Calendar cells */}
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(7,1fr)', gap: 4 }}>
                {cells.map((day, i) => {
                  const entries = day ? leaveByDay[day] : [];
                  const isToday = day && new Date().getFullYear() === calYear && new Date().getMonth() + 1 === calMonth && new Date().getDate() === day;
                  return (
                    <div key={i} style={{
                      minHeight: 64, padding: '0.3rem', borderRadius: 'var(--radius-sm)',
                      background: day ? 'var(--surface)' : 'transparent',
                      border: day ? `1px solid ${isToday ? 'var(--brand)' : 'var(--border)'}` : 'none',
                      position: 'relative',
                    }}>
                      {day && (
                        <>
                          <span style={{ fontSize: '0.72rem', fontWeight: isToday ? 700 : 400, color: isToday ? 'var(--brand)' : 'var(--text-secondary)' }}>{day}</span>
                          <div style={{ marginTop: 2, display: 'flex', flexDirection: 'column', gap: 2 }}>
                            {entries?.slice(0, 2).map((r, j) => (
                              <div key={j} style={{
                                fontSize: '0.65rem', background: 'var(--brand-light)', color: 'var(--brand)',
                                borderRadius: 3, padding: '1px 4px', overflow: 'hidden',
                                whiteSpace: 'nowrap', textOverflow: 'ellipsis',
                              }} title={`${r.employee?.firstName} ${r.employee?.lastName}`}>
                                {r.employee?.firstName}
                              </div>
                            ))}
                            {entries?.length > 2 && (
                              <div style={{ fontSize: '0.62rem', color: 'var(--text-muted)' }}>+{entries.length - 2} more</div>
                            )}
                          </div>
                        </>
                      )}
                    </div>
                  );
                })}
              </div>
            </div>
          </div>
        )}

      </div>
    </>
  );
}
