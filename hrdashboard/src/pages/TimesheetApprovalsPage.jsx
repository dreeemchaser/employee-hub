import { useEffect, useState, useCallback } from 'react';
import TopBar from '../components/TopBar';
import Spinner from '../components/Spinner';
import { getAllTimesheets, approveTimesheet, rejectTimesheet } from '../api/HrService';

const STATUS_COLOR = { APPROVED: 'approved', REJECTED: 'rejected', SUBMITTED: 'submitted', DRAFT: 'pending' };

export default function TimesheetApprovalsPage() {
  const [timesheets, setTimesheets] = useState([]);
  const [loading, setLoading]       = useState(true);
  const [acting, setActing]         = useState(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const res = await getAllTimesheets();
      setTimesheets(res.data?.data ?? []);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { load(); }, [load]);

  const handle = async (id, action, newStatus) => {
    setActing(id);
    try {
      await action(id);
      setTimesheets(prev => prev.map(t => t.id === id ? { ...t, status: newStatus } : t));
    } finally {
      setActing(null);
    }
  };

  return (
    <>
      <TopBar title='Timesheet Approvals' breadcrumb='HR Admin / Timesheet Approvals' />
      <div className='page'>
        <div className='card'>
          <div className='card__header'>
            <span className='card__title'>Timesheets</span>
          </div>
          {loading ? <Spinner /> : (
            <div className='table-wrap'>
              <table>
                <thead>
                  <tr>
                    <th>Employee</th>
                    <th>Week Starting</th>
                    <th>Week Ending</th>
                    <th>Total Hours</th>
                    <th>Status</th>
                    <th></th>
                  </tr>
                </thead>
                <tbody>
                  {timesheets.length === 0 ? (
                    <tr><td colSpan={6} style={{ textAlign: 'center', color: 'var(--text-muted)', padding: '2rem' }}>No timesheets found.</td></tr>
                  ) : timesheets.map(t => (
                    <tr key={t.id}>
                      <td>{t.employee?.firstName} {t.employee?.lastName}</td>
                      <td>{t.weekStartDate}</td>
                      <td>{t.weekEndDate}</td>
                      <td>{t.totalHours ?? '—'}</td>
                      <td><span className={`badge badge--${STATUS_COLOR[t.status] ?? 'pending'}`}>{t.status}</span></td>
                      <td>
                        {t.status === 'SUBMITTED' && (
                          <div style={{ display: 'flex', gap: '0.4rem' }}>
                            <button
                              className='btn btn-success btn-sm'
                              disabled={acting === t.id}
                              onClick={() => handle(t.id, approveTimesheet, 'APPROVED')}
                            >
                              <i className='bi bi-check-lg'></i> Approve
                            </button>
                            <button
                              className='btn btn-danger btn-sm'
                              disabled={acting === t.id}
                              onClick={() => handle(t.id, rejectTimesheet, 'REJECTED')}
                            >
                              <i className='bi bi-x-lg'></i> Reject
                            </button>
                          </div>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </div>
    </>
  );
}
