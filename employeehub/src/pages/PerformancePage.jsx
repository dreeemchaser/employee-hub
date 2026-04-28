import { useState, useEffect, useCallback } from 'react';
import TopBar from '../components/TopBar';
import { getMyGoals, getMyReviews } from '../api/EmployeeService';

const RATING_LABELS = { 1: 'Poor', 2: 'Below Average', 3: 'Meets Expectations', 4: 'Exceeds Expectations', 5: 'Outstanding' };
const STATUS_COLORS  = { COMPLETED: 'active', IN_PROGRESS: 'pending', NOT_STARTED: 'inactive', MISSED: 'inactive' };
const STATUS_LABELS  = { COMPLETED: 'Completed', IN_PROGRESS: 'In Progress', NOT_STARTED: 'Not Started', MISSED: 'Missed' };

export default function PerformancePage() {
  const [tab, setTab]       = useState('goals');
  const [goals, setGoals]   = useState([]);
  const [reviews, setReviews] = useState([]);
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    try {
      const [goalsRes, reviewsRes] = await Promise.all([getMyGoals(), getMyReviews()]);
      setGoals(goalsRes.data?.data ?? []);
      setReviews(reviewsRes.data?.data ?? []);
    } catch {
      // silently fail
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { load(); }, [load]);

  return (
    <>
      <TopBar title='Performance & KPIs' breadcrumb='Employee Hub / Performance' />
      <div className='page'>

        <div className='profile-tabs' style={{ marginBottom: '1.5rem' }}>
          {[['goals', 'My Goals'], ['reviews', 'Reviews']].map(([key, label]) => (
            <button key={key} className={`profile-tab${tab === key ? ' active' : ''}`} onClick={() => setTab(key)}>
              {label}
            </button>
          ))}
        </div>

        {loading && <p style={{ color: 'var(--text-muted)', fontSize: '0.85rem' }}>Loading...</p>}

        {/* ── Goals ── */}
        {!loading && tab === 'goals' && (
          <div className='card'>
            <div className='card__header'><span className='card__title'>Performance Goals</span></div>
            <div className='table-wrap'>
              <table>
                <thead><tr><th>Goal</th><th>Cycle</th><th>Due Date</th><th>Status</th></tr></thead>
                <tbody>
                  {goals.length === 0 ? (
                    <tr><td colSpan={4} style={{ textAlign: 'center', color: 'var(--text-muted)', padding: '2rem' }}>No goals assigned yet.</td></tr>
                  ) : goals.map(g => (
                    <tr key={g.id}>
                      <td style={{ fontWeight: 500 }}>{g.title}</td>
                      <td style={{ color: 'var(--text-muted)' }}>{g.cycle?.name ?? g.cycleId}</td>
                      <td style={{ color: 'var(--text-muted)' }}>{g.dueDate}</td>
                      <td><span className={`badge badge--${STATUS_COLORS[g.status] ?? 'pending'}`}>{STATUS_LABELS[g.status] ?? g.status}</span></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}

        {/* ── Reviews ── */}
        {!loading && tab === 'reviews' && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
            {reviews.length === 0 ? (
              <div className='empty-state'><i className='bi bi-graph-up-arrow'></i><p>No reviews yet.</p></div>
            ) : reviews.map(r => (
              <div className='card' key={r.id}>
                <div className='card__header'>
                  <div>
                    <span className='card__title'>{r.cycle?.name ?? 'Review'}</span>
                    <p style={{ fontSize: '0.75rem', color: 'var(--text-muted)', marginTop: 2 }}>
                      Reviewed by {r.reviewer ? `${r.reviewer.firstName} ${r.reviewer.lastName}` : '—'} · {r.reviewDate}
                    </p>
                  </div>
                  <span className='badge badge--active'>{r.status}</span>
                </div>
                <div className='card__body'>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '1rem' }}>
                    <div style={{ display: 'flex', gap: 2 }}>
                      {[1,2,3,4,5].map(s => (
                        <i key={s} className={`bi ${s <= r.rating ? 'bi-star-fill' : 'bi-star'}`}
                          style={{ color: s <= r.rating ? '#f59e0b' : 'var(--border)', fontSize: '1.1rem' }}></i>
                      ))}
                    </div>
                    <span style={{ fontSize: '0.83rem', fontWeight: 600, color: 'var(--text-primary)' }}>{RATING_LABELS[r.rating]}</span>
                  </div>
                  <div className='form-grid'>
                    <div>
                      <p className='form-label' style={{ marginBottom: '0.4rem' }}>Strengths</p>
                      <p style={{ fontSize: '0.85rem', color: 'var(--text-secondary)', lineHeight: 1.6 }}>{r.strengths}</p>
                    </div>
                    <div>
                      <p className='form-label' style={{ marginBottom: '0.4rem' }}>Areas for Improvement</p>
                      <p style={{ fontSize: '0.85rem', color: 'var(--text-secondary)', lineHeight: 1.6 }}>{r.areasForImprovement}</p>
                    </div>
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}

      </div>
    </>
  );
}
