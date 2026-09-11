import { useState, useEffect, useCallback } from 'react';
import TopBar from '../components/TopBar';
import { getMe, updateMe, changePassword } from '../api/EmployeeService';

export default function ProfilePage() {
  const [tab, setTab]         = useState('profile');
  const [profile, setProfile] = useState(null);
  const [editing, setEditing] = useState(false);
  const [form, setForm]       = useState({});
  const [pwForm, setPwForm]   = useState({ currentPassword: '', newPassword: '', confirmPassword: '' });
  const [feedback, setFeedback] = useState(null);
  const [saving, setSaving]   = useState(false);

  const load = useCallback(async () => {
    try {
      const res = await getMe();
      const data = res.data?.data ?? res.data;
      setProfile(data);
      setForm({
        firstName:   data.firstName  ?? '',
        lastName:    data.lastName   ?? '',
        phone:       data.phone      ?? '',
        address:     data.address    ?? '',
        nationality: data.nationality ?? '',
        gender:      data.gender     ?? '',
      });
    } catch { /* silent */ }
  }, []);

  useEffect(() => { load(); }, [load]);

  const set   = e => setForm(f => ({ ...f, [e.target.name]: e.target.value }));
  const setPw = e => setPwForm(f => ({ ...f, [e.target.name]: e.target.value }));

  const handleSave = async e => {
    e.preventDefault();
    setSaving(true);
    setFeedback(null);
    try {
      await updateMe(form);
      await load();
      setEditing(false);
      setFeedback({ type: 'success', msg: 'Profile updated successfully.' });
    } catch (err) {
      setFeedback({ type: 'error', msg: err.response?.data?.message ?? 'Failed to update profile.' });
    } finally { setSaving(false); }
  };

  const handlePasswordChange = async e => {
    e.preventDefault();
    if (pwForm.newPassword !== pwForm.confirmPassword) {
      setFeedback({ type: 'error', msg: 'New passwords do not match.' });
      return;
    }
    setSaving(true);
    setFeedback(null);
    try {
      await changePassword({ currentPassword: pwForm.currentPassword, newPassword: pwForm.newPassword });
      setFeedback({ type: 'success', msg: 'Password changed successfully.' });
      setPwForm({ currentPassword: '', newPassword: '', confirmPassword: '' });
    } catch (err) {
      setFeedback({ type: 'error', msg: err.response?.data?.message ?? 'Failed to change password.' });
    } finally { setSaving(false); }
  };

  const ROLE_LABEL = {
    SUPER_ADMIN: 'Super Admin', HR_ADMIN: 'HR Admin',
    PAYROLL_ADMIN: 'Payroll Admin', MANAGER: 'Manager', EMPLOYEE: 'Employee',
  };

  return (
    <>
      <TopBar title='My Profile' breadcrumb='Employee Hub / Profile' />
      <div className='page'>

        {/* Header card */}
        {profile && (
          <div className='card' style={{ marginBottom: '1.5rem', padding: '1.5rem' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '1.25rem' }}>
              <div style={{
                width: 72, height: 72, borderRadius: '50%',
                background: 'var(--brand)', color: '#fff',
                display: 'flex', alignItems: 'center', justifyContent: 'center',
                fontSize: '1.8rem', fontWeight: 700, flexShrink: 0,
              }}>
                {profile.firstName?.[0]}{profile.lastName?.[0]}
              </div>
              <div>
                <h2 style={{ fontSize: '1.25rem', fontWeight: 700, marginBottom: '0.2rem' }}>
                  {profile.firstName} {profile.lastName}
                </h2>
                <p style={{ color: 'var(--text-muted)', fontSize: '0.85rem' }}>{profile.jobTitle}</p>
                <div style={{ display: 'flex', gap: '0.5rem', marginTop: '0.4rem', flexWrap: 'wrap' }}>
                  <span className='badge badge--active'>{ROLE_LABEL[profile.role] ?? profile.role}</span>
                  <span className='badge badge--pending'>{profile.employeeNumber}</span>
                  {profile.department && <span className='badge' style={{ background: 'var(--brand-light)', color: 'var(--brand)' }}>{profile.department}</span>}
                </div>
              </div>
            </div>
          </div>
        )}

        <div className='profile-tabs' style={{ marginBottom: '1.5rem' }}>
          {[['profile', 'Personal Info'], ['security', 'Security']].map(([key, label]) => (
            <button key={key} className={`profile-tab${tab === key ? ' active' : ''}`} onClick={() => { setTab(key); setFeedback(null); }}>
              {label}
            </button>
          ))}
        </div>

        {feedback && (
          <p className={`feedback feedback--${feedback.type}`} style={{ marginBottom: '1rem' }}>
            <i className={`bi ${feedback.type === 'success' ? 'bi-check-circle' : 'bi-exclamation-circle'}`}></i> {feedback.msg}
          </p>
        )}

        {/* ── Personal Info ── */}
        {tab === 'profile' && profile && (
          <div className='card' style={{ maxWidth: 680 }}>
            <div className='card__header'>
              <span className='card__title'>Personal Information</span>
              {!editing && (
                <button className='btn btn-sm' onClick={() => setEditing(true)}>
                  <i className='bi bi-pencil'></i> Edit
                </button>
              )}
            </div>
            <div className='card__body'>
              {!editing ? (
                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1.25rem' }}>
                  {[
                    { label: 'First Name',   value: profile.firstName },
                    { label: 'Last Name',    value: profile.lastName },
                    { label: 'Email',        value: profile.email },
                    { label: 'Phone',        value: profile.phone || '—' },
                    { label: 'Gender',       value: profile.gender || '—' },
                    { label: 'Nationality',  value: profile.nationality || '—' },
                    { label: 'Department',   value: profile.department || '—' },
                    { label: 'Team',         value: profile.team || '—' },
                    { label: 'Start Date',   value: profile.startDate || '—' },
                    { label: 'Employment',   value: profile.employmentType || '—' },
                  ].map(({ label, value }) => (
                    <div key={label}>
                      <p style={{ fontSize: '0.72rem', color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: '0.05em', marginBottom: '0.2rem' }}>{label}</p>
                      <p style={{ fontSize: '0.9rem', fontWeight: 500 }}>{value}</p>
                    </div>
                  ))}
                  <div style={{ gridColumn: '1 / -1' }}>
                    <p style={{ fontSize: '0.72rem', color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: '0.05em', marginBottom: '0.2rem' }}>Address</p>
                    <p style={{ fontSize: '0.9rem', fontWeight: 500 }}>{profile.address || '—'}</p>
                  </div>
                </div>
              ) : (
                <form onSubmit={handleSave}>
                  <div className='form-grid'>
                    <div className='form-group'>
                      <label className='form-label'>First Name</label>
                      <input className='form-control' name='firstName' value={form.firstName} onChange={set} required />
                    </div>
                    <div className='form-group'>
                      <label className='form-label'>Last Name</label>
                      <input className='form-control' name='lastName' value={form.lastName} onChange={set} required />
                    </div>
                    <div className='form-group'>
                      <label className='form-label'>Phone</label>
                      <input className='form-control' name='phone' value={form.phone} onChange={set} placeholder='+27 ...' />
                    </div>
                    <div className='form-group'>
                      <label className='form-label'>Gender</label>
                      <select className='form-control' name='gender' value={form.gender} onChange={set}>
                        <option value=''>— Select —</option>
                        <option>Male</option><option>Female</option><option>Non-binary</option><option>Prefer not to say</option>
                      </select>
                    </div>
                    <div className='form-group'>
                      <label className='form-label'>Nationality</label>
                      <input className='form-control' name='nationality' value={form.nationality} onChange={set} placeholder='South African' />
                    </div>
                    <div className='form-group' style={{ gridColumn: '1 / -1' }}>
                      <label className='form-label'>Address</label>
                      <textarea className='form-control' name='address' value={form.address} onChange={set} rows={2} placeholder='Street, City, Province' />
                    </div>
                  </div>
                  <div style={{ display: 'flex', gap: '0.5rem', justifyContent: 'flex-end', marginTop: '1.25rem' }}>
                    <button type='button' className='btn btn-ghost' onClick={() => { setEditing(false); setFeedback(null); }}>Cancel</button>
                    <button type='submit' className='btn' disabled={saving}>
                      <i className='bi bi-check-lg'></i> {saving ? 'Saving...' : 'Save Changes'}
                    </button>
                  </div>
                </form>
              )}
            </div>
          </div>
        )}

        {/* ── Security ── */}
        {tab === 'security' && (
          <div className='card' style={{ maxWidth: 460 }}>
            <div className='card__header'><span className='card__title'>Change Password</span></div>
            <div className='card__body'>
              <form onSubmit={handlePasswordChange}>
                <div className='form-group' style={{ marginBottom: '1rem' }}>
                  <label className='form-label'>Current Password</label>
                  <input className='form-control' type='password' name='currentPassword' value={pwForm.currentPassword} onChange={setPw} required />
                </div>
                <div className='form-group' style={{ marginBottom: '1rem' }}>
                  <label className='form-label'>New Password</label>
                  <input className='form-control' type='password' name='newPassword' value={pwForm.newPassword} onChange={setPw} required minLength={8} />
                </div>
                <div className='form-group' style={{ marginBottom: '1.5rem' }}>
                  <label className='form-label'>Confirm New Password</label>
                  <input className='form-control' type='password' name='confirmPassword' value={pwForm.confirmPassword} onChange={setPw} required minLength={8} />
                </div>
                <button type='submit' className='btn' disabled={saving} style={{ width: '100%' }}>
                  <i className='bi bi-shield-lock'></i> {saving ? 'Changing...' : 'Change Password'}
                </button>
              </form>
            </div>
          </div>
        )}
      </div>
    </>
  );
}
