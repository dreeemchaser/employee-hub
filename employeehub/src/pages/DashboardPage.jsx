import { useEffect, useState, useCallback } from 'react';
import TopBar from '../components/TopBar';
import {
  getMyLeaveBalances, getMyLeaveRequests, getMyTimesheets,
  getMyNotifications, markNotificationRead, getEmployees,
} from '../api/EmployeeService';
import { isHrOrAdmin } from '../api/AuthService';

const fmt = n => n != null ? String(n) : '—';
const STATUS_COLOR = { APPROVED: 'approved', REJECTED: 'rejected', PENDING: 'pending', DRAFT: 'inactive', SUBMITTED: 'pending', CANCELLED: 'inactive' };

export default function DashboardPage() {
  const [balances, setBalances]       = useState([]);
  const [leave, setLeave]             = useState([]);
  const [timesheets, setTimesheets]   = useState([]);
  const [notifications, setNotifs]    = useState([]);
  const [empCount, setEmpCount]       = useState('—');
  const [loading, setLoading]         = useState(true);
  const hrAdmin = isHrOrAdmin();

  const load = useCallback(async () => {
    const calls = [
      getMyLeaveBalances(),
      getMyLeaveRequests(),
      getMyTimesheets(),
      getMyNotifications(),
      ...(hrAdmin ? [getEmployees(0, 1)] : []),
    ];
    const results = await Promise.allSettled(calls);
    if (results[0].status === 'fulfilled') setBalances(results[0].value.data?.data ?? []);
    if (results[1].status === 'fulfilled') setLeave(results[1].value.data?.data ?? []);
    if (results[2].status === 'fulfilled') setTimesheets(results[2].value.data?.data ?? []);
    if (results[3].status === 'fulfilled') setNotifs((results[3].value.data?.data ?? []).slice(0, 5));
    if (hrAdmin && results[4]?.status === 'fulfilled') {
      const d = results[4].value.data?.data ?? results[4].value.data;
      setEmpCount(d?.totalElements ?? '—');
    }
    setLoading(false);
  }, [hrAdmin]);

  useEffect(() => { load(); }, [load]);

  const handleMarkRead = async id => {
    try {
      await markNotificationRead(id);
      setNotifs(prev => prev.filter(n => n.id !== id));
    } catch { /* ignore */ }
  };

  const pendingLeave     = leave.filter(r => r.status === 'PENDING').length;
  const pendingTS        = timesheets.filter(t => t.status === 'SUBMITTED').length;
  const annualBalance    = balances.find(b => b.leaveType?.name?.toLowerCase().includes('annual'));

  const STAT_CARDS = [
    ...(hrAdmin ? [{ icon: 'bi-people', color: 'blue', value: fmt(empCount), label: 'Total Employees' }] : []),
    { icon: 'bi-calendar-check', color: 'green', value: fmt(pendingLeave), label: 'Pending Leave' },
    { icon: 'bi-clock-history',  color: 'amber', value: fmt(pendingTS),    label: 'Timesheets Awaiting' },
    annualBalance ? {
      icon: 'bi-sun',
      color: 'brand',
      value: `${parseFloat(annualBalance.remainingDays ?? 0)} days`,
      label: 'Annual Leave Left',
    } : null,
  ].filter(Boolean);

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

        {/* Stat cards */}
        <div className='stat-grid' style={{ marginBottom: '1.5rem' }}>
          {STAT_CARDS.map(s => (
            <div className='stat-card' key={s.label}>
              <div className={`stat-card__icon stat-card__icon--${s.color}`}>
                <i className={`bi ${s.icon}`}></i>
              </div>
              <div>
                <div className='stat-card__value'>{s.value}</div>
                <div className='stat-card__label'>{s.label}</div>
              </div>
            </div>
          ))}
        </div>

        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1.25rem', marginBottom: '1.25rem' }}>

          {/* Leave balance mini-bars */}
          <div className='card'>
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

          {/* Recent activity */}
          <div className='card'>
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
        </div>

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
