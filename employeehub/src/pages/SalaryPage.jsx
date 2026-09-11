import { useState, useEffect, useCallback } from 'react';
import TopBar from '../components/TopBar';
import { getMyPaySlips, getMyIncreaseRequests, submitIncreaseRequest } from '../api/EmployeeService';

const fmt = n => `R ${Number(n).toLocaleString('en-ZA', { minimumFractionDigits: 2 })}`;
const INC_COLOR = { APPROVED: 'approved', REJECTED: 'rejected', PENDING: 'pending' };

export default function SalaryPage() {
  const [tab, setTab]           = useState('overview');
  const [payslips, setPayslips] = useState([]);
  const [selected, setSelected] = useState(null);
  const [requests, setRequests] = useState([]);
  const [form, setForm]         = useState({ proposedSalary: '', justification: '' });
  const [loading, setLoading]   = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [feedback, setFeedback] = useState(null);

  const load = useCallback(async () => {
    try {
      const [psRes, irRes] = await Promise.allSettled([getMyPaySlips(), getMyIncreaseRequests()]);
      if (psRes.status === 'fulfilled') setPayslips(psRes.value.data?.data ?? []);
      if (irRes.status === 'fulfilled') setRequests(irRes.value.data?.data ?? []);
    } catch { /* silent */ } finally { setLoading(false); }
  }, []);

  useEffect(() => { load(); }, [load]);

  const current = payslips[0] ?? null;

  const handleSubmitRequest = async e => {
    e.preventDefault();
    if (!form.proposedSalary || isNaN(parseFloat(form.proposedSalary))) {
      setFeedback({ type: 'error', msg: 'Please enter a valid proposed salary.' });
      return;
    }
    setSubmitting(true);
    setFeedback(null);
    try {
      await submitIncreaseRequest({
        proposedSalary: parseFloat(form.proposedSalary),
        justification: form.justification,
      });
      setFeedback({ type: 'success', msg: 'Salary increase request submitted.' });
      setForm({ proposedSalary: '', justification: '' });
      await load();
    } catch (err) {
      setFeedback({ type: 'error', msg: err.response?.data?.message ?? 'Failed to submit request.' });
    } finally { setSubmitting(false); }
  };

  return (
    <>
      <TopBar title='Salary' breadcrumb='Employee Hub / Salary' />
      <div className='page'>

        <div className='profile-tabs' style={{ marginBottom: '1.5rem' }}>
          {[['overview','Overview'],['payslips','Payslips'],['increase','Salary Increase']].map(([key, label]) => (
            <button key={key} className={`profile-tab${tab === key ? ' active' : ''}`}
              onClick={() => { setTab(key); setSelected(null); setFeedback(null); }}>
              {label}
            </button>
          ))}
        </div>

        {loading && <p style={{ color: 'var(--text-muted)', fontSize: '0.85rem' }}>Loading...</p>}

        {/* ── Overview ── */}
        {!loading && tab === 'overview' && (
          <>
            {current ? (
              <>
                <div className='stat-grid' style={{ marginBottom: '1.5rem' }}>
                  {[
                    { label: 'Monthly Gross', value: fmt(current.basicSalary), icon: 'bi-cash-coin',  color: 'blue' },
                    { label: 'Net Pay',       value: fmt(current.netSalary),   icon: 'bi-wallet2',    color: 'green' },
                    { label: 'PAYE Tax',      value: fmt(current.paye),        icon: 'bi-receipt',    color: 'red' },
                    { label: 'UIF',           value: fmt(current.uif),         icon: 'bi-graph-up',   color: 'amber' },
                  ].map(s => (
                    <div className='stat-card' key={s.label}>
                      <div className={`stat-card__icon stat-card__icon--${s.color}`}><i className={`bi ${s.icon}`}></i></div>
                      <div>
                        <div className='stat-card__value' style={{ fontSize: '1.1rem' }}>{s.value}</div>
                        <div className='stat-card__label'>{s.label}</div>
                      </div>
                    </div>
                  ))}
                </div>
                <div className='card' style={{ maxWidth: 480 }}>
                  <div className='card__header'><span className='card__title'>Latest Payslip — {current.month}</span></div>
                  <div className='card__body'>
                    {[
                      { label: 'Basic Salary',  value: fmt(current.basicSalary) },
                      { label: 'PAYE Tax',      value: `- ${fmt(current.paye)}`, red: true },
                      { label: 'UIF',           value: `- ${fmt(current.uif)}`, red: true },
                      current.medicalAid > 0 && { label: 'Medical Aid', value: `- ${fmt(current.medicalAid)}`, red: true },
                      current.pensionFund > 0 && { label: 'Pension Fund', value: `- ${fmt(current.pensionFund)}`, red: true },
                      { label: 'Net Pay',       value: fmt(current.netSalary), bold: true },
                    ].filter(Boolean).map(row => (
                      <div key={row.label} style={{ display: 'flex', justifyContent: 'space-between', padding: '0.55rem 0', borderBottom: '1px solid var(--border)' }}>
                        <span style={{ fontSize: '0.85rem', color: 'var(--text-secondary)' }}>{row.label}</span>
                        <span style={{ fontSize: '0.85rem', fontWeight: row.bold ? 700 : 400, color: row.red ? 'var(--red)' : row.bold ? 'var(--text-primary)' : undefined }}>{row.value}</span>
                      </div>
                    ))}
                  </div>
                </div>
              </>
            ) : (
              <div className='empty-state'><i className='bi bi-cash-coin'></i><p>No salary records yet.</p></div>
            )}
          </>
        )}

        {/* ── Payslips ── */}
        {!loading && tab === 'payslips' && !selected && (
          <div className='card'>
            <div className='card__header'><span className='card__title'>Payslip History</span></div>
            <div className='table-wrap'>
              <table>
                <thead><tr><th>Month</th><th>Gross</th><th>PAYE</th><th>UIF</th><th>Net Pay</th><th></th></tr></thead>
                <tbody>
                  {payslips.length === 0
                    ? <tr><td colSpan={6} style={{ textAlign: 'center', color: 'var(--text-muted)', padding: '2rem' }}>No payslips yet.</td></tr>
                    : payslips.map(p => (
                      <tr key={p.id}>
                        <td style={{ fontWeight: 500 }}>{p.month}</td>
                        <td>{fmt(p.basicSalary)}</td>
                        <td style={{ color: 'var(--red)' }}>{fmt(p.paye)}</td>
                        <td style={{ color: 'var(--red)' }}>{fmt(p.uif)}</td>
                        <td style={{ fontWeight: 600, color: 'var(--green)' }}>{fmt(p.netSalary)}</td>
                        <td><button className='btn btn-ghost btn-sm' onClick={() => setSelected(p)}><i className='bi bi-eye'></i> View</button></td>
                      </tr>
                    ))}
                </tbody>
              </table>
            </div>
          </div>
        )}

        {!loading && tab === 'payslips' && selected && (
          <div className='card' style={{ maxWidth: 480 }}>
            <div className='card__header'>
              <span className='card__title'>Payslip — {selected.month}</span>
              <button className='btn btn-ghost btn-sm' onClick={() => setSelected(null)}><i className='bi bi-arrow-left'></i> Back</button>
            </div>
            <div className='card__body'>
              {[
                { label: 'Basic Salary', value: fmt(selected.basicSalary) },
                { label: 'PAYE Tax',     value: `- ${fmt(selected.paye)}`, red: true },
                { label: 'UIF',          value: `- ${fmt(selected.uif)}`, red: true },
                selected.medicalAid > 0 && { label: 'Medical Aid', value: `- ${fmt(selected.medicalAid)}`, red: true },
                selected.pensionFund > 0 && { label: 'Pension Fund', value: `- ${fmt(selected.pensionFund)}`, red: true },
                { label: 'Net Pay',      value: fmt(selected.netSalary), bold: true },
              ].filter(Boolean).map(row => (
                <div key={row.label} style={{ display: 'flex', justifyContent: 'space-between', padding: '0.55rem 0', borderBottom: '1px solid var(--border)' }}>
                  <span style={{ fontSize: '0.85rem', color: 'var(--text-secondary)' }}>{row.label}</span>
                  <span style={{ fontSize: '0.85rem', fontWeight: row.bold ? 700 : 400, color: row.red ? 'var(--red)' : row.bold ? 'var(--text-primary)' : undefined }}>{row.value}</span>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* ── Salary Increase ── */}
        {!loading && tab === 'increase' && (
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1.25rem', alignItems: 'start' }}>
            {/* Request form */}
            <div className='card'>
              <div className='card__header'><span className='card__title'>Request Salary Increase</span></div>
              <div className='card__body'>
                {feedback && (
                  <p className={`feedback feedback--${feedback.type}`} style={{ marginBottom: '1rem' }}>
                    <i className={`bi ${feedback.type === 'success' ? 'bi-check-circle' : 'bi-exclamation-circle'}`}></i> {feedback.msg}
                  </p>
                )}
                {current && (
                  <p style={{ fontSize: '0.82rem', color: 'var(--text-muted)', marginBottom: '1rem' }}>
                    Current gross salary: <strong style={{ color: 'var(--text-primary)' }}>{fmt(current.basicSalary)}</strong> / month
                  </p>
                )}
                <form onSubmit={handleSubmitRequest}>
                  <div className='form-group' style={{ marginBottom: '1rem' }}>
                    <label className='form-label'>Proposed Monthly Salary (R)</label>
                    <input className='form-control' type='number' min='0' step='100'
                      placeholder='e.g. 35000'
                      value={form.proposedSalary}
                      onChange={e => setForm(f => ({ ...f, proposedSalary: e.target.value }))}
                      required />
                  </div>
                  <div className='form-group' style={{ marginBottom: '1.5rem' }}>
                    <label className='form-label'>Justification</label>
                    <textarea className='form-control' rows={4}
                      placeholder='Explain why you believe a salary increase is warranted...'
                      value={form.justification}
                      onChange={e => setForm(f => ({ ...f, justification: e.target.value }))}
                      required />
                  </div>
                  <button type='submit' className='btn' style={{ width: '100%' }} disabled={submitting}>
                    <i className='bi bi-send'></i> {submitting ? 'Submitting...' : 'Submit Request'}
                  </button>
                </form>
              </div>
            </div>

            {/* Request history */}
            <div className='card'>
              <div className='card__header'><span className='card__title'>Request History</span></div>
              {requests.length === 0 ? (
                <div className='card__body'>
                  <div className='empty-state'><i className='bi bi-inbox'></i><p>No requests yet.</p></div>
                </div>
              ) : (
                <div>
                  {requests.map((r, i) => (
                    <div key={r.id} style={{ padding: '0.9rem 1.25rem', borderBottom: i < requests.length - 1 ? '1px solid var(--border)' : 'none' }}>
                      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '0.3rem' }}>
                        <span style={{ fontWeight: 600, fontSize: '0.88rem' }}>{fmt(r.proposedSalary)}</span>
                        <span className={`badge badge--${INC_COLOR[r.status] ?? 'pending'}`}>{r.status}</span>
                      </div>
                      <p style={{ fontSize: '0.78rem', color: 'var(--text-muted)' }}>
                        +{r.increasePercentage}% &nbsp;·&nbsp; {r.createdAt ? new Date(r.createdAt).toLocaleDateString('en-ZA') : ''}
                      </p>
                      {r.rejectionReason && (
                        <p style={{ fontSize: '0.78rem', color: 'var(--red)', marginTop: '0.3rem' }}>{r.rejectionReason}</p>
                      )}
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>
        )}

      </div>
    </>
  );
}
