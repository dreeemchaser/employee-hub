import { useEffect, useState, useCallback } from 'react';
import TopBar from '../components/TopBar';
import Spinner from '../components/Spinner';
import { getAuditLogs } from '../api/HrService';

export default function AuditLogsPage() {
  const [data, setData]       = useState({ content: [], totalPages: 0 });
  const [page, setPage]       = useState(0);
  const [loading, setLoading] = useState(true);

  const load = useCallback(async (p = 0) => {
    setLoading(true);
    try {
      const res = await getAuditLogs(p);
      setData(res.data?.data ?? res.data);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { load(page); }, [load, page]);

  const logs = data.content ?? [];
  const totalPages = data.totalPages ?? 0;

  return (
    <>
      <TopBar title='Audit Logs' breadcrumb='HR Admin / Audit Logs' />
      <div className='page'>
        <div className='card'>
          <div className='card__header'>
            <span className='card__title'>System Audit Trail</span>
          </div>
          {loading ? <Spinner /> : (
            <div className='table-wrap'>
              <table>
                <thead>
                  <tr>
                    <th>Timestamp</th>
                    <th>Actor</th>
                    <th>Action</th>
                    <th>Entity</th>
                    <th>Entity ID</th>
                  </tr>
                </thead>
                <tbody>
                  {logs.length === 0 ? (
                    <tr><td colSpan={5} style={{ textAlign: 'center', color: 'var(--text-muted)', padding: '2rem' }}>No audit logs found.</td></tr>
                  ) : logs.map((log, i) => (
                    <tr key={log.id ?? i}>
                      <td style={{ fontFamily: 'monospace', fontSize: '0.78rem', color: 'var(--text-secondary)', whiteSpace: 'nowrap' }}>
                        {log.timestamp ? new Date(log.timestamp).toLocaleString() : '—'}
                      </td>
                      <td>{log.performedBy?.email ?? log.performedBy?.firstName ?? '—'}</td>
                      <td><span style={{ fontFamily: 'monospace', fontSize: '0.78rem' }}>{log.action}</span></td>
                      <td style={{ color: 'var(--text-secondary)' }}>{log.entityType ?? '—'}</td>
                      <td style={{ fontFamily: 'monospace', fontSize: '0.78rem', color: 'var(--text-muted)' }}>{log.entityId ?? '—'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
          {totalPages > 1 && (
            <div className='pagination'>
              <a className={page === 0 ? 'disabled' : ''} onClick={() => setPage(p => Math.max(0, p - 1))}>‹</a>
              {Array.from({ length: totalPages }, (_, i) => (
                <a key={i} className={i === page ? 'active' : ''} onClick={() => setPage(i)}>{i + 1}</a>
              ))}
              <a className={page === totalPages - 1 ? 'disabled' : ''} onClick={() => setPage(p => Math.min(totalPages - 1, p + 1))}>›</a>
            </div>
          )}
        </div>
      </div>
    </>
  );
}
