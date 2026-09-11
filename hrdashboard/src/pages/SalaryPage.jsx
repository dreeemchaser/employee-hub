import { useEffect, useState, useCallback } from 'react';
import TopBar from '../components/TopBar';
import Spinner from '../components/Spinner';
import { getAllIncreaseRequests, approveIncreaseRequest, rejectIncreaseRequest } from '../api/HrService';

const fmt = n => `R ${Number(n).toLocaleString('en-ZA', { minimumFractionDigits: 2 })}`;
const STATUS_COLOR = { APPROVED: 'approved', REJECTED: 'rejected', PENDING: 'pending' };

export default function SalaryPage() {
  const [requests, setRequests]   = useState([]);
  const [filtered, setFiltered]   = useState([]);
  const [statusFilter, setFilter] = useState('ALL');
  const [loading, setLoading]     = useState(true);
  const [acting, setActing]       = useState(null);

  // Reject modal
  const [rejectModal, setRejectModal] = useState(null);
  const [rejectReason, setRejectReason] = useState('');
  const [rejecting, setRejecting] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const res = await getAllIncreaseRequests();
      const data = res.data?.data ?? [];
      setRequests(data);
      applyFilter(data, statusFilter);
    } finally { setLoading(false); }
  }, []);

  const applyFilter = (data, f) => setFiltered(f === 'ALL' ? data : data.filter(r => r.status === f));

  useEffect(() => { load(); }, [load]);
  useEffect(() => { applyFilter(requests, statusFilter); }, [statusFilter, requests]);

  const handleApprove = async id => {
    setActing(id);
    try {
      await approveIncreaseRequest(id);
      setRequests(prev => prev.map(r => r.id === id ? { ...r, status: 'APPROVED' } : r));
    } finally { setActing(null); }
  };

  const openReject = id => { setRejectModal({ id }); setRejectReason(''); };
  const closeReject = () => { setRejectModal(null); setRejectReason(''); };

  const handleReject = async () => {
    if (!rejectReason.trim()) return;
    setRejecting(true);
    try {
      await rejectIncreaseRequest(rejectModal.id, rejectReason);
      setRequests(prev => prev.map(r => r.id === rejectModal.id ? { ...r, status: 'REJECTED', rejectionReason: rejectReason } : r));
      closeReject();
    } finally { setRejecting(false); }
  };

  const pending = requests.filter(r => r.status === 'PENDING').length;

  return (
    <>
      <TopBar title='Salary Management' breadcrumb='HR Admin / Salary' />
      <div className='page'>

        {/* Reject modal */}
        {rejectModal && (
          <div style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.45)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000 }}>
            <div className='card' style={{ width: 440, padding: '1.5rem' }}>
              <h3 style={{ fontSize: '1rem', fontWeight: 700, marginBottom: '1rem' }}>Reject Salary Increase Request</h3>
              <div className='form-group' style={{ marginBottom: '1.25rem' }}>
                <label className='form-label'>Reason <span style={{ color: 'var(--red)' }}>*</span></label>
                <textarea className='form-control' rows={3} placeholder='Provide a reason...'
                  value={rejectReason} onChange={e => setRejectReason(e.target.value)} autoFocus />
              </div>
              <div style={{ display: 'flex', gap: '0.5rem', justifyContent: 'flex-end' }}>
                <button className='btn btn-ghost' onClick={closeReject}>Cancel</button>
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
              Salary Increase Requests
              {pending > 0 && <span style={{ marginLeft: '0.5rem', background: 'var(--amber)', color: '#fff', borderRadius: 'var(--radius-full)', fontSize: '0.7rem', padding: '1px 8px', fontWeight: 700 }}>{pending} pending</span>}
            </span>
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
                    <th>Employee</th>
                    <th>Current</th>
                    <th>Proposed</th>
                    <th>Increase</th>
                    <th>Justification</th>
                    <th>Requested By</th>
                    <th>Date</th>
                    <th>Status</th>
                    <th></th>
                  </tr>
                </thead>
                <tbody>
                  {filtered.length === 0 ? (
                    <tr><td colSpan={9} style={{ textAlign: 'center', color: 'var(--text-muted)', padding: '2rem' }}>No requests found.</td></tr>
                  ) : filtered.map(r => (
                    <tr key={r.id}>
                      <td style={{ fontWeight: 500 }}>{r.employee?.firstName} {r.employee?.lastName}</td>
                      <td>{fmt(r.currentSalary)}</td>
                      <td style={{ fontWeight: 600 }}>{fmt(r.proposedSalary)}</td>
                      <td style={{ color: 'var(--green)', fontWeight: 600 }}>+{r.increasePercentage}%</td>
                      <td style={{ maxWidth: 200, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', color: 'var(--text-secondary)' }} title={r.justification}>
                        {r.justification}
                      </td>
                      <td>{r.requestedBy?.firstName} {r.requestedBy?.lastName}</td>
                      <td style={{ color: 'var(--text-muted)', fontSize: '0.8rem' }}>
                        {r.createdAt ? new Date(r.createdAt).toLocaleDateString('en-ZA') : '—'}
                      </td>
                      <td><span className={`badge badge--${STATUS_COLOR[r.status] ?? 'pending'}`}>{r.status}</span></td>
                      <td>
                        {r.status === 'PENDING' && (
                          <div style={{ display: 'flex', gap: '0.4rem' }}>
                            <button className='btn btn-success btn-sm' disabled={acting === r.id} onClick={() => handleApprove(r.id)}>
                              <i className='bi bi-check-lg'></i>
                            </button>
                            <button className='btn btn-danger btn-sm' disabled={acting === r.id} onClick={() => openReject(r.id)}>
                              <i className='bi bi-x-lg'></i>
                            </button>
                          </div>
                        )}
                        {r.status === 'REJECTED' && r.rejectionReason && (
                          <span style={{ fontSize: '0.75rem', color: 'var(--red)' }} title={r.rejectionReason}>
                            <i className='bi bi-info-circle'></i> {r.rejectionReason.slice(0, 25)}…
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
