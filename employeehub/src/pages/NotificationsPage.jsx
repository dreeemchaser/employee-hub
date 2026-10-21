import { useState, useEffect, useCallback } from 'react';
import TopBar from '../components/TopBar';
import {
  getMyNotifications, markNotificationRead, markAllNotificationsRead,
  getNotificationPreferences, updateNotificationPreference,
} from '../api/EmployeeService';

const TYPE_ICON = {
  LEAVE:     'bi-calendar-check',
  TIMESHEET: 'bi-clock-history',
  SALARY:    'bi-cash-coin',
  DOCUMENT:  'bi-folder2-open',
  SYSTEM:    'bi-bell',
  GENERAL:   'bi-bell',
  ATTENDANCE:'bi-clock',
  OFFBOARDING:'bi-person-dash',
};

const TYPE_LABEL = {
  LEAVE: 'Leave', TIMESHEET: 'Timesheets', SALARY: 'Salary',
  DOCUMENT: 'Documents', GENERAL: 'General', ATTENDANCE: 'Attendance',
  OFFBOARDING: 'Offboarding',
};

export default function NotificationsPage() {
  const [notifications, setNotifs] = useState([]);
  const [loading, setLoading]      = useState(true);
  const [marking, setMarking]      = useState(false);
  const [preferences, setPreferences] = useState([]);
  const [preferenceBusy, setPreferenceBusy] = useState(null);

  const load = useCallback(async () => {
    try {
      const [notificationRes, preferenceRes] = await Promise.all([
        getMyNotifications(), getNotificationPreferences(),
      ]);
      setNotifs(notificationRes.data?.data ?? []);
      setPreferences(preferenceRes.data?.data ?? []);
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

  const handlePreference = async (type, enabled) => {
    setPreferenceBusy(type);
    try {
      await updateNotificationPreference(type, enabled);
      setPreferences(prev => prev.map(p => p.notificationType === type
        ? { ...p, emailEnabled: enabled } : p));
    } catch { /* keep the previous setting visible */ }
    finally { setPreferenceBusy(null); }
  };

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
        <div className='card' style={{ marginTop: '1rem' }}>
          <div className='card__header'>
            <span className='card__title'>Email preferences</span>
          </div>
          <div className='card__body'>
            <p style={{ color: 'var(--text-secondary)', fontSize: '0.82rem', marginBottom: '1rem' }}>
              In-app notifications are always enabled. Choose which event types may also send email.
            </p>
            <div style={{ display: 'grid', gap: '0.65rem' }}>
              {preferences.map(preference => (
                <label key={preference.notificationType} style={{
                  display: 'flex', alignItems: 'center', justifyContent: 'space-between',
                  padding: '0.7rem 0.85rem', border: '1px solid var(--border)',
                  borderRadius: 'var(--radius-sm)', cursor: 'pointer',
                }}>
                  <span style={{ display: 'flex', alignItems: 'center', gap: '0.6rem' }}>
                    <i className={`bi ${TYPE_ICON[preference.notificationType] ?? 'bi-bell'}`}></i>
                    {TYPE_LABEL[preference.notificationType] ?? preference.notificationType}
                  </span>
                  <input
                    type='checkbox'
                    checked={preference.emailEnabled}
                    disabled={preferenceBusy === preference.notificationType}
                    onChange={e => handlePreference(preference.notificationType, e.target.checked)}
                    aria-label={`Email ${TYPE_LABEL[preference.notificationType] ?? preference.notificationType} notifications`}
                  />
                </label>
              ))}
            </div>
          </div>
        </div>
      </div>
    </>
  );
}
