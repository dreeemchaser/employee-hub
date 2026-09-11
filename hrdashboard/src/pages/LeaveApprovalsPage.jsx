import { useEffect, useState, useCallback } from 'react';
import TopBar from '../components/TopBar';
import Spinner from '../components/Spinner';
import { getAllLeaveRequests, approveLeave, rejectLeave } from '../api/HrService';

const STATUS_COLOR = { APPROVED: 'approved', REJECTED: 'rejected', PENDING: 'pending', CANCELLED: 'inactive' };

export default function LeaveApprovalsPage() {
  const [requests, setRequests]   = useState([]);
  const [filtered, setFiltered]   = useState([]);
  const [statusFilter, setFilter] = useState('ALL');
  const [loading, setLoading]     = useState(true);
  const [acting, setActing]       = useState(null);

  // Reject modal state
  const [rejectModal, setRejectModal] = useState(null); // { id }
  const [rejectReason, setRejectReason] = useState('');
  const [rejecting, setRejecting] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const res = await getAllLeaveRequests();
      const data = res.data?.data ?? [];
      setRequests(data);
      applyFilter(data, statusFilter);
    } finally { setLoading(false); }
  }, []);

  const applyFilter = (data, f) => {
    setFiltered(f === 'ALL' ? data : data.filter(r => r.status === f));
  };

  useEffect(() => { load(); }, [load]);
  useEffect(() => { applyFilter(requests, statusFilter); }, [statusFilter, requests]);

  const handleApprove = async id => {
    setActing(id);
    try {
      await approveLeave(id);
      setRequests(prev => prev.map(r => r.id === id ? { ...r, status: 'APPROVED' } : r));
    } finally { setActing(null); }
  };

  const openRejectModal = id => { setRejectModal({ id }); setRejectReason(''); };
  const closeRejectModal = () => { setRejectModal(null); setRejectReason(''); };

  const handleReject = async () => {
    if (!rejectReason.trim()) return;
    setRejecting(true);
    try {
      await rejectLeave(rejectModal.id, rejectReason);
      setRequests(prev => prev.map(r => r.id === rejectModal.id ? { ...r, status: 'REJECTED' } : r));
      closeRejectModal();
    } finally { setRejecting(false); }
  };

  const pending = requests.filter(r => r.status === 'PENDING').length;

  return (
    <>
      <TopBar title='Leave Approvals' breadcrumb='HR Admin / Leave Approvals' />
      <div className='page'>

        {/* Reject modal */}
        {rejectModal && (
          <div style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.45)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000 }}>
            <div className='card' style={{ width: 440, padding: '1.5rem' }}>
              <h3 style={{ fontSize: '1rem', fontWeight: 700, marginBottom: '1rem' }}>Reject Leave Request</h3>
              <div className='form-group' style={{ marginBottom: '1.25rem' }}>
                <label className='form-label'>Reason for rejection <span style={{ color: 'var(--red)' }}>*</span></label>
                <textarea className='form-control' rows={3} placeholder='Provide a reason...'
                  value={rejectReason} onChange={e => setRejectReason(e.target.value)} autoFocus />
              </div>
              <div style={{ display: 'flex', gap: '0.5rem', justifyContent: 'flex-end' }}>
                <button className='btn btn-ghost' onClick={closeRejectModal}>Cancel</button>
                <button className='btn btn-danger' onClick={handleReject} disabled={rejecting || !rejectReason.trim()}>
                  {rejecting ? 'Rejecting...' : 'Reject'}
                </button>
              </div>
            </div>
          </div>
        )}

        <div className='card'>
          <div className='card__header'>
            <span className='card__title'>
              Leave Requests
              {pending > 0 && <span style={{ marginLeft: '0.5rem', background: 'var(--amber)', color: '#fff', borderRadius: 'var(--radius-full)', fontSize: '0.7rem', padding: '1px 8px', fontWeight: 700 }}>{pending} pending</span>}
            </span>
            {/* Status filter */}
            <div style={{ display: 'flex', gap: '0.35rem' }}>
              {['ALL','PENDING','APPROVED','REJECTED'].map(s => (
                <button key={s} className={`btn btn-sm ${statusFilter === s ? '' : 'btn-ghost'}`}
                  style={{ fontSize: '0.75rem' }} onClick={() => setFilter(s)}>
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
                    <th>Employee</th><th>Type</th><th>From</th><th>To</th>
                    <th>Days</th><th>Reason</th><th>Status</th><th></th>
                  </tr>
                </thead>
                <tbody>
                  {filtered.length === 0 ? (
                    <tr><td colSpan={8} style={{ textAlign: 'center', color: 'var(--text-muted)', padding: '2rem' }}>No requests found.</td></tr>
                  ) : filtered.map(r => (
                    <tr key={r.id}>
                      <td style={{ fontWeight: 500 }}>{r.employee?.firstName} {r.employee?.lastName}</td>
                      <td>{r.leaveType?.name}</td>
                      <td>{r.startDate}</td>
                      <td>{r.endDate}</td>
                      <td>{r.totalDays}</td>
                      <td style={{ color: 'var(--text-secondary)', maxWidth: 180, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }} title={r.reason}>{r.reason || '—'}</td>
                      <td><span className={`badge badge--${STATUS_COLOR[r.status] ?? 'pending'}`}>{r.status}</span></td>
                      <td>
                        {r.status === 'PENDING' && (
                          <div style={{ display: 'flex', gap: '0.4rem' }}>
                            <button className='btn btn-success btn-sm' disabled={acting === r.id} onClick={() => handleApprove(r.id)}>
                              <i className='bi bi-check-lg'></i> Approve
                            </button>
                            <button className='btn btn-danger btn-sm' disabled={acting === r.id} onClick={() => openRejectModal(r.id)}>
                              <i className='bi bi-x-lg'></i> Reject
                            </button>
                          </div>
                        )}
                        {r.status === 'REJECTED' && r.rejectionReason && (
                          <span style={{ fontSize: '0.75rem', color: 'var(--red)' }} title={r.rejectionReason}>
                            <i className='bi bi-info-circle'></i> {r.rejectionReason.slice(0, 30)}{r.rejectionReason.length > 30 ? '…' : ''}
                          </span>
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
