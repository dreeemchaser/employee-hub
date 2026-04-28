import { useEffect, useState, useCallback } from 'react';
import TopBar from '../components/TopBar';
import { getEmployees, getMyLeaveRequests, getMyTimesheets, getMyDocuments, getMyNotifications, markNotificationRead } from '../api/EmployeeService';
import { isHrOrAdmin } from '../api/AuthService';

export default function DashboardPage() {
  const [stats, setStats] = useState({ employees: '—', leave: '—', timesheets: '—', documents: '—' });
  const [notifications, setNotifications] = useState([]);

  const load = useCallback(async () => {
    try {
      const [empRes, leaveRes, tsRes, docRes, notifRes] = await Promise.allSettled([
        getEmployees(0, 1),
        getMyLeaveRequests(),
        getMyTimesheets(),
        getMyDocuments(),
        getMyNotifications(),
      ]);

      setStats({
        employees:  empRes.status === 'fulfilled'   ? (empRes.value.data?.totalElements ?? empRes.value.data?.data?.totalElements ?? '—') : '—',
        leave:      leaveRes.status === 'fulfilled'  ? (leaveRes.value.data?.data?.filter(r => r.status === 'PENDING').length ?? '—') : '—',
        timesheets: tsRes.status === 'fulfilled'     ? (tsRes.value.data?.data?.filter(t => t.status === 'SUBMITTED').length ?? '—') : '—',
        documents:  docRes.status === 'fulfilled'    ? (docRes.value.data?.data?.filter(d => d.status === 'PENDING').length ?? '—') : '—',
      });

      if (notifRes.status === 'fulfilled') {
        setNotifications((notifRes.value.data?.data ?? []).slice(0, 5));
      }
    } catch {
      // silently fail
    }
  }, []);

  useEffect(() => { load(); }, [load]);

  const handleMarkRead = async (id) => {
    try {
      await markNotificationRead(id);
      setNotifications(prev => prev.filter(n => n.id !== id));
    } catch {
      // ignore
    }
  };

  const hrOrAdmin = isHrOrAdmin();

  const STAT_CARDS = [
    ...(hrOrAdmin ? [{ icon: 'bi-people', color: 'blue', value: stats.employees, label: 'Total Employees' }] : []),
    { icon: 'bi-calendar-check', color: 'green', value: stats.leave,      label: 'Pending Leave' },
    { icon: 'bi-clock-history',  color: 'amber', value: stats.timesheets, label: 'Pending Timesheets' },
    { icon: 'bi-folder2-open',   color: 'red',   value: stats.documents,  label: 'Documents Pending' },
  ];

  return (
    <>
      <TopBar title='Dashboard' breadcrumb='Employee Hub / Dashboard' />
      <div className='page'>
        <div className='stat-grid'>
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

        <div className='card'>
          <div className='card__header'>
            <span className='card__title'>Notifications</span>
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
