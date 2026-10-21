import { useEffect, useState, useCallback } from 'react';
import {
  BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer,
} from 'recharts';
import TopBar from '../components/TopBar';
import Spinner from '../components/Spinner';
import {
  getEmployees, getAllLeaveRequests, getAllTimesheets, getAllDocuments, getDepartmentBreakdown,
} from '../api/HrService';

const STATUS_COLOR = { APPROVED: 'approved', REJECTED: 'rejected', PENDING: 'pending', SUBMITTED: 'pending', DRAFT: 'inactive', VERIFIED: 'approved' };

// Series colours reuse the same CSS custom properties the stat cards use for
// each category, so the chart reads consistently with the rest of the dashboard.
const SERIES = [
  { key: 'pendingLeave',      name: 'Pending Leave',      color: 'var(--brand)' },
  { key: 'pendingTimesheets', name: 'Pending Timesheets', color: 'var(--amber)' },
  { key: 'pendingDocuments',  name: 'Pending Docs',       color: 'var(--red)' },
];

export default function DashboardPage() {
  const [stats, setStats]       = useState(null);
  const [recentLeave, setLeave] = useState([]);
  const [recentTs, setTs]       = useState([]);
  const [deptBreakdown, setDeptBreakdown] = useState([]);
  const [loading, setLoading]   = useState(true);

  const load = useCallback(async () => {
    const [empRes, leaveRes, tsRes, docRes, deptRes] = await Promise.allSettled([
      getEmployees(0, 1),
      getAllLeaveRequests(),
      getAllTimesheets(),
      getAllDocuments(),
      getDepartmentBreakdown(),
    ]);

    const empData  = empRes.value?.data?.data ?? empRes.value?.data;
    const leaves   = leaveRes.status === 'fulfilled' ? (leaveRes.value.data?.data ?? []) : [];
    const tsheets  = tsRes.status    === 'fulfilled' ? (tsRes.value.data?.data ?? []) : [];
    const docs     = docRes.status   === 'fulfilled' ? (docRes.value.data?.data ?? []) : [];
    const depts    = deptRes.status  === 'fulfilled' ? (deptRes.value.data?.data ?? []) : [];

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
    setDeptBreakdown(depts);
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

            {/* Pending items by department */}
            <div className='card' style={{ marginTop: '1.25rem' }}>
              <div className='card__header'>
                <span className='card__title'>Pending Items by Department</span>
              </div>
              <div className='card__body'>
                {deptBreakdown.length === 0 ? (
                  <div className='empty-state'><i className='bi bi-bar-chart'></i><p>No departments to show.</p></div>
                ) : (
                  <ResponsiveContainer width='100%' height={320}>
                    <BarChart data={deptBreakdown} margin={{ top: 8, right: 16, left: 0, bottom: 8 }}>
                      <CartesianGrid strokeDasharray='3 3' stroke='var(--border)' vertical={false} />
                      <XAxis dataKey='departmentName' tick={{ fontSize: 12, fill: 'var(--text-secondary)' }} />
                      <YAxis allowDecimals={false} tick={{ fontSize: 12, fill: 'var(--text-secondary)' }} />
                      <Tooltip cursor={{ fill: 'var(--brand-light)' }} />
                      <Legend wrapperStyle={{ fontSize: '0.8rem' }} />
                      {SERIES.map(s => (
                        <Bar key={s.key} dataKey={s.key} name={s.name} fill={s.color} radius={[4, 4, 0, 0]} maxBarSize={48} />
                      ))}
                    </BarChart>
                  </ResponsiveContainer>
                )}
              </div>
            </div>
          </>
        )}
      </div>
    </>
  );
}
