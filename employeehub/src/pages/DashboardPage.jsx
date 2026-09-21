import { useEffect, useState, useCallback, useMemo } from 'react';
import TopBar from '../components/TopBar';
import {
  getMyLeaveBalances, getMyLeaveRequests, getMyTimesheets,
  getMyNotifications, markNotificationRead, getEmployees,
  getMyAttendance, clockIn, clockOut, getMyDocuments,
} from '../api/EmployeeService';
import { isHrOrAdmin, getAccountKey } from '../api/AuthService';
import { useDashboardLayout } from '../hooks/useDashboardLayout';

const fmt = n => n != null ? String(n) : '—';
const STATUS_COLOR = { APPROVED: 'approved', REJECTED: 'rejected', PENDING: 'pending', DRAFT: 'inactive', SUBMITTED: 'pending', CANCELLED: 'inactive' };

// Stable ids + human labels for every personalizable card, in default display
// order (stat cards first, then the two body cards). The clock-in widget and
// Notifications card are intentionally absent: they are always shown, at fixed
// positions, and cannot be reordered or hidden.
const CARD_LABELS = {
  employeeCount:    'Total Employees',
  pendingLeave:     'Pending Leave',
  pendingTimesheets:'Timesheets Awaiting',
  annualLeaveLeft:  'Annual Leave Left',
  expiringDocs:     'Documents Expiring Soon',
  leaveBalances:    'Leave Balances',
  recentActivity:   'Recent Activity',
};
const ALL_CARD_IDS = Object.keys(CARD_LABELS);
const BODY_CARD_IDS = ['leaveBalances', 'recentActivity'];

