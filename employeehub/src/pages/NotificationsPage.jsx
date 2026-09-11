import { useState, useEffect, useCallback } from 'react';
import TopBar from '../components/TopBar';
import { getMyNotifications, markNotificationRead, markAllNotificationsRead } from '../api/EmployeeService';

const TYPE_ICON = {
  LEAVE:     'bi-calendar-check',
  TIMESHEET: 'bi-clock-history',
  SALARY:    'bi-cash-coin',
  DOCUMENT:  'bi-folder2-open',
  SYSTEM:    'bi-bell',
};

export default function NotificationsPage() {
  const [notifications, setNotifs] = useState([]);
  const [loading, setLoading]      = useState(true);
  const [marking, setMarking]      = useState(false);

  const load = useCallback(async () => {
    try {
      const res = await getMyNotifications();
      setNotifs(res.data?.data ?? []);
    } catch { /* silent */ } finally { setLoading(false); }
  }, []);

  useEffect(() => { load(); }, [load]);

  const handleMarkOne = async id => {
    try {
      await markNotificationRead(id);
      setNotifs(prev => prev.map(n => n.id === id ? { ...n, isRead: true } : n));
    } catch { /* ignore */ }
  };

  const handleMarkAll = async () => {
    setMarking(true);
    try {
      await markAllNotificationsRead();
      setNotifs(prev => prev.map(n => ({ ...n, isRead: true })));
    } catch { /* ignore */ } finally { setMarking(false); }
  };

  const unread = notifications.filter(n => !n.isRead).length;

  return (
    <>
      <TopBar title='Notifications' breadcrumb='Employee Hub / Notifications' />
      <div className='page'>
        <div className='card'>
          <div className='card__header'>
            <span className='card__title'>
              Notifications {unread > 0 && <span style={{ marginLeft: '0.4rem', background: 'var(--red)', color: '#fff', borderRadius: 'var(--radius-full)', fontSize: '0.7rem', padding: '1px 7px', fontWeight: 700 }}>{unread}</span>}
            </span>
            {unread > 0 && (
              <button className='btn btn-sm btn-ghost' onClick={handleMarkAll} disabled={marking}>
                <i className='bi bi-check2-all'></i> {marking ? 'Marking...' : 'Mark all read'}
              </button>
            )}
          </div>

          {loading ? (
            <div className='card__body'><p style={{ color: 'var(--text-muted)', fontSize: '0.85rem' }}>Loading...</p></div>
          ) : notifications.length === 0 ? (
            <div className='card__body'>
              <div className='empty-state'>
                <i className='bi bi-bell-slash'></i>
                <p>No notifications yet.</p>
              </div>
            </div>
          ) : (
            <div>
              {notifications.map((n, i) => (
                <div key={n.id} style={{
                  display: 'flex', alignItems: 'flex-start', gap: '1rem',
                  padding: '1rem 1.25rem',
                  borderBottom: i < notifications.length - 1 ? '1px solid var(--border)' : 'none',
                  background: n.isRead ? 'transparent' : 'var(--brand-light)',
                  transition: 'background 0.2s',
                }}>
                  <div style={{
                    width: 38, height: 38, borderRadius: '50%', flexShrink: 0,
                    background: n.isRead ? 'var(--border)' : 'var(--brand)',
                    display: 'flex', alignItems: 'center', justifyContent: 'center',
                  }}>
                    <i className={`bi ${TYPE_ICON[n.type] ?? 'bi-bell'}`} style={{ color: '#fff', fontSize: '0.9rem' }}></i>
                  </div>
                  <div style={{ flex: 1, minWidth: 0 }}>
                    <p style={{ fontSize: '0.88rem', fontWeight: n.isRead ? 400 : 600, marginBottom: '0.15rem' }}>{n.title}</p>
                    <p style={{ fontSize: '0.8rem', color: 'var(--text-secondary)', marginBottom: '0.25rem' }}>{n.message}</p>
                    <p style={{ fontSize: '0.72rem', color: 'var(--text-muted)' }}>
                      {n.createdAt ? new Date(n.createdAt).toLocaleString('en-ZA') : ''}
                    </p>
                  </div>
                  {!n.isRead && (
                    <button className='btn btn-ghost btn-sm' onClick={() => handleMarkOne(n.id)} title='Mark as read'>
                      <i className='bi bi-check-lg'></i>
                    </button>
                  )}
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </>
  );
}
