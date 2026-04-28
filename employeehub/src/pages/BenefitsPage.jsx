import { useState, useEffect, useCallback } from 'react';
import TopBar from '../components/TopBar';
import { getBenefitTypes, getMyBenefits, applyForBenefit } from '../api/EmployeeService';

export default function BenefitsPage() {
  const [available, setAvailable] = useState([]);
  const [active, setActive]       = useState([]);
  const [applying, setApplying]   = useState(null);
  const [feedback, setFeedback]   = useState(null);

  const load = useCallback(async () => {
    try {
      const [typesRes, myRes] = await Promise.all([getBenefitTypes(), getMyBenefits()]);
      setAvailable(typesRes.data?.data ?? []);
      setActive(myRes.data?.data ?? []);
    } catch {
      // silently fail
    }
  }, []);

  useEffect(() => { load(); }, [load]);

  const activeIds = new Set(active.map(b => b.benefitType?.id));

  const handleApply = async (id) => {
    setApplying(id);
    setFeedback(null);
    try {
      await applyForBenefit(id);
      setFeedback({ type: 'success', msg: 'Application submitted successfully.' });
      await load();
    } catch (err) {
      setFeedback({ type: 'error', msg: err.response?.data?.message ?? 'Failed to apply.' });
    } finally {
      setApplying(null);
    }
  };

  return (
    <>
      <TopBar title='Benefits' breadcrumb='Employee Hub / Benefits' />
      <div className='page'>

        {feedback && (
          <p className={`feedback feedback--${feedback.type}`} style={{ marginBottom: '1rem' }}>
            <i className={`bi ${feedback.type === 'success' ? 'bi-check-circle' : 'bi-exclamation-circle'}`}></i> {feedback.msg}
          </p>
        )}

        {/* Active benefits */}
        <div className='card' style={{ marginBottom: '1.5rem' }}>
          <div className='card__header'><span className='card__title'>My Active Benefits</span></div>
          <div className='table-wrap'>
            <table>
              <thead><tr><th>Benefit</th><th>My Contribution</th><th>Employer Contribution</th><th>Status</th></tr></thead>
              <tbody>
                {active.length === 0 ? (
                  <tr><td colSpan={4} style={{ textAlign: 'center', color: 'var(--text-muted)', padding: '2rem' }}>No active benefits.</td></tr>
                ) : active.map(b => (
                  <tr key={b.id}>
                    <td style={{ fontWeight: 500 }}>{b.benefitType?.name}</td>
                    <td style={{ color: 'var(--text-secondary)' }}>R {b.benefitType?.employeeContribution?.toLocaleString('en-ZA')} / month</td>
                    <td style={{ color: 'var(--green)' }}>R {b.benefitType?.employerContribution?.toLocaleString('en-ZA')} / month</td>
                    <td><span className='badge badge--active'>{b.status}</span></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>

        {/* Available benefits */}
        <p style={{ fontSize: '0.8rem', fontWeight: 600, textTransform: 'uppercase', letterSpacing: '0.05em', color: 'var(--text-muted)', marginBottom: '0.75rem' }}>
          Available to Apply
        </p>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(280px, 1fr))', gap: '1rem' }}>
          {available.filter(b => !activeIds.has(b.id)).map(b => (
            <div className='card' key={b.id} style={{ padding: '1.25rem' }}>
              <p style={{ fontWeight: 600, fontSize: '0.9rem', marginBottom: '0.5rem' }}>{b.name}</p>
              <p style={{ fontSize: '0.82rem', color: 'var(--text-secondary)', marginBottom: '0.75rem', lineHeight: 1.5 }}>{b.description}</p>
              <p style={{ fontSize: '0.78rem', color: 'var(--brand)', fontWeight: 600, marginBottom: '1rem' }}>
                R {b.employeeContribution?.toLocaleString('en-ZA')} / month
              </p>
              <button
                className='btn btn-sm'
                style={{ width: '100%', justifyContent: 'center' }}
                disabled={applying === b.id}
                onClick={() => handleApply(b.id)}
              >
                {applying === b.id ? 'Applying...' : <><i className='bi bi-plus-lg'></i> Apply</>}
              </button>
            </div>
          ))}
        </div>

      </div>
    </>
  );
}
