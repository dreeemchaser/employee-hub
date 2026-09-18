import { useState, useEffect, useCallback, useMemo } from 'react';
import TopBar from '../components/TopBar';
import {
  getMyLeaveBalances, getMyLeaveRequests,
  submitLeaveRequest, cancelLeaveRequest, getLeaveCalendar,
} from '../api/EmployeeService';

const EMPTY_FORM = { leaveTypeId: '', startDate: '', endDate: '', reason: '', documentationConfirmed: false };
const STATUS_COLOR = { APPROVED: 'approved', REJECTED: 'rejected', PENDING: 'pending', CANCELLED: 'inactive' };
const MONTHS = ['January','February','March','April','May','June','July','August','September','October','November','December'];
const COLORS = ['var(--brand)','var(--red)','var(--amber)','var(--green)','hsl(270,60%,55%)','hsl(190,60%,40%)'];
const ANNUAL_LEAVE_NOTICE_DAYS = 14;
const SICK_LEAVE_DOC_THRESHOLD = 3;

// Per-leave-type styling for the team calendar. Keyed by lowercased type name.
// Each entry carries a colour (used for the chip) and an emoji (quick glance cue).
const LEAVE_TYPE_STYLES = {
  'annual leave':                { color: 'hsl(221, 83%, 53%)', emoji: '🌴' },
  'sick leave':                  { color: 'hsl(351, 83%, 55%)', emoji: '🤒' },
  'family responsibility leave': { color: 'hsl(38, 92%, 45%)',  emoji: '👪' },
  'maternity leave':             { color: 'hsl(316, 70%, 55%)', emoji: '🤱' },
  'parental leave':              { color: 'hsl(270, 60%, 55%)', emoji: '🍼' },
  'study leave':                 { color: 'hsl(190, 65%, 42%)', emoji: '📚' },
};
const DEFAULT_LEAVE_STYLE = { color: 'hsl(215, 15%, 45%)', emoji: '📅' };

function leaveStyle(typeName) {
  return LEAVE_TYPE_STYLES[(typeName ?? '').toLowerCase()] ?? DEFAULT_LEAVE_STYLE;
}

// Count Mon–Fri days between two date strings (matches backend exactly)
function countWorkingDays(startStr, endStr) {
  if (!startStr || !endStr) return 0;
  const start = new Date(startStr);
  const end   = new Date(endStr);
  if (end < start) return 0;
  let count = 0;
  const cur = new Date(start);
  while (cur <= end) {
    const dow = cur.getDay();
    if (dow !== 0 && dow !== 6) count++;
    cur.setDate(cur.getDate() + 1);
  }
  return count;
}

// Days from today until a date string (calendar days)
function daysFromToday(dateStr) {
  if (!dateStr) return null;
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  const target = new Date(dateStr);
  return Math.floor((target - today) / 86400000);
}

