import { useEffect, useState, useCallback, useMemo } from 'react';
import TopBar from '../components/TopBar';
import Spinner from '../components/Spinner';
import { getAllDocuments, verifyDocument } from '../api/HrService';

const STATUS_COLOR = { VERIFIED: 'verified', PENDING: 'pending', REJECTED: 'rejected' };

export default function DocumentsPage() {
  const [documents, setDocuments] = useState([]);
  const [loading, setLoading]     = useState(true);
  const [acting, setActing]       = useState(null);
  const [expiringOnly, setExpiringOnly] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const res = await getAllDocuments();
      setDocuments(res.data?.data ?? []);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { load(); }, [load]);

  // Client-side filter over the data GET /documents already returns -- no
  // separate backend endpoint needed since expiryDate/expired (Jackson strips
  // the "is" prefix from boolean getter isExpired when serializing) are
  // already present on every DocumentResponse.
  const visibleDocuments = useMemo(() => {
    if (!expiringOnly) return documents;
    const in30Days = new Date();
    in30Days.setDate(in30Days.getDate() + 30);
    return documents.filter(d => d.expiryDate && (d.expired || new Date(d.expiryDate) <= in30Days));
  }, [documents, expiringOnly]);

  const handleVerify = async (id) => {
    setActing(id);
    try {
      await verifyDocument(id);
      setDocuments(prev => prev.map(d => d.id === id ? { ...d, status: 'VERIFIED' } : d));
    } finally {
      setActing(null);
    }
  };

  return (
    <>
      <TopBar title='Documents' breadcrumb='HR Admin / Documents' />
      <div className='page'>
        <div className='card'>
          <div className='card__header'>
            <span className='card__title'>Employee Documents</span>
            <label style={{ display: 'flex', alignItems: 'center', gap: '0.4rem', fontSize: '0.85rem', cursor: 'pointer' }}>
              <input type='checkbox' checked={expiringOnly} onChange={e => setExpiringOnly(e.target.checked)} />
              Show expiring/expired only
            </label>
          </div>
          {loading ? <Spinner /> : (
            <div className='table-wrap'>
              <table>
                <thead>
                  <tr>
                    <th>Employee</th>
                    <th>Document Type</th>
                    <th>File</th>
                    <th>Uploaded</th>
                    <th>Expires</th>
                    <th>Status</th>
                    <th></th>
                  </tr>
                </thead>
                <tbody>
                  {visibleDocuments.length === 0 ? (
                    <tr><td colSpan={7} style={{ textAlign: 'center', color: 'var(--text-muted)', padding: '2rem' }}>
                      {expiringOnly ? 'No expiring or expired documents.' : 'No documents found.'}
                    </td></tr>
                  ) : visibleDocuments.map(d => (
                    <tr key={d.id}>
                      <td>{d.employee?.firstName} {d.employee?.lastName}</td>
                      <td>{d.documentType}</td>
                      <td style={{ color: 'var(--text-secondary)', fontFamily: 'monospace', fontSize: '0.78rem' }}>{d.fileName}</td>
                      <td style={{ color: 'var(--text-secondary)' }}>{d.createdAt ? new Date(d.createdAt).toLocaleDateString() : '—'}</td>
                      <td>
                        {d.expiryDate ? (
                          <span style={{ display: 'flex', alignItems: 'center', gap: '0.4rem' }}>
                            <span style={{ color: d.expired ? 'var(--red)' : 'var(--text-secondary)' }}>{d.expiryDate}</span>
                            {d.expired && <span className='badge badge--rejected'>Expired</span>}
                          </span>
                        ) : (
                          <span style={{ color: 'var(--text-muted)' }}>—</span>
                        )}
                      </td>
                      <td><span className={`badge badge--${STATUS_COLOR[d.status] ?? 'pending'}`}>{d.status}</span></td>
                      <td>
                        {d.status === 'PENDING' && (
                          <button
                            className='btn btn-success btn-sm'
                            disabled={acting === d.id}
                            onClick={() => handleVerify(d.id)}
                          >
                            <i className='bi bi-patch-check'></i> Verify
                          </button>
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
