import { useRef, useState, useEffect } from 'react';
import { saveEmployee, updateEmployeePhoto, getDepartments, getTeams } from '../api/EmployeeService';

const EMPTY = {
  firstName: '', lastName: '', email: '', phone: '',
  jobTitle: '', address: '', gender: '', password: '',
  departmentId: '', teamId: '',
  employmentType: 'FULL_TIME', employmentStatus: 'ACTIVE',
  startDate: '', role: 'EMPLOYEE',
};

const NewEmployeeModal = ({ onEmployeeSaved }) => {
  const dialogRef = useRef(null);
  const [form, setForm]               = useState(EMPTY);
  const [departments, setDepartments] = useState([]);
  const [teams, setTeams]             = useState([]);
  const [photo, setPhoto]             = useState(null);
  const [preview, setPreview]         = useState(null);
  const [saving, setSaving]           = useState(false);
  const [error, setError]             = useState(null);

  // Load departments once on mount
  useEffect(() => {
    getDepartments()
      .then(r => setDepartments(r.data?.data ?? []))
      .catch(() => {});
  }, []);

  // Reload teams whenever department changes
  useEffect(() => {
    if (!form.departmentId) { setTeams([]); return; }
    getTeams(form.departmentId)
      .then(r => setTeams(r.data?.data ?? []))
      .catch(() => {});
  }, [form.departmentId]);

  const open = () => {
    setError(null);
    setForm(EMPTY);
    setPhoto(null);
    setPreview(null);
    dialogRef.current?.showModal();
  };

  const close = () => dialogRef.current?.close();

  const set = e => {
    const { name, value } = e.target;
    setForm(prev => ({
      ...prev,
      [name]: value,
      ...(name === 'departmentId' ? { teamId: '' } : {}),
    }));
  };

  const handlePhotoChange = e => {
    const file = e.target.files[0];
    if (!file) return;
    setPhoto(file);
    setPreview(URL.createObjectURL(file));
  };

  const handleSubmit = async e => {
    e.preventDefault();
    setSaving(true);
    setError(null);
    try {
      const payload = {
        ...form,
        departmentId: Number(form.departmentId),
        teamId: Number(form.teamId),
        startDate: form.startDate || new Date().toISOString().split('T')[0],
      };
      const res = await saveEmployee(payload);
      const savedId = res.data?.data?.id;
      if (photo && savedId) {
        await updateEmployeePhoto(savedId, photo);
      }
      await onEmployeeSaved();
      close();
    } catch (err) {
      setError(err.response?.data?.message ?? 'Failed to save employee. Please try again.');
    } finally {
      setSaving(false);
    }
  };

  return (
    <>
      <button onClick={open} className='btn btn-sm'>
        <i className='bi bi-plus-lg'></i> Add Employee
      </button>

      <dialog ref={dialogRef} className='modal'>
        <div className='modal__header'>
          <h3>New Employee</h3>
          <i onClick={close} className='bi bi-x-lg' style={{ cursor: 'pointer' }}></i>
        </div>

        <div className='divider'></div>

        {error && <p className='feedback feedback--error'><i className='bi bi-exclamation-circle'></i> {error}</p>}

        <form onSubmit={handleSubmit}>
          <div className='form-grid'>

            <div className='form-group'>
              <label className='form-label'>First Name <span style={{ color: 'var(--red)' }}>*</span></label>
              <input className='form-control' type='text' name='firstName' value={form.firstName} onChange={set} required />
            </div>
            <div className='form-group'>
              <label className='form-label'>Last Name <span style={{ color: 'var(--red)' }}>*</span></label>
              <input className='form-control' type='text' name='lastName' value={form.lastName} onChange={set} required />
            </div>

            <div className='form-group'>
              <label className='form-label'>Email <span style={{ color: 'var(--red)' }}>*</span></label>
              <input className='form-control' type='email' name='email' value={form.email} onChange={set} required />
            </div>
            <div className='form-group'>
              <label className='form-label'>Password <span style={{ color: 'var(--red)' }}>*</span></label>
              <input className='form-control' type='password' name='password' value={form.password} onChange={set} required />
            </div>
            <div className='form-group'>
              <label className='form-label'>Phone</label>
              <input className='form-control' type='text' name='phone' value={form.phone} onChange={set} />
            </div>

            <div className='form-group'>
              <label className='form-label'>Job Title <span style={{ color: 'var(--red)' }}>*</span></label>
              <input className='form-control' type='text' name='jobTitle' value={form.jobTitle} onChange={set} required />
            </div>
            <div className='form-group'>
              <label className='form-label'>Start Date</label>
              <input className='form-control' type='date' name='startDate' value={form.startDate} onChange={set} />
            </div>

            <div className='form-group'>
              <label className='form-label'>Department <span style={{ color: 'var(--red)' }}>*</span></label>
              <select className='form-control' name='departmentId' value={form.departmentId} onChange={set} required>
                <option value=''>— Select department —</option>
                {departments.map(d => <option key={d.id} value={d.id}>{d.name}</option>)}
              </select>
            </div>
            <div className='form-group'>
              <label className='form-label'>Team <span style={{ color: 'var(--red)' }}>*</span></label>
              <select className='form-control' name='teamId' value={form.teamId} onChange={set} required disabled={!form.departmentId}>
                <option value=''>— Select team —</option>
                {teams.map(t => <option key={t.id} value={t.id}>{t.name}</option>)}
              </select>
            </div>

            <div className='form-group'>
              <label className='form-label'>Employment Type</label>
              <select className='form-control' name='employmentType' value={form.employmentType} onChange={set}>
                <option value='FULL_TIME'>Full Time</option>
                <option value='PART_TIME'>Part Time</option>
                <option value='CONTRACT'>Contract</option>
              </select>
            </div>
            <div className='form-group'>
              <label className='form-label'>Role</label>
              <select className='form-control' name='role' value={form.role} onChange={set}>
                <option value='EMPLOYEE'>Employee</option>
                <option value='MANAGER'>Manager</option>
                <option value='HR_ADMIN'>HR Admin</option>
                <option value='PAYROLL_ADMIN'>Payroll Admin</option>
                <option value='SUPER_ADMIN'>Super Admin</option>
              </select>
            </div>

            <div className='form-group'>
              <label className='form-label'>Photo</label>
              <input className='form-control' type='file' accept='image/*' onChange={handlePhotoChange} />
            </div>

            {preview && (
              <div className='form-group' style={{ display: 'flex', alignItems: 'center' }}>
                <img src={preview} alt='preview' style={{ width: 56, height: 56, borderRadius: '50%', objectFit: 'cover', border: '3px solid var(--brand)' }} />
              </div>
            )}

          </div>

          <div className='modal__footer'>
            <button type='button' onClick={close} className='btn btn-ghost'>Cancel</button>
            <button type='submit' className='btn' disabled={saving}>
              {saving ? 'Saving...' : 'Save Employee'}
            </button>
          </div>
        </form>
      </dialog>
    </>
  );
};

export default NewEmployeeModal;
