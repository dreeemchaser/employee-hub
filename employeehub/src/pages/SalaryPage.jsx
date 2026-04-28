import { useState, useEffect, useCallback } from 'react';
import TopBar from '../components/TopBar';
import { getMyPaySlips } from '../api/EmployeeService';

const fmt = n => `R ${Number(n).toLocaleString('en-ZA', { minimumFractionDigits: 2 })}`;

export default function SalaryPage() {
  const [tab, setTab]           = useState('overview');
  const [payslips, setPayslips] = useState([]);
  const [selected, setSelected] = useState(null);
  const [loading, setLoading]   = useState(true);

  const load = useCallback(async () => {
    try {
      const res = await getMyPaySlips();
      setPayslips(res.data?.data ?? []);
    } catch {
      // silently fail
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { load(); }, [load]);

  const current = payslips[0] ?? null;

  return (
    <>
      <TopBar title='Salary' breadcrumb='Employee Hub / Salary' />
      <div className='page'>

        <div className='profile-tabs' style={{ marginBottom: '1.5rem' }}>
          {[['overview', 'Overview'], ['payslips', 'Payslips']].map(([key, label]) => (
            <button key={key} className={`profile-tab${tab === key ? ' active' : ''}`} onClick={() => { setTab(key); setSelected(null); }}>
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
                    { label: 'Current Monthly Gross', value: fmt(current.basicSalary), icon: 'bi-cash-coin',  color: 'blue' },
                    { label: 'Current Net Pay',        value: fmt(current.netSalary),   icon: 'bi-wallet2',    color: 'green' },
                    { label: 'PAYE Tax',               value: fmt(current.paye),        icon: 'bi-receipt',    color: 'red' },
                    { label: 'UIF',                    value: fmt(current.uif),         icon: 'bi-graph-up',   color: 'amber' },
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
                      { label: 'Basic Salary', value: fmt(current.basicSalary) },
                      { label: 'PAYE Tax',     value: `- ${fmt(current.paye)}`,        red: true },
                      { label: 'UIF',          value: `- ${fmt(current.uif)}`,         red: true },
                      current.medicalAid > 0 && { label: 'Medical Aid', value: `- ${fmt(current.medicalAid)}`, red: true },
                      current.pensionFund > 0 && { label: 'Pension Fund', value: `- ${fmt(current.pensionFund)}`, red: true },
                      { label: 'Net Pay',      value: fmt(current.netSalary),          bold: true },
                    ].filter(Boolean).map(row => (
                      <div key={row.label} style={{ display: 'flex', justifyContent: 'space-between', padding: '0.55rem 0', borderBottom: '1px solid var(--border)' }}>
                        <span style={{ fontSize: '0.85rem', color: 'var(--text-secondary)' }}>{row.label}</span>
                        <span style={{ fontSize: '0.85rem', fontWeight: row.bold ? 700 : 400, color: row.red ? 'var(--red)' : row.bold ? 'var(--text-primary)' : 'var(--text-secondary)' }}>
                          {row.value}
                        </span>
                      </div>
                    ))}
                  </div>
                </div>
              </>
            ) : (
              <div className='empty-state'><i className='bi bi-cash-coin'></i><p>No salary records found.</p></div>
            )}
          </>
        )}

        {/* ── Payslips list ── */}
        {!loading && tab === 'payslips' && !selected && (
          <div className='card'>
            <div className='card__header'><span className='card__title'>Payslip History</span></div>
            <div className='table-wrap'>
              <table>
                <thead><tr><th>Month</th><th>Gross</th><th>PAYE</th><th>UIF</th><th>Net Pay</th><th></th></tr></thead>
                <tbody>
                  {payslips.length === 0 ? (
                    <tr><td colSpan={6} style={{ textAlign: 'center', color: 'var(--text-muted)', padding: '2rem' }}>No payslips yet.</td></tr>
                  ) : payslips.map(p => (
                    <tr key={p.id}>
                      <td style={{ fontWeight: 500 }}>{p.month}</td>
                      <td>{fmt(p.basicSalary)}</td>
                      <td style={{ color: 'var(--red)' }}>{fmt(p.paye)}</td>
                      <td style={{ color: 'var(--red)' }}>{fmt(p.uif)}</td>
                      <td style={{ fontWeight: 600, color: 'var(--green)' }}>{fmt(p.netSalary)}</td>
                      <td>
                        <button className='btn btn-ghost btn-sm' onClick={() => setSelected(p)}>
                          <i className='bi bi-eye'></i> View
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}

        {/* ── Payslip detail ── */}
        {!loading && tab === 'payslips' && selected && (
          <div className='card' style={{ maxWidth: 480 }}>
            <div className='card__header'>
              <span className='card__title'>Payslip — {selected.month}</span>
              <button className='btn btn-ghost btn-sm' onClick={() => setSelected(null)}><i className='bi bi-arrow-left'></i> Back</button>
            </div>
            <div className='card__body'>
              {[
                { label: 'Basic Salary', value: fmt(selected.basicSalary) },
                { label: 'PAYE Tax',     value: `- ${fmt(selected.paye)}`,     red: true },
                { label: 'UIF',          value: `- ${fmt(selected.uif)}`,      red: true },
                selected.medicalAid > 0 && { label: 'Medical Aid', value: `- ${fmt(selected.medicalAid)}`, red: true },
                selected.pensionFund > 0 && { label: 'Pension Fund', value: `- ${fmt(selected.pensionFund)}`, red: true },
                { label: 'Net Pay',      value: fmt(selected.netSalary),       bold: true },
              ].filter(Boolean).map(row => (
                <div key={row.label} style={{ display: 'flex', justifyContent: 'space-between', padding: '0.55rem 0', borderBottom: '1px solid var(--border)' }}>
                  <span style={{ fontSize: '0.85rem', color: 'var(--text-secondary)' }}>{row.label}</span>
                  <span style={{ fontSize: '0.85rem', fontWeight: row.bold ? 700 : 400, color: row.red ? 'var(--red)' : row.bold ? 'var(--text-primary)' : 'var(--text-secondary)' }}>
                    {row.value}
                  </span>
                </div>
              ))}
            </div>
          </div>
        )}

      </div>
    </>
  );
}
