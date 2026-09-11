import { useState, useEffect, useCallback } from 'react';
import TopBar from '../components/TopBar';
import {
  getMyTimesheets, createTimesheet, addTimesheetEntry,
  submitTimesheet, deleteTimesheetEntry,
} from '../api/EmployeeService';

const STATUS_COLOR = { APPROVED: 'approved', REJECTED: 'rejected', PENDING: 'pending', DRAFT: 'inactive', SUBMITTED: 'pending' };
const emptyEntry = () => ({ date: '', hoursWorked: '', description: '', projectOrTask: '' });

export default function TimesheetsPage() {
  const [tab, setTab]               = useState('log');
  const [timesheets, setTimesheets] = useState([]);
  const [expanded, setExpanded]     = useState(null);
  const [entries, setEntries]       = useState([emptyEntry()]);
  const [weekStart, setWeekStart]   = useState(getMonday());
  const [weekEnd, setWeekEnd]       = useState(getSunday());
  const [submitting, setSubmitting] = useState(false);
  const [feedback, setFeedback]     = useState(null);

  const totalHours = entries.reduce((s, e) => s + (parseFloat(e.hoursWorked) || 0), 0);

  const load = useCallback(async () => {
    try {
      const res = await getMyTimesheets();
      setTimesheets(res.data?.data ?? []);
    } catch { /* silent */ }
  }, []);

  useEffect(() => { load(); }, [load]);

  const setEntry = (i, field, val) => {
    const updated = [...entries];
    updated[i] = { ...updated[i], [field]: val };
    setEntries(updated);
  };

  const addRow    = () => setEntries(e => [...e, emptyEntry()]);
  const removeRow = i  => setEntries(e => e.filter((_, idx) => idx !== i));

  const handleSubmit = async e => {
    e.preventDefault();
    setSubmitting(true);
    setFeedback(null);
    const filled = entries.filter(e => parseFloat(e.hoursWorked) > 0 && e.date);
    if (filled.length === 0) {
      setFeedback({ type: 'error', msg: 'Add at least one entry with a date and hours.' });
      setSubmitting(false);
      return;
    }
    try {
      const tsRes = await createTimesheet({ weekStartDate: weekStart, weekEndDate: weekEnd });
      const tsId  = tsRes.data?.data?.id;
      for (const entry of filled) {
        await addTimesheetEntry(tsId, {
          date: entry.date,
          hoursWorked: parseFloat(entry.hoursWorked),
          description: entry.description,
          projectOrTask: entry.projectOrTask,
        });
      }
      await submitTimesheet(tsId);
      setFeedback({ type: 'success', msg: 'Timesheet submitted for approval.' });
      setEntries([emptyEntry()]);
      await load();
      setTimeout(() => setTab('history'), 1200);
    } catch (err) {
      setFeedback({ type: 'error', msg: err.response?.data?.message ?? 'Failed to submit timesheet.' });
    } finally { setSubmitting(false); }
  };

  const handleDeleteEntry = async (tsId, entryId) => {
    try {
      const res = await deleteTimesheetEntry(tsId, entryId);
      // Update the timesheet in state with updated entries
      setTimesheets(prev => prev.map(t => t.id === tsId ? (res.data?.data ?? t) : t));
    } catch (err) {
      alert(err.response?.data?.message ?? 'Failed to delete entry.');
    }
  };

  return (
    <>
      <TopBar title='Timesheets' breadcrumb='Employee Hub / Timesheets' />
      <div className='page'>

        <div className='profile-tabs' style={{ marginBottom: '1.5rem' }}>
          {[['log', 'Log Hours'], ['history', 'History']].map(([key, label]) => (
            <button key={key} className={`profile-tab${tab === key ? ' active' : ''}`} onClick={() => setTab(key)}>
              {label}
            </button>
          ))}
        </div>

        {/* ── Log Hours ── */}
        {tab === 'log' && (
          <div className='card' style={{ maxWidth: 760 }}>
            <div className='card__header'>
              <span className='card__title'>New Timesheet</span>
            </div>
            <div className='card__body'>
              {feedback && (
                <p className={`feedback feedback--${feedback.type}`} style={{ marginBottom: '1rem' }}>
                  <i className={`bi ${feedback.type === 'success' ? 'bi-check-circle' : 'bi-exclamation-circle'}`}></i> {feedback.msg}
                </p>
              )}
              {/* Week range */}
              <div className='form-grid' style={{ marginBottom: '1.25rem' }}>
                <div className='form-group'>
                  <label className='form-label'>Week Start</label>
                  <input className='form-control' type='date' value={weekStart} onChange={e => setWeekStart(e.target.value)} />
                </div>
                <div className='form-group'>
                  <label className='form-label'>Week End</label>
                  <input className='form-control' type='date' value={weekEnd} onChange={e => setWeekEnd(e.target.value)} />
                </div>
              </div>
              <form onSubmit={handleSubmit}>
                <div className='table-wrap' style={{ marginBottom: '0.75rem' }}>
                  <table>
                    <thead>
                      <tr>
                        <th>Date</th>
                        <th style={{ width: 80 }}>Hours</th>
                        <th>Project / Task</th>
                        <th>Description</th>
                        <th style={{ width: 40 }}></th>
                      </tr>
                    </thead>
                    <tbody>
                      {entries.map((entry, i) => (
                        <tr key={i}>
                          <td><input className='form-control' type='date' value={entry.date} onChange={e => setEntry(i, 'date', e.target.value)} style={{ height: 34 }} /></td>
                          <td><input className='form-control' type='number' min='0' max='24' step='0.5' placeholder='0' value={entry.hoursWorked} onChange={e => setEntry(i, 'hoursWorked', e.target.value)} style={{ height: 34, textAlign: 'center' }} /></td>
                          <td><input className='form-control' type='text' placeholder='PROJ-123' value={entry.projectOrTask} onChange={e => setEntry(i, 'projectOrTask', e.target.value)} style={{ height: 34 }} /></td>
                          <td><input className='form-control' type='text' placeholder='What did you work on?' value={entry.description} onChange={e => setEntry(i, 'description', e.target.value)} style={{ height: 34 }} /></td>
                          <td>
                            {entries.length > 1 && (
                              <button type='button' className='btn btn-ghost btn-sm' style={{ color: 'var(--red)' }} onClick={() => removeRow(i)}>
                                <i className='bi bi-trash'></i>
                              </button>
                            )}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '1rem' }}>
                    <button type='button' className='btn btn-ghost btn-sm' onClick={addRow}>
                      <i className='bi bi-plus-lg'></i> Add Row
                    </button>
                    <span style={{ fontSize: '0.85rem', fontWeight: 600, color: totalHours >= 40 ? 'var(--green)' : 'var(--brand)' }}>
                      Total: {totalHours}h
                    </span>
                  </div>
                  <div style={{ display: 'flex', gap: '0.5rem' }}>
                    <button type='button' className='btn btn-ghost' onClick={() => setEntries([emptyEntry()])}>Clear</button>
                    <button type='submit' className='btn' disabled={submitting || totalHours === 0}>
                      <i className='bi bi-send'></i> {submitting ? 'Submitting...' : 'Submit for Approval'}
                    </button>
                  </div>
                </div>
              </form>
            </div>
          </div>
        )}

        {/* ── History ── */}
        {tab === 'history' && (
          <div className='card'>
            <div className='card__header'>
              <span className='card__title'>Timesheet History</span>
              <button className='btn btn-sm' onClick={() => setTab('log')}><i className='bi bi-plus-lg'></i> New</button>
            </div>
            <div className='table-wrap'>
              <table>
                <thead><tr><th>Week Starting</th><th>Week Ending</th><th>Total Hours</th><th>Status</th><th></th></tr></thead>
                <tbody>
                  {timesheets.length === 0
                    ? <tr><td colSpan={5} style={{ textAlign: 'center', color: 'var(--text-muted)', padding: '2rem' }}>No timesheets yet.</td></tr>
                    : timesheets.map(t => (
                      <>
                        <tr key={t.id}>
                          <td style={{ fontWeight: 500 }}>{t.weekStartDate}</td>
                          <td>{t.weekEndDate}</td>
                          <td>{t.totalHours ?? 0}h</td>
                          <td><span className={`badge badge--${STATUS_COLOR[t.status] ?? 'pending'}`}>{t.status}</span></td>
                          <td>
                            <button className='btn btn-ghost btn-sm' onClick={() => setExpanded(expanded === t.id ? null : t.id)}>
                              <i className={`bi bi-chevron-${expanded === t.id ? 'up' : 'down'}`}></i>
                            </button>
                          </td>
                        </tr>
                        {expanded === t.id && t.entries?.length > 0 && (
                          <tr key={`${t.id}-entries`}>
                            <td colSpan={5} style={{ padding: '0 1rem 1rem', background: 'var(--bg)' }}>
                              <table style={{ width: '100%', fontSize: '0.82rem' }}>
                                <thead><tr style={{ color: 'var(--text-muted)' }}><th>Date</th><th>Hours</th><th>Project</th><th>Description</th>{t.status === 'DRAFT' && <th></th>}</tr></thead>
                                <tbody>
                                  {t.entries.map(e => (
                                    <tr key={e.id}>
                                      <td>{e.date}</td>
                                      <td>{e.hoursWorked}h</td>
                                      <td>{e.projectOrTask ?? '—'}</td>
                                      <td>{e.description ?? '—'}</td>
                                      {t.status === 'DRAFT' && (
                                        <td>
                                          <button className='btn btn-ghost btn-sm' style={{ color: 'var(--red)' }} onClick={() => handleDeleteEntry(t.id, e.id)}>
                                            <i className='bi bi-trash'></i>
                                          </button>
                                        </td>
                                      )}
                                    </tr>
                                  ))}
                                </tbody>
                              </table>
                            </td>
                          </tr>
                        )}
                      </>
                    ))}
                </tbody>
              </table>
            </div>
          </div>
        )}
      </div>
    </>
  );
}

function getMonday() {
  const d = new Date();
  const day = d.getDay();
  const diff = d.getDate() - day + (day === 0 ? -6 : 1);
  d.setDate(diff);
  return d.toISOString().split('T')[0];
}

function getSunday() {
  const d = new Date();
  const day = d.getDay();
  const diff = d.getDate() - day + (day === 0 ? 0 : 7);
  d.setDate(diff);
  return d.toISOString().split('T')[0];
}