export default function LeavePage() {
  const [tab, setTab]               = useState('overview');
  const [balances, setBalances]     = useState([]);
  const [requests, setRequests]     = useState([]);
  const [form, setForm]             = useState(EMPTY_FORM);
  const [submitting, setSubmitting] = useState(false);
  const [feedback, setFeedback]     = useState(null);

  // Calendar state
  const now = new Date();
  const [calYear, setCalYear]   = useState(now.getFullYear());
  const [calMonth, setCalMonth] = useState(now.getMonth() + 1);
  const [calLeave, setCalLeave] = useState([]);

  const load = useCallback(async () => {
    try {
      const [balRes, reqRes] = await Promise.all([getMyLeaveBalances(), getMyLeaveRequests()]);
      setBalances(balRes.data?.data ?? []);
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

  // ── Derived form state ─────────────────────────────────────────
  const leaveTypes = useMemo(
    () => balances.map(b => ({ id: b.leaveType?.id, name: b.leaveType?.name, requiresDocs: b.leaveType?.requiresDocumentation })).filter(t => t.id),
    [balances]
  );

  const selectedBalance = useMemo(
    () => balances.find(b => String(b.leaveType?.id) === String(form.leaveTypeId)) ?? null,
    [balances, form.leaveTypeId]
  );

  const selectedTypeName = selectedBalance?.leaveType?.name ?? '';
  const workingDays      = countWorkingDays(form.startDate, form.endDate);
  const daysUntilStart   = daysFromToday(form.startDate);
  const remaining        = parseFloat(selectedBalance?.remainingDays ?? 0);

  // Sick leave beyond the threshold requires the employee to confirm they have
  // emailed their manager the supporting documentation before they can submit.
  const requiresDocConfirmation =
    selectedTypeName.toLowerCase() === 'sick leave' && workingDays > SICK_LEAVE_DOC_THRESHOLD;

  // ── Inline warnings (computed before submit) ───────────────────
  const warnings = useMemo(() => {
    const w = [];
    if (!form.leaveTypeId || !form.startDate || !form.endDate) return w;

    // End before start
    if (form.endDate && form.startDate && form.endDate < form.startDate) {
      w.push({ type: 'error', msg: 'End date cannot be before start date.' });
      return w; // no point computing further
    }

    // No working days
    if (workingDays === 0 && form.startDate && form.endDate) {
      w.push({ type: 'error', msg: 'The selected range contains no working days (Mon–Fri).' });
      return w;
    }

    // Insufficient balance
    if (selectedBalance && workingDays > remaining) {
      w.push({
        type: 'error',
        msg: `Insufficient balance. You have ${remaining} day(s) remaining but are requesting ${workingDays} day(s).`,
      });
    }

    // Annual leave notice period
    if (selectedTypeName.toLowerCase() === 'annual leave' && daysUntilStart !== null && daysUntilStart < ANNUAL_LEAVE_NOTICE_DAYS) {
      w.push({
        type: 'warning',
        msg: `Annual leave requires at least ${ANNUAL_LEAVE_NOTICE_DAYS} days' advance notice. Your start date is ${daysUntilStart} day(s) away — please speak to your manager before submitting.`,
      });
    }

    // Sick leave > 3 days — doctor's note required (confirm via checkbox below)
    if (selectedTypeName.toLowerCase() === 'sick leave' && workingDays > SICK_LEAVE_DOC_THRESHOLD) {
      w.push({
        type: 'warning',
        msg: `Sick leave exceeding ${SICK_LEAVE_DOC_THRESHOLD} days requires a doctor's note. Email your manager the supporting documentation, then confirm below to submit.`,
        icon: 'bi-envelope-exclamation',
      });
    }

    // Other documentation-required leave types
    if (selectedBalance?.leaveType?.requiresDocumentation && selectedTypeName.toLowerCase() !== 'sick leave') {
      w.push({
        type: 'info',
        msg: `${selectedTypeName} requires supporting documentation. Please have your documents ready and email them to HR or your manager.`,
        icon: 'bi-paperclip',
      });
    }

    return w;
  }, [form, workingDays, remaining, daysUntilStart, selectedBalance, selectedTypeName]);

  const hasBlockingWarning = warnings.some(w => w.type === 'error');

  const set = e => {
    const { name, type, value, checked } = e.target;
    setForm(f => ({ ...f, [name]: type === 'checkbox' ? checked : value }));
    setFeedback(null);
  };

  const handleSubmit = async e => {
    e.preventDefault();
    if (hasBlockingWarning) return;
    if (requiresDocConfirmation && !form.documentationConfirmed) return;
    setSubmitting(true);
    setFeedback(null);
    try {
      await submitLeaveRequest({
        leaveTypeId:            form.leaveTypeId,
        startDate:              form.startDate,
        endDate:                form.endDate,
        reason:                 form.reason,
        documentationConfirmed: form.documentationConfirmed,
      });
      setFeedback({ type: 'success', msg: 'Leave request submitted successfully.' });
      setForm(EMPTY_FORM);
      await load();
      setTimeout(() => setTab('history'), 1400);
    } catch (err) {
      let msg = err.response?.data?.message ?? 'Failed to submit request.';
      // Strip the REQUIRES_DOCUMENTATION sentinel prefix if backend returns it
      if (msg.startsWith('REQUIRES_DOCUMENTATION:')) {
        msg = msg.replace('REQUIRES_DOCUMENTATION:', '').trim();
      }
      setFeedback({ type: 'error', msg });
    } finally {
      setSubmitting(false);
    }
  };

  const handleCancel = async id => {
    if (!window.confirm('Cancel this leave request?')) return;
    try {
      await cancelLeaveRequest(id);
      await load();
    } catch (err) {
      alert(err.response?.data?.message ?? 'Could not cancel request.');
    }
  };

  // ── Calendar helpers ───────────────────────────────────────────
  const daysInMonth   = new Date(calYear, calMonth, 0).getDate();
  const firstWeekday  = new Date(calYear, calMonth - 1, 1).getDay() || 7;
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

  // Distinct leave-type names present this month, for the calendar legend.
  const calLegend = [...new Set(calLeave.map(r => r.leaveType?.name).filter(Boolean))]
    .sort((a, b) => a.localeCompare(b));

  const prevMonth = () => { if (calMonth === 1) { setCalYear(y => y-1); setCalMonth(12); } else setCalMonth(m => m-1); };
  const nextMonth = () => { if (calMonth === 12) { setCalYear(y => y+1); setCalMonth(1); } else setCalMonth(m => m+1); };

  // Min date for start date input = today
  const todayStr = new Date().toISOString().split('T')[0];

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
                const low       = pct <= 25;
                return (
                  <div className='card' key={b.id} style={{ padding: '1.25rem' }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '0.75rem' }}>
                      <div>
                        <p style={{ fontSize: '0.8rem', color: 'var(--text-muted)', marginBottom: '0.2rem' }}>{b.leaveType?.name}</p>
                        <p style={{ fontSize: '1.6rem', fontWeight: 700, lineHeight: 1, color: low ? 'var(--red)' : 'inherit' }}>{remaining}</p>
                        <p style={{ fontSize: '0.72rem', color: 'var(--text-muted)' }}>of {total} days remaining</p>
                      </div>
                      <div className='stat-card__icon stat-card__icon--blue'>
                        <i className='bi bi-calendar-check'></i>
                      </div>
                    </div>
                    <div style={{ height: 6, background: 'var(--border)', borderRadius: 'var(--radius-full)', overflow: 'hidden' }}>
                      <div style={{
                        height: '100%', width: `${pct}%`,
                        background: low ? 'var(--red)' : COLORS[i % COLORS.length],
                        borderRadius: 'var(--radius-full)', transition: 'width 0.4s ease',
                      }} />
                    </div>
                    {low && (
                      <p style={{ fontSize: '0.7rem', color: 'var(--red)', marginTop: '0.4rem' }}>
                        <i className='bi bi-exclamation-circle'></i> Low balance
                      </p>
                    )}
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
          <div className='card' style={{ maxWidth: 620 }}>
            <div className='card__header'><span className='card__title'>Apply for Leave</span></div>
            <div className='card__body'>

              {/* Server feedback */}
              {feedback && (
                <div className={`feedback feedback--${feedback.type}`} style={{ marginBottom: '1.25rem' }}>
                  <i className={`bi ${feedback.type === 'success' ? 'bi-check-circle' : 'bi-exclamation-circle'}`}></i> {feedback.msg}
                </div>
              )}

              <form onSubmit={handleSubmit}>
                {/* Leave type */}
                <div className='form-group' style={{ marginBottom: '1rem' }}>
                  <label className='form-label'>Leave Type</label>
                  <select className='form-control' name='leaveTypeId' value={form.leaveTypeId} onChange={set} required>
                    <option value=''>— Select type —</option>
                    {leaveTypes.map(t => <option key={t.id} value={t.id}>{t.name}</option>)}
                  </select>
                </div>

                {/* Dates */}
                <div className='form-grid' style={{ marginBottom: '1rem' }}>
                  <div className='form-group'>
                    <label className='form-label'>Start Date</label>
                    <input
                      className='form-control'
                      type='date'
                      name='startDate'
                      value={form.startDate}
                      min={todayStr}
                      onChange={set}
                      required
                    />
                  </div>
                  <div className='form-group'>
                    <label className='form-label'>End Date</label>
                    <input
                      className='form-control'
                      type='date'
                      name='endDate'
                      value={form.endDate}
                      min={form.startDate || todayStr}
                      onChange={set}
                      required
                    />
                  </div>
                </div>

                {/* Live balance + day count summary */}
                {form.leaveTypeId && form.startDate && form.endDate && !hasBlockingWarning && workingDays > 0 && (
                  <div style={{
                    display: 'flex', gap: '1.5rem', alignItems: 'center',
                    background: 'var(--brand-light)', borderRadius: 'var(--radius-sm)',
                    padding: '0.65rem 1rem', marginBottom: '1rem',
                    fontSize: '0.83rem',
                  }}>
                    <span>
                      <i className='bi bi-calendar-range' style={{ marginRight: '0.3rem', color: 'var(--brand)' }}></i>
                      <strong>{workingDays}</strong> working day{workingDays !== 1 ? 's' : ''} requested
                    </span>
                    <span style={{ color: 'var(--text-secondary)' }}>·</span>
                    <span>
                      <i className='bi bi-wallet2' style={{ marginRight: '0.3rem', color: 'var(--brand)' }}></i>
                      <strong style={{ color: remaining - workingDays < 0 ? 'var(--red)' : 'inherit' }}>
                        {remaining - workingDays}
                      </strong> day{remaining - workingDays !== 1 ? 's' : ''} remaining after approval
                    </span>
                  </div>
                )}

                {/* Inline warnings */}
                {warnings.length > 0 && (
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem', marginBottom: '1rem' }}>
                    {warnings.map((w, i) => (
                      <div key={i} style={{
                        display: 'flex', gap: '0.65rem', alignItems: 'flex-start',
                        padding: '0.75rem 1rem',
                        borderRadius: 'var(--radius-sm)',
                        fontSize: '0.83rem',
                        background: w.type === 'error'   ? 'var(--red-bg)'
                                  : w.type === 'warning' ? 'var(--amber-bg)'
                                  : 'var(--brand-light)',
                        border: `1px solid ${
                          w.type === 'error'   ? 'hsla(351,83%,55%,0.25)'
                        : w.type === 'warning' ? 'hsla(38,92%,40%,0.3)'
                        :                        'hsla(221,83%,53%,0.2)'}`,
                        color: w.type === 'error'   ? 'var(--red)'
                             : w.type === 'warning' ? 'var(--amber)'
                             : 'var(--brand)',
                      }}>
                        <i className={`bi ${w.icon ?? (w.type === 'error' ? 'bi-x-circle' : w.type === 'warning' ? 'bi-exclamation-triangle' : 'bi-info-circle')}`}
                          style={{ fontSize: '1rem', flexShrink: 0, marginTop: '0.1rem' }} />
                        <span>{w.msg}</span>
                      </div>
                    ))}
                  </div>
                )}

                {/* Documentation confirmation gate (sick leave > threshold) */}
                {requiresDocConfirmation && (
                  <label
                    htmlFor='documentationConfirmed'
                    style={{
                      display: 'flex', gap: '0.65rem', alignItems: 'flex-start',
                      padding: '0.85rem 1rem', marginBottom: '1rem',
                      borderRadius: 'var(--radius-sm)',
                      background: 'var(--amber-bg)',
                      border: '1px solid hsla(38,92%,40%,0.3)',
                      cursor: 'pointer', fontSize: '0.85rem',
                    }}
                  >
                    <input
                      id='documentationConfirmed'
                      type='checkbox'
                      name='documentationConfirmed'
                      checked={form.documentationConfirmed}
                      onChange={set}
                      style={{ marginTop: '0.15rem', flexShrink: 0 }}
                    />
                    <span>Did you email your manager with the relevant information and documents?</span>
                  </label>
                )}

                {/* Reason */}
                <div className='form-group' style={{ marginBottom: '1.25rem' }}>
                  <label className='form-label'>
                    Reason <span style={{ color: 'var(--text-muted)', fontWeight: 400 }}>(optional)</span>
                  </label>
                  <textarea
                    className='form-control'
                    name='reason'
                    value={form.reason}
                    onChange={set}
                    rows={3}
                    placeholder='Brief reason for your leave request…'
                  />
                </div>

                <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.5rem' }}>
                  <button type='button' className='btn btn-ghost' onClick={() => { setForm(EMPTY_FORM); setFeedback(null); }}>
                    Clear
                  </button>
                  <button
                    type='submit'
                    className='btn'
                    disabled={submitting || hasBlockingWarning || !form.leaveTypeId || !form.startDate || !form.endDate || (requiresDocConfirmation && !form.documentationConfirmed)}
                  >
                    <i className='bi bi-send'></i> {submitting ? 'Submitting…' : 'Submit Request'}
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
                <thead>
                  <tr><th>Type</th><th>From</th><th>To</th><th>Days</th><th>Reason</th><th>Status</th><th></th></tr>
                </thead>
                <tbody>
                  {requests.length === 0
                    ? <tr><td colSpan={7} style={{ textAlign: 'center', color: 'var(--text-muted)', padding: '2rem' }}>No leave requests yet.</td></tr>
                    : requests.map(r => (
                      <tr key={r.id}>
                        <td style={{ fontWeight: 500 }}>{r.leaveType?.name}</td>
                        <td>{r.startDate}</td>
                        <td>{r.endDate}</td>
                        <td>{r.totalDays}</td>
                        <td style={{ color: 'var(--text-secondary)', maxWidth: 180, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}
                          title={r.reason}>{r.reason || '—'}</td>
                        <td>
                          <span className={`badge badge--${STATUS_COLOR[r.status] ?? 'pending'}`}>{r.status}</span>
                          {r.status === 'REJECTED' && r.rejectionReason && (
                            <span style={{ display: 'block', fontSize: '0.7rem', color: 'var(--red)', marginTop: 2 }}
                              title={r.rejectionReason}>
                              <i className='bi bi-info-circle'></i> {r.rejectionReason.slice(0, 40)}{r.rejectionReason.length > 40 ? '…' : ''}
                            </span>
                          )}
                        </td>
                        <td>
                          {r.status === 'PENDING' && (
                            <button className='btn btn-ghost btn-sm' style={{ color: 'var(--red)' }} onClick={() => handleCancel(r.id)}>
                              Cancel
                            </button>
                          )}
                        </td>
                      </tr>
                    ))}
                </tbody>
              </table>
            </div>
          </div>
        )}

        {/* ── Team Calendar ── */}
        {tab === 'calendar' && (
          <div className='card'>
            <div className='card__header'>
              <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
                <button className='btn btn-ghost btn-sm' onClick={prevMonth}><i className='bi bi-chevron-left'></i></button>
                <span className='card__title'>{MONTHS[calMonth - 1]} {calYear}</span>
                <button className='btn btn-ghost btn-sm' onClick={nextMonth}><i className='bi bi-chevron-right'></i></button>
              </div>
              <span style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>
                {calLeave.length} approved leave{calLeave.length !== 1 ? 's' : ''}
              </span>
            </div>
            <div className='card__body'>
              {/* Legend — only the leave types present this month */}
              {calLegend.length > 0 && (
                <div style={{
                  display: 'flex', flexWrap: 'wrap', gap: '0.75rem',
                  padding: '0.6rem 0.75rem', marginBottom: '0.85rem',
                  background: 'var(--surface)', border: '1px solid var(--border)',
                  borderRadius: 'var(--radius-sm)', fontSize: '0.75rem',
                }}>
                  {calLegend.map(name => {
                    const st = leaveStyle(name);
                    return (
                      <span key={name} style={{ display: 'inline-flex', alignItems: 'center', gap: '0.35rem' }}>
                        <span aria-hidden='true' style={{
                          width: 11, height: 11, borderRadius: 3, background: st.color, flexShrink: 0,
                        }} />
                        <span>{st.emoji} {name}</span>
                      </span>
                    );
                  })}
                </div>
              )}
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(7,1fr)', gap: 4, marginBottom: 4 }}>
                {['Mon','Tue','Wed','Thu','Fri','Sat','Sun'].map(d => (
                  <div key={d} style={{ textAlign: 'center', fontSize: '0.72rem', fontWeight: 600, color: 'var(--text-muted)', padding: '0.3rem 0' }}>{d}</div>
                ))}
              </div>
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(7,1fr)', gap: 4 }}>
                {cells.map((day, i) => {
                  const entries = day ? leaveByDay[day] : [];
                  const today   = new Date();
                  const isToday = day && today.getFullYear() === calYear && today.getMonth() + 1 === calMonth && today.getDate() === day;
                  const isWeekend = i % 7 >= 5; // Sat/Sun columns
                  return (
                    <div key={i} style={{
                      minHeight: 64, padding: '0.3rem',
                      borderRadius: 'var(--radius-sm)',
                      background: !day ? 'transparent' : isWeekend ? 'hsla(214,17%,90%,0.4)' : 'var(--surface)',
                      border: day ? `1px solid ${isToday ? 'var(--brand)' : 'var(--border)'}` : 'none',
                    }}>
                      {day && (
                        <>
                          <span style={{ fontSize: '0.72rem', fontWeight: isToday ? 700 : 400, color: isToday ? 'var(--brand)' : isWeekend ? 'var(--text-muted)' : 'var(--text-secondary)' }}>{day}</span>
                          <div style={{ marginTop: 2, display: 'flex', flexDirection: 'column', gap: 2 }}>
                            {entries?.slice(0, 2).map((r, j) => {
                              const st = leaveStyle(r.leaveType?.name);
                              return (
                                <div key={j} style={{
                                  fontSize: '0.65rem',
                                  background: st.color, color: '#fff',
                                  borderRadius: 3, padding: '1px 4px',
                                  overflow: 'hidden', whiteSpace: 'nowrap', textOverflow: 'ellipsis',
                                }} title={`${r.employee?.firstName} ${r.employee?.lastName} — ${r.leaveType?.name}`}>
                                  {st.emoji} {r.employee?.firstName}
                                </div>
                              );
                            })}
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
