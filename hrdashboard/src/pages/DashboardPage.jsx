import { useEffect, useState, useCallback } from 'react';
import TopBar from '../components/TopBar';
import Spinner from '../components/Spinner';
import { getEmployees, getAllLeaveRequests, getAllTimesheets, getAllDocuments } from '../api/HrService';

const STATUS_COLOR = { APPROVED: 'approved', REJECTED: 'rejected', PENDING: 'pending', SUBMITTED: 'pending', DRAFT: 'inactive', VERIFIED: 'approved' };

export default function DashboardPage() {
  const [stats, setStats]       = useState(null);
  const [recentLeave, setLeave] = useState([]);
  const [recentTs, setTs]       = useState([]);
  const [loading, setLoading]   = useState(true);

  const load = useCallback(async () => {
    const [empRes, leaveRes, tsRes, docRes] = await Promise.allSettled([
      getEmployees(0, 1),
      getAllLeaveRequests(),
      getAllTimesheets(),
      getAllDocuments(),
    ]);

    const empData  = empRes.value?.data?.data ?? empRes.value?.data;
    const leaves   = leaveRes.status === 'fulfilled' ? (leaveRes.value.data?.data ?? []) : [];
    const tsheets  = tsRes.status    === 'fulfilled' ? (tsRes.value.data?.data ?? []) : [];
    const docs     = docRes.status   === 'fulfilled' ? (docRes.value.data?.data ?? []) : [];

    setStats({
      employees:          empRes.status === 'fulfilled' ? (empData?.totalElements ?? '—') : '—',
      pendingLeave:       leaves.filter(r => r.status === 'PENDING').length,
      pendingTimesheets:  tsheets.filter(t => t.status === 'SUBMITTED').length,
      pendingDocuments:   docs.filter(d => d.status === 'PENDING').length,
      approvedLeave:      leaves.filter(r => r.status === 'APPROVED').length,
      activeEmployees:    empData?.totalElements ?? '—',
    });
    setLeave(leaves.filter(r => r.status === 'PENDING').slice(0, 5));
    setTs(tsheets.filter(t => t.status === 'SUBMITTED').slice(0, 5));
    setLoading(false);
  }, []);

  useEffect(() => { load(); }, [load]);

  const STAT_CARDS = stats ? [
    { icon: 'bi-people',         color: 'blue',  value: stats.employees,         label: 'Total Employees' },
    { icon: 'bi-calendar-check', color: 'amber', value: stats.pendingLeave,      label: 'Pending Leave' },
    { icon: 'bi-clock-history',  color: 'amber', value: stats.pendingTimesheets, label: 'Pending Timesheets' },
    { icon: 'bi-folder2-open',   color: 'red',   value: stats.pendingDocuments,  label: 'Pending Docs' },
    { icon: 'bi-calendar2-check',color: 'green', value: stats.approvedLeave,     label: 'Approved Leaves' },
  ] : [];

  return (
    <>
      <TopBar title='Dashboard' breadcrumb='HR Admin / Dashboard' />
      <div className='page'>
        {loading ? <Spinner /> : (
          <>
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

            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1.25rem' }}>
              {/* Pending leave */}
              <div className='card'>
                <div className='card__header'>
                  <span className='card__title'>Pending Leave Requests</span>
                  <a href='/leave-approvals' style={{ fontSize: '0.8rem', color: 'var(--brand)' }}>View all →</a>
                </div>
                {recentLeave.length === 0 ? (
                  <div className='card__body'>
                    <div className='empty-state'><i className='bi bi-calendar-check'></i><p>No pending requests.</p></div>
                  </div>
                ) : recentLeave.map((r, i) => (
                  <div key={r.id} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '0.75rem 1.25rem', borderBottom: i < recentLeave.length - 1 ? '1px solid var(--border)' : 'none' }}>
                    <div>
                      <p style={{ fontSize: '0.85rem', fontWeight: 600 }}>{r.employee?.firstName} {r.employee?.lastName}</p>
                      <p style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>{r.leaveType?.name} · {r.startDate} → {r.endDate}</p>
                    </div>
                    <span className={`badge badge--${STATUS_COLOR[r.status] ?? 'pending'}`}>{r.status}</span>
                  </div>
                ))}
              </div>

              {/* Pending timesheets */}
              <div className='card'>
                <div className='card__header'>
                  <span className='card__title'>Timesheets Awaiting Review</span>
                  <a href='/timesheet-approvals' style={{ fontSize: '0.8rem', color: 'var(--brand)' }}>View all →</a>
                </div>
                {recentTs.length === 0 ? (
                  <div className='card__body'>
                    <div className='empty-state'><i className='bi bi-clock-history'></i><p>None pending.</p></div>
                  </div>
                ) : recentTs.map((t, i) => (
                  <div key={t.id} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '0.75rem 1.25rem', borderBottom: i < recentTs.length - 1 ? '1px solid var(--border)' : 'none' }}>
                    <div>
                      <p style={{ fontSize: '0.85rem', fontWeight: 600 }}>{t.employee?.firstName} {t.employee?.lastName}</p>
                      <p style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>Week of {t.weekStartDate} · {t.totalHours ?? 0}h</p>
                    </div>
                    <span className={`badge badge--${STATUS_COLOR[t.status] ?? 'pending'}`}>{t.status}</span>
                  </div>
                ))}
              </div>
            </div>
          </>
        )}
      </div>
    </>
  );
}