export default function DashboardPage() {
  const [balances, setBalances]       = useState([]);
  const [leave, setLeave]             = useState([]);
  const [timesheets, setTimesheets]   = useState([]);
  const [notifications, setNotifs]    = useState([]);
  const [empCount, setEmpCount]       = useState('—');
  const [expiringDocsCount, setExpiringDocsCount] = useState(0);
  const [loading, setLoading]         = useState(true);
  const [openSession, setOpenSession] = useState(null);
  const [clocking, setClocking]       = useState(false);
  const [clockFeedback, setClockFeedback] = useState(null);
  const [showSettings, setShowSettings] = useState(false);
  const hrAdmin = isHrOrAdmin();

  const accountKey = useMemo(() => getAccountKey(), []);
  const { isHidden, toggleHidden, moveUp, moveDown, reset, orderedVisible } =
    useDashboardLayout(accountKey, ALL_CARD_IDS);

  const loadAttendance = useCallback(async () => {
    try {
      const res = await getMyAttendance(0, 1);
      const latest = res.data?.data?.content?.[0];
      setOpenSession(latest && latest.status === 'OPEN' ? latest : null);
    } catch { /* silent */ }
  }, []);

  const load = useCallback(async () => {
    const calls = [
      getMyLeaveBalances(),
      getMyLeaveRequests(),
      getMyTimesheets(),
      getMyNotifications(),
      getMyDocuments(),
      ...(hrAdmin ? [getEmployees(0, 1)] : []),
    ];
    const results = await Promise.allSettled(calls);
    if (results[0].status === 'fulfilled') setBalances(results[0].value.data?.data ?? []);
    if (results[1].status === 'fulfilled') setLeave(results[1].value.data?.data ?? []);
    if (results[2].status === 'fulfilled') setTimesheets(results[2].value.data?.data ?? []);
    if (results[3].status === 'fulfilled') setNotifs((results[3].value.data?.data ?? []).slice(0, 5));
    if (results[4].status === 'fulfilled') {
      const docs = results[4].value.data?.data ?? [];
      const in30Days = new Date();
      in30Days.setDate(in30Days.getDate() + 30);
      const expiringSoon = docs.filter(d => d.expiryDate && (d.expired || new Date(d.expiryDate) <= in30Days));
      setExpiringDocsCount(expiringSoon.length);
    }
    if (hrAdmin && results[5]?.status === 'fulfilled') {
      const d = results[5].value.data?.data ?? results[5].value.data;
      setEmpCount(d?.totalElements ?? '—');
    }
    setLoading(false);
  }, [hrAdmin]);

  useEffect(() => { load(); }, [load]);
  useEffect(() => { loadAttendance(); }, [loadAttendance]);

  const handleClockIn = async () => {
    setClocking(true);
    setClockFeedback(null);
    try {
      const res = await clockIn();
      setOpenSession(res.data?.data);
      setClockFeedback({ type: 'success', msg: 'Clocked in.' });
    } catch (err) {
      setClockFeedback({ type: 'error', msg: err.response?.data?.message ?? 'Failed to clock in.' });
    } finally {
      setClocking(false);
    }
  };

  const handleClockOut = async () => {
    setClocking(true);
    setClockFeedback(null);
    try {
      await clockOut();
      setOpenSession(null);
      setClockFeedback({ type: 'success', msg: 'Clocked out.' });
    } catch (err) {
      setClockFeedback({ type: 'error', msg: err.response?.data?.message ?? 'Failed to clock out.' });
    } finally {
      setClocking(false);
    }
  };

  const handleMarkRead = async id => {
    try {
      await markNotificationRead(id);
      setNotifs(prev => prev.filter(n => n.id !== id));
    } catch { /* ignore */ }
  };

  const pendingLeave     = leave.filter(r => r.status === 'PENDING').length;
  const pendingTS        = timesheets.filter(t => t.status === 'SUBMITTED').length;
  const annualBalance    = balances.find(b => b.leaveType?.name?.toLowerCase().includes('annual'));

  // Candidate stat cards, each tagged with its stable id. Data-dependent cards
  // (annual balance, expiring docs) are only candidates when they have something
  // to show — a hide/show preference can never force an empty card to appear.
  const statCandidates = [
    ...(hrAdmin ? [{ id: 'employeeCount', icon: 'bi-people', color: 'blue', value: fmt(empCount), label: 'Total Employees' }] : []),
    { id: 'pendingLeave', icon: 'bi-calendar-check', color: 'green', value: fmt(pendingLeave), label: 'Pending Leave' },
    { id: 'pendingTimesheets', icon: 'bi-clock-history', color: 'amber', value: fmt(pendingTS), label: 'Timesheets Awaiting' },
    annualBalance ? {
      id: 'annualLeaveLeft',
      icon: 'bi-sun',
      color: 'brand',
      value: `${parseFloat(annualBalance.remainingDays ?? 0)} days`,
      label: 'Annual Leave Left',
    } : null,
    expiringDocsCount > 0 ? {
      id: 'expiringDocs',
      icon: 'bi-file-earmark-x',
      color: 'red',
      value: fmt(expiringDocsCount),
      label: 'Documents Expiring Soon',
      href: '/documents',
    } : null,
  ].filter(Boolean);

  // Apply the saved order + hidden set. Stat cards and body cards are each
  // ordered within their own visual group (stat grid vs. body row).
  const statById = Object.fromEntries(statCandidates.map(c => [c.id, c]));
  const visibleStatCards = orderedVisible(statCandidates.map(c => c.id)).map(id => statById[id]);
  const visibleBodyCardIds = orderedVisible(BODY_CARD_IDS);

  // Card ids actually available right now (data-dependent ones may be missing) —
  // you can't reorder or toggle a card that has nothing to render. Shown in the
  // settings panel in the current saved order, including hidden ones (so they can
  // be toggled back on). orderedVisible drops hidden, so union it with the hidden
  // available ids, preserving saved order for the visible run then appending
  // hidden ones at the end.
  const availableCardIds = [...statCandidates.map(c => c.id), ...BODY_CARD_IDS];
  const visibleOrdered = orderedVisible(availableCardIds);
  const hiddenAvailable = availableCardIds.filter(id => isHidden(id));
  const settingsRows = [...visibleOrdered, ...hiddenAvailable];

  const recentActivity = [
    ...leave.slice(0, 3).map(r => ({
      icon: 'bi-calendar-check',
      color: STATUS_COLOR[r.status] ?? 'pending',
      text: `Leave request — ${r.leaveType?.name}`,
      sub: `${r.startDate} → ${r.endDate}`,
      status: r.status,
    })),
    ...timesheets.slice(0, 2).map(t => ({
      icon: 'bi-clock-history',
      color: STATUS_COLOR[t.status] ?? 'pending',
      text: `Timesheet — week of ${t.weekStartDate}`,
      sub: `${t.totalHours ?? 0}h logged`,
      status: t.status,
    })),
  ].slice(0, 5);

  if (loading) return (
    <>
      <TopBar title='Dashboard' breadcrumb='Employee Hub / Dashboard' />
      <div className='page'><p style={{ color: 'var(--text-muted)' }}>Loading...</p></div>
    </>
  );

  return (
    <>
      <TopBar title='Dashboard' breadcrumb='Employee Hub / Dashboard' />
      <div className='page'>

        {/* Clock in/out */}
        <div className='card' style={{ marginBottom: '1.5rem', padding: '1rem 1.5rem' }}>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: '0.75rem' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
              <div className={`stat-card__icon stat-card__icon--${openSession ? 'green' : 'blue'}`} style={{ width: 40, height: 40 }}>
                <i className='bi bi-clock-history'></i>
              </div>
              <div>
                <p style={{ fontWeight: 600, fontSize: '0.9rem' }}>
                  {openSession
                    ? `Clocked in since ${new Date(openSession.clockInAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}`
                    : 'Not clocked in'}
                </p>
                {clockFeedback && (
                  <p className={`feedback feedback--${clockFeedback.type}`} style={{ fontSize: '0.78rem', margin: 0 }}>
                    {clockFeedback.msg}
                  </p>
                )}
              </div>
            </div>
            {openSession ? (
              <button className='btn btn-sm btn-danger' onClick={handleClockOut} disabled={clocking}>
                <i className='bi bi-box-arrow-right'></i> {clocking ? 'Clocking out...' : 'Clock Out'}
              </button>
            ) : (
              <button className='btn btn-sm' style={{ background: 'var(--green)' }} onClick={handleClockIn} disabled={clocking}>
                <i className='bi bi-box-arrow-in-right'></i> {clocking ? 'Clocking in...' : 'Clock In'}
              </button>
            )}
          </div>
        </div>

        {/* Customize dashboard toggle */}
        <div style={{ display: 'flex', justifyContent: 'flex-end', marginBottom: '0.75rem' }}>
          <button className='btn btn-ghost btn-sm' onClick={() => setShowSettings(s => !s)}>
            <i className='bi bi-sliders'></i> Customize Dashboard
          </button>
        </div>

        {showSettings && (
          <div className='card' style={{ marginBottom: '1.5rem' }}>
            <div className='card__header'>
              <span className='card__title'>Customize Dashboard</span>
              <button className='btn btn-ghost btn-sm' onClick={reset}>Reset to default</button>
            </div>
            <div className='card__body' style={{ display: 'flex', flexDirection: 'column', gap: '0.4rem' }}>
              <p style={{ fontSize: '0.78rem', color: 'var(--text-muted)', marginBottom: '0.35rem' }}>
                Show, hide, and reorder the cards on your dashboard. Preferences are saved to this browser.
              </p>
              {settingsRows.map((id, i) => (
                <div key={id} style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: '0.75rem', padding: '0.4rem 0', borderBottom: i < settingsRows.length - 1 ? '1px solid var(--border)' : 'none' }}>
                  <label style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', fontSize: '0.85rem', cursor: 'pointer' }}>
                    <input
                      type='checkbox'
                      checked={!isHidden(id)}
                      onChange={() => toggleHidden(id)}
                      aria-label={`Show ${CARD_LABELS[id]}`}
                    />
                    <span style={{ color: isHidden(id) ? 'var(--text-muted)' : 'inherit' }}>{CARD_LABELS[id]}</span>
                  </label>
                  <div style={{ display: 'flex', gap: '0.25rem' }}>
                    <button
                      className='btn btn-ghost btn-sm'
                      onClick={() => moveUp(id)}
                      disabled={isHidden(id) || i === 0}
                      aria-label={`Move ${CARD_LABELS[id]} up`}
                      title='Move up'
                    >
                      <i className='bi bi-arrow-up'></i>
                    </button>
                    <button
                      className='btn btn-ghost btn-sm'
                      onClick={() => moveDown(id)}
                      disabled={isHidden(id) || i >= visibleOrdered.length - 1}
                      aria-label={`Move ${CARD_LABELS[id]} down`}
                      title='Move down'
                    >
                      <i className='bi bi-arrow-down'></i>
                    </button>
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* Stat cards */}
        {visibleStatCards.length > 0 && (
          <div className='stat-grid' style={{ marginBottom: '1.5rem' }}>
            {visibleStatCards.map(s => {
              const Wrapper = s.href ? 'a' : 'div';
              return (
                <Wrapper className='stat-card' key={s.id} href={s.href} style={s.href ? { cursor: 'pointer', textDecoration: 'none', color: 'inherit' } : undefined}>
                  <div className={`stat-card__icon stat-card__icon--${s.color}`}>
                    <i className={`bi ${s.icon}`}></i>
                  </div>
                  <div>
                    <div className='stat-card__value'>{s.value}</div>
                    <div className='stat-card__label'>{s.label}</div>
                  </div>
                </Wrapper>
              );
            })}
          </div>
        )}

        {visibleBodyCardIds.length > 0 && (
          <div
            style={{
              display: 'grid',
              gridTemplateColumns: visibleBodyCardIds.length > 1 ? '1fr 1fr' : '1fr',
              gap: '1.25rem',
              marginBottom: '1.25rem',
            }}
          >
            {visibleBodyCardIds.map(id => {
              if (id === 'leaveBalances') {
                return (
                  <div className='card' key={id}>
                    <div className='card__header'>
                      <span className='card__title'>Leave Balances</span>
                    </div>
                    <div className='card__body' style={{ display: 'flex', flexDirection: 'column', gap: '0.85rem' }}>
                      {balances.length === 0 ? (
                        <p style={{ color: 'var(--text-muted)', fontSize: '0.85rem' }}>No balances found.</p>
                      ) : balances.map((b, i) => {
                        const rem   = parseFloat(b.remainingDays ?? 0);
                        const total = parseFloat(b.totalDays ?? 1);
                        const pct   = Math.max(0, Math.min(100, Math.round((rem / total) * 100)));
                        const COLORS = ['var(--brand)', 'var(--red)', 'var(--amber)', 'var(--green)', 'hsl(270,60%,55%)', 'hsl(190,60%,40%)'];
                        return (
                          <div key={b.id}>
                            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '0.25rem' }}>
                              <span style={{ fontSize: '0.8rem', fontWeight: 500 }}>{b.leaveType?.name}</span>
                              <span style={{ fontSize: '0.78rem', color: 'var(--text-muted)' }}>{rem}/{total} days</span>
                            </div>
                            <div style={{ height: 6, background: 'var(--border)', borderRadius: 'var(--radius-full)', overflow: 'hidden' }}>
                              <div style={{ height: '100%', width: `${pct}%`, background: COLORS[i % COLORS.length], borderRadius: 'var(--radius-full)', transition: 'width 0.4s ease' }} />
                            </div>
                          </div>
                        );
                      })}
                    </div>
                  </div>
                );
              }
              if (id === 'recentActivity') {
                return (
                  <div className='card' key={id}>
                    <div className='card__header'><span className='card__title'>Recent Activity</span></div>
                    <div className='card__body'>
                      {recentActivity.length === 0 ? (
                        <div className='empty-state'><i className='bi bi-activity'></i><p>No recent activity.</p></div>
                      ) : recentActivity.map((a, i) => (
                        <div key={i} style={{ display: 'flex', alignItems: 'flex-start', gap: '0.75rem', padding: '0.55rem 0', borderBottom: i < recentActivity.length - 1 ? '1px solid var(--border)' : 'none' }}>
                          <div style={{ width: 32, height: 32, borderRadius: '50%', background: 'var(--brand-light)', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
                            <i className={`bi ${a.icon}`} style={{ color: 'var(--brand)', fontSize: '0.8rem' }}></i>
                          </div>
                          <div style={{ flex: 1, minWidth: 0 }}>
                            <p style={{ fontSize: '0.82rem', fontWeight: 500, marginBottom: '0.1rem' }}>{a.text}</p>
                            <p style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>{a.sub}</p>
                          </div>
                          <span className={`badge badge--${a.color}`} style={{ fontSize: '0.7rem', flexShrink: 0 }}>{a.status}</span>
                        </div>
                      ))}
                    </div>
                  </div>
                );
              }
              return null;
            })}
          </div>
        )}

        {/* Notifications */}
        <div className='card'>
          <div className='card__header'>
            <span className='card__title'>Notifications</span>
            <a href='/notifications' style={{ fontSize: '0.8rem', color: 'var(--brand)' }}>View all →</a>
          </div>
          <div className='card__body'>
            {notifications.length === 0 ? (
              <div className='empty-state'>
                <i className='bi bi-bell'></i>
                <p>No new notifications.</p>
              </div>
            ) : (
              <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
                {notifications.map(n => (
                  <div key={n.id} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '0.6rem 0', borderBottom: '1px solid var(--border)' }}>
                    <div>
                      <p style={{ fontSize: '0.85rem', fontWeight: 600 }}>{n.title}</p>
                      <p style={{ fontSize: '0.78rem', color: 'var(--text-muted)' }}>{n.message}</p>
                    </div>
                    <button className='btn btn-ghost btn-sm' onClick={() => handleMarkRead(n.id)}>
                      <i className='bi bi-check'></i>
                    </button>
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>

      </div>
    </>
  );
}
