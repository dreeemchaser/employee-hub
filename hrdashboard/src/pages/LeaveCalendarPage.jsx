import { useCallback, useEffect, useState } from 'react';
import TopBar from '../components/TopBar';
import { getLeaveCalendar } from '../api/HrService';

const MONTHS = ['January','February','March','April','May','June','July','August','September','October','November','December'];

export default function LeaveCalendarPage() {
  const now = new Date();
  const [year, setYear] = useState(now.getFullYear());
  const [month, setMonth] = useState(now.getMonth() + 1);
  const [leaveTypeId, setLeaveTypeId] = useState('');
  const [employeeId, setEmployeeId] = useState('');
  const [departmentId, setDepartmentId] = useState('');
  const [teamId, setTeamId] = useState('');
  const [entries, setEntries] = useState([]);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const load = useCallback(async () => {
    setLoading(true); setError('');
    try {
      const res = await getLeaveCalendar(year, month, { leaveTypeId, employeeId, departmentId, teamId });
      setEntries(res.data?.data ?? []);
    } catch { setError('Unable to load the leave calendar.'); }
    finally { setLoading(false); }
  }, [year, month, leaveTypeId, employeeId, departmentId, teamId]);

  useEffect(() => { load(); }, [load]);
  const days = new Date(year, month, 0).getDate();
  const first = new Date(year, month - 1, 1).getDay() || 7;
  const byDay = {};
  entries.forEach(entry => {
    for (let date = new Date(entry.startDate); date <= new Date(entry.endDate); date.setDate(date.getDate() + 1)) {
      if (date.getFullYear() === year && date.getMonth() + 1 === month) {
        (byDay[date.getDate()] ??= []).push(entry);
      }
    }
  });
  const cells = Array.from({ length: first - 1 }, () => null).concat(
    Array.from({ length: days }, (_, index) => index + 1)
  );
  const shift = delta => {
    const date = new Date(year, month - 1 + delta, 1);
    setYear(date.getFullYear()); setMonth(date.getMonth() + 1);
  };

  return <>
    <TopBar title='Shared Leave Calendar' breadcrumb='HR Admin / Calendar' />
    <div className='page'>
      <div className='card'>
        <div className='card__header'>
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
            <button className='btn btn-ghost btn-sm' onClick={() => shift(-1)}>‹</button>
            <strong>{MONTHS[month - 1]} {year}</strong>
            <button className='btn btn-ghost btn-sm' onClick={() => shift(1)}>›</button>
          </div>
          <span style={{ color: 'var(--text-muted)', fontSize: '0.8rem' }}>{entries.length} approved leave entries</span>
        </div>
        <div className='card__body'>
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: '0.6rem', marginBottom: '1rem' }}>
            {[['Leave type', leaveTypeId, setLeaveTypeId], ['Employee ID', employeeId, setEmployeeId],
              ['Department ID', departmentId, setDepartmentId], ['Team ID', teamId, setTeamId]].map(([label, value, setter]) => (
              <label className='form-group' style={{ margin: 0, minWidth: 150 }} key={label}>
                <span className='form-label'>{label}</span>
                <input className='form-control' value={value} onChange={e => setter(e.target.value)} placeholder='All' />
              </label>
            ))}
          </div>
          {loading && <p style={{ color: 'var(--text-muted)' }}>Loading calendar…</p>}
          {error && <p style={{ color: 'var(--red)' }}>{error}</p>}
          {!loading && !error && entries.length === 0 && <p style={{ color: 'var(--text-muted)' }}>No approved leave matches these filters.</p>}
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(7,1fr)', gap: 4 }}>
            {['Mon','Tue','Wed','Thu','Fri','Sat','Sun'].map(day => <strong key={day} style={{ textAlign: 'center', fontSize: '0.72rem', color: 'var(--text-muted)' }}>{day}</strong>)}
            {cells.map((day, index) => <div key={index} style={{ minHeight: 72, padding: 4, border: day ? '1px solid var(--border)' : 'none', borderRadius: 4 }}>
              {day && <><span style={{ fontSize: '0.72rem' }}>{day}</span>{(byDay[day] ?? []).slice(0, 3).map(entry =>
                <div key={entry.id} title={`${entry.employee?.firstName ?? ''} ${entry.employee?.lastName ?? ''}`} style={{ marginTop: 2, padding: '2px 4px', background: 'var(--brand)', color: '#fff', borderRadius: 3, fontSize: '0.64rem', overflow: 'hidden', whiteSpace: 'nowrap', textOverflow: 'ellipsis' }}>
                  {entry.employee?.firstName} {entry.employee?.lastName}
                </div>
              )}</>}
            </div>)}
          </div>
        </div>
      </div>
    </div>
  </>;
}
