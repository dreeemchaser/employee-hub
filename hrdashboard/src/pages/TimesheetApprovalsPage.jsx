import { useEffect, useState, useCallback } from 'react';
import TopBar from '../components/TopBar';
import Spinner from '../components/Spinner';
import { getAllTimesheets, approveTimesheet, rejectTimesheet } from '../api/HrService';

const STATUS_COLOR = {
  APPROVED: 'approved',
  REJECTED: 'rejected',
  SUBMITTED: 'pending',
  DRAFT: 'inactive',
};

export default function TimesheetApprovalsPage() {
  const [timesheets, setTimesheets]   = useState([]);
  const [filtered, setFiltered]       = useState([]);
  const [statusFilter, setFilter]     = useState('ALL');
  const [loading, setLoading]         = useState(true);
  const [acting, setActing]           = useState(null);
  const [expanded, setExpanded]       = useState(null);

  // Reject modal state
  const [rejectModal, setRejectModal]   = useState(null); // { id }
  const [rejectReason, setRejectReason] = useState('');
  const [rejecting, setRejecting]       = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const res = await getAllTimesheets();
      const data = res.data?.data ?? [];
      setTimesheets(data);
      applyFilter(data, statusFilter);
    } finally {
      setLoading(false);
    }
  }, []);

  const applyFilter = (data, f) => {
    setFiltered(f === 'ALL' ? data : data.filter(t => t.status === f));
  };

  useEffect(() => { load(); }, [load]);
  useEffect(() => { applyFilter(timesheets, statusFilter); }, [statusFilter, timesheets]);

  const handleApprove = async id => {
    setActing(id);
    try {
      await approveTimesheet(id);
      setTimesheets(prev => prev.map(t => t.id === id ? { ...t, status: 'APPROVED' } : t));
    } finally {
      setActing(null);
    }
  };

  const openRejectModal  = id => { setRejectModal({ id }); setRejectReason(''); };
  const closeRejectModal = ()  => { setRejectModal(null);  setRejectReason(''); };

  const handleReject = async () => {
    if (!rejectReason.trim()) return;
    setRejecting(true);
    try {
      await rejectTimesheet(rejectModal.id, rejectReason);
      setTimesheets(prev =>
        prev.map(t => t.id === rejectModal.id ? { ...t, status: 'REJECTED' } : t)
      );
      closeRejectModal();
    } finally {
      setRejecting(false);
    }
  };

  const toggleExpand = id => setExpanded(prev => (prev === id ? null : id));

  const pending = timesheets.filter(t => t.status === 'SUBMITTED').length;

  return (
    <>
      <TopBar title='Timesheet Approvals' breadcrumb='HR Admin / Timesheet Approvals' />
      <div className='page'>

        {/* ── Reject modal ── */}
        {rejectModal && (
          <div style={{
            position: 'fixed', inset: 0,
            background: 'rgba(0,0,0,0.45)',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            zIndex: 1000,
          }}>
            <div className='card' style={{ width: 440, padding: '1.5rem' }}>
              <h3 style={{ fontSize: '1rem', fontWeight: 700, marginBottom: '1rem' }}>
                Reject Timesheet
              </h3>
              <div className='form-group' style={{ marginBottom: '1.25rem' }}>
                <label className='form-label'>
                  Reason for rejection <span style={{ color: 'var(--red)' }}>*</span>
                </label>
                <textarea
                  className='form-control'
                  rows={3}
                  placeholder='Provide a reason...'
                  value={rejectReason}
                  onChange={e => setRejectReason(e.target.value)}
                  autoFocus
                />
              </div>
              <div style={{ display: 'flex', gap: '0.5rem', justifyContent: 'flex-end' }}>
                <button className='btn btn-ghost' onClick={closeRejectModal}>Cancel</button>
                <button
                  className='btn btn-danger'
                  onClick={handleReject}
                  disabled={rejecting || !rejectReason.trim()}
                >
                  {rejecting ? 'Rejecting...' : 'Reject'}
                </button>
              </div>
            </div>
          </div>
        )}

        <div className='card'>
          <div className='card__header'>
            <span className='card__title'>
              Timesheets
              {pending > 0 && (
                <span style={{
                  marginLeft: '0.5rem',
                  background: 'var(--amber)', color: '#fff',
                  borderRadius: 'var(--radius-full)',
                  fontSize: '0.7rem', padding: '1px 8px', fontWeight: 700,
                }}>
                  {pending} pending
                </span>
              )}
            </span>

            {/* Status filter pills */}
            <div style={{ display: 'flex', gap: '0.35rem' }}>
              {['ALL', 'SUBMITTED', 'APPROVED', 'REJECTED', 'DRAFT'].map(s => (
                <button
                  key={s}
                  className={`btn btn-sm ${statusFilter === s ? '' : 'btn-ghost'}`}
                  style={{ fontSize: '0.75rem' }}
                  onClick={() => setFilter(s)}
                >
                  {s}
                </button>
              ))}
            </div>
          </div>

          {loading ? <Spinner /> : (
            <div className='table-wrap'>
              <table>
                <thead>
                  <tr>
                    <th style={{ width: 32 }}></th>
                    <th>Employee</th>
                    <th>Week Starting</th>
                    <th>Week Ending</th>
                    <th>Total Hours</th>
                    <th>Status</th>
                    <th></th>
                  </tr>
                </thead>
                <tbody>
                  {filtered.length === 0 ? (
                    <tr>
                      <td colSpan={7} style={{ textAlign: 'center', color: 'var(--text-muted)', padding: '2rem' }}>
                        No timesheets found.
                      </td>
                    </tr>
                  ) : filtered.map(t => (
                    <>
                      {/* ── Summary row ── */}
                      <tr key={t.id} style={{ cursor: 'pointer' }} onClick={() => toggleExpand(t.id)}>
                        <td>
                          <i className={`bi bi-chevron-${expanded === t.id ? 'up' : 'down'}`}
                            style={{ color: 'var(--text-muted)', fontSize: '0.8rem' }} />
                        </td>
                        <td style={{ fontWeight: 500 }}>
                          {t.employee?.firstName} {t.employee?.lastName}
                        </td>
                        <td>{t.weekStartDate}</td>
                        <td>{t.weekEndDate}</td>
                        <td>{t.totalHours ?? '—'}h</td>
                        <td>
                          <span className={`badge badge--${STATUS_COLOR[t.status] ?? 'pending'}`}>
                            {t.status}
                          </span>
                        </td>
                        <td onClick={e => e.stopPropagation()}>
                          {t.status === 'SUBMITTED' && (
                            <div style={{ display: 'flex', gap: '0.4rem' }}>
                              <button
                                className='btn btn-success btn-sm'
                                disabled={acting === t.id}
                                onClick={() => handleApprove(t.id)}
                              >
                                <i className='bi bi-check-lg'></i> Approve
                              </button>
                              <button
                                className='btn btn-danger btn-sm'
                                disabled={acting === t.id}
                                onClick={() => openRejectModal(t.id)}
                              >
                                <i className='bi bi-x-lg'></i> Reject
                              </button>
                            </div>
                          )}
                          {t.status === 'REJECTED' && t.rejectionReason && (
                            <span
                              style={{ fontSize: '0.75rem', color: 'var(--red)' }}
                              title={t.rejectionReason}
                            >
                              <i className='bi bi-info-circle'></i>{' '}
                              {t.rejectionReason.slice(0, 30)}
                              {t.rejectionReason.length > 30 ? '…' : ''}
                            </span>
                          )}
                        </td>
                      </tr>

                      {/* ── Expanded entries row ── */}
                      {expanded === t.id && (
                        <tr key={`${t.id}-detail`}>
                          <td colSpan={7} style={{ padding: 0, background: 'var(--bg)' }}>
                            <div style={{ padding: '0.75rem 2.5rem 1rem' }}>
                              {(!t.entries || t.entries.length === 0) ? (
                                <p style={{ color: 'var(--text-muted)', fontSize: '0.85rem', padding: '0.5rem 0' }}>
                                  No entries recorded.
                                </p>
                              ) : (
                                <>
                                  <p style={{
                                    fontSize: '0.72rem', fontWeight: 600,
                                    textTransform: 'uppercase', letterSpacing: '0.05em',
                                    color: 'var(--text-muted)', marginBottom: '0.5rem',
                                  }}>
                                    Daily breakdown
                                  </p>
                                  <table style={{ width: '100%', fontSize: '0.83rem' }}>
                                    <thead>
                                      <tr style={{ color: 'var(--text-muted)' }}>
                                        <th style={{ padding: '4px 12px 4px 0', fontWeight: 600, textAlign: 'left' }}>Date</th>
                                        <th style={{ padding: '4px 12px 4px 0', fontWeight: 600, textAlign: 'left' }}>Hours</th>
                                        <th style={{ padding: '4px 12px 4px 0', fontWeight: 600, textAlign: 'left' }}>Project / Task</th>
                                        <th style={{ padding: '4px 0', fontWeight: 600, textAlign: 'left' }}>Description</th>
                                      </tr>
                                    </thead>
                                    <tbody>
                                      {t.entries.map(e => (
                                        <tr key={e.id} style={{ borderTop: '1px solid var(--border)' }}>
                                          <td style={{ padding: '5px 12px 5px 0' }}>{e.date}</td>
                                          <td style={{ padding: '5px 12px 5px 0', fontWeight: 500 }}>{e.hoursWorked}h</td>
                                          <td style={{ padding: '5px 12px 5px 0', color: 'var(--brand)' }}>{e.projectOrTask ?? '—'}</td>
                                          <td style={{ padding: '5px 0', color: 'var(--text-secondary)' }}>{e.description ?? '—'}</td>
                                        </tr>
                                      ))}
                                    </tbody>
                                    <tfoot>
                                      <tr style={{ borderTop: '2px solid var(--border)' }}>
                                        <td style={{ padding: '5px 12px 5px 0', fontWeight: 600 }}>Total</td>
                                        <td style={{ padding: '5px 12px 5px 0', fontWeight: 700, color: 'var(--brand)' }}>
                                          {t.entries.reduce((s, e) => s + (parseFloat(e.hoursWorked) || 0), 0)}h
                                        </td>
                                        <td colSpan={2} />
                                      </tr>
                                    </tfoot>
                                  </table>
                                </>
                              )}
                            </div>
                          </td>
                        </tr>
                      )}
                    </>
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
