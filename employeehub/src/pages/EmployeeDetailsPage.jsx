import { useEffect, useState, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import {
  getEmployee, updateEmployee, updateEmployeePhoto, deleteEmployee,
  getDepartments, getTeams,
} from '../api/EmployeeService';
import { isHrOrAdmin } from '../api/AuthService';
import Spinner from '../components/Spinner';
import TopBar from '../components/TopBar';

const API_URL = process.env.REACT_APP_API_URL || 'http://localhost:8080';
const FALLBACK = 'https://ui-avatars.com/api/?background=1a3a5c&color=fff&name=';

const TABS = ['Personal', 'Contact', 'Employment'];

const Field = ({ label, name, type = 'text', value, onChange, options, disabled }) => (
  <div className='form-group'>
    <label className='form-label'>{label}</label>
    {options ? (
      <select className='form-control' name={name} value={value || ''} onChange={onChange} disabled={disabled}>
        <option value=''>— Select —</option>
        {options.map(o => (
          <option key={o.value ?? o} value={o.value ?? o}>{o.label ?? o}</option>
        ))}
      </select>
    ) : (
      <input className='form-control' type={type} name={name} value={value || ''} onChange={onChange} disabled={disabled} />
    )}
  </div>
);

const EmployeeDetailsPage = () => {
  const { id } = useParams();
  const navigate = useNavigate();

  const [employee, setEmployee]     = useState(null);
  const [form, setForm]             = useState({});
  const [departments, setDepartments] = useState([]);
  const [teams, setTeams]           = useState([]);
  const [preview, setPreview]       = useState(null);
  const [photo, setPhoto]           = useState(null);
  const [saving, setSaving]         = useState(false);
  const [error, setError]           = useState(null);
  const [success, setSuccess]       = useState(null);
  const [confirmDelete, setConfirmDelete] = useState(false);
  const [activeTab, setActiveTab]   = useState('Personal');

  const load = useCallback(async () => {
    try {
      const res = await getEmployee(id);
      const emp = res.data?.data ?? res.data;
      setEmployee(emp);
      setForm({
        firstName:        emp.firstName ?? '',
        lastName:         emp.lastName ?? '',
        email:            emp.email ?? '',
        phone:            emp.phone ?? '',
        dateOfBirth:      emp.dateOfBirth ?? '',
        gender:           emp.gender ?? '',
        nationality:      emp.nationality ?? '',
        idNumber:         emp.idNumber ?? '',
        address:          emp.address ?? '',
        jobTitle:         emp.jobTitle ?? '',
        employmentType:   emp.employmentType ?? '',
        employmentStatus: emp.employmentStatus ?? '',
        startDate:        emp.startDate ?? '',
        endDate:          emp.endDate ?? '',
        departmentId:     emp.department?.id ?? '',
        teamId:           emp.team?.id ?? '',
        managerId:        emp.manager?.id ?? '',
        role:             emp.role ?? '',
      });
    } catch {
      setError('Failed to load employee.');
    }
  }, [id]);

  useEffect(() => { load(); }, [load]);

  useEffect(() => {
    getDepartments()
      .then(r => setDepartments(r.data?.data ?? []))
      .catch(() => {});
  }, []);

  useEffect(() => {
    if (!form.departmentId) { setTeams([]); return; }
    getTeams(form.departmentId)
      .then(r => setTeams(r.data?.data ?? []))
      .catch(() => {});
  }, [form.departmentId]);

  if (!employee) return <Spinner />;

  const fullName = `${employee.firstName ?? ''} ${employee.lastName ?? ''}`.trim();
  const photoSrc = preview
    ? preview
    : employee.profilePhoto
      ? `${API_URL}/employees/photo/${employee.profilePhoto}`
      : `${FALLBACK}${encodeURIComponent(fullName)}`;

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

  const handleSave = async () => {
    setSaving(true);
    setError(null);
    setSuccess(null);
    try {
      if (photo) {
        await updateEmployeePhoto(id, photo);
        setPreview(null);
        setPhoto(null);
      }
      const payload = {
        ...form,
        departmentId: Number(form.departmentId),
        teamId: Number(form.teamId),
        managerId: form.managerId || null,
      };
      await updateEmployee(id, payload);
      setSuccess('Changes saved successfully.');
      await load();
    } catch (err) {
      setError(err.response?.data?.message ?? 'Failed to save. Please try again.');
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async () => {
    try {
      await deleteEmployee(id);
      navigate('/employees');
    } catch {
      setError('Failed to delete employee.');
      setConfirmDelete(false);
    }
  };

  const isActive = employee.employmentStatus?.toLowerCase() === 'active';
  const canDelete = isHrOrAdmin();

  const deptOptions = departments.map(d => ({ value: d.id, label: d.name }));
  const teamOptions = teams.map(t => ({ value: t.id, label: t.name }));

  return (
    <>
      <TopBar title={fullName} breadcrumb='Employee Hub / Employees / Profile' />
      <div className='page'>
        <div className='profile-layout'>

          {/* ── Left Panel ── */}
          <div className='profile-card'>
            <label className='profile-card__photo-wrap' htmlFor='photo-upload'>
              <img src={photoSrc} alt={fullName} />
              <span className='photo-overlay'><i className='bi bi-camera'></i></span>
            </label>
            <input id='photo-upload' type='file' accept='image/*' onChange={handlePhotoChange} style={{ display: 'none' }} />

            <div style={{ textAlign: 'center' }}>
              <p className='profile-card__name'>{fullName}</p>
              {employee.jobTitle && <p className='profile-card__title'>{employee.jobTitle}</p>}
            </div>

            <span className={`badge badge--${isActive ? 'active' : 'inactive'}`}>
              <i className={`bi ${isActive ? 'bi-check-circle' : 'bi-x-circle'}`}></i>
              {employee.employmentStatus}
            </span>

            {employee.employeeNumber && (
              <p style={{ fontSize: '0.75rem', color: 'hsla(0,0%,100%,0.5)', marginTop: '-0.5rem' }}>
                {employee.employeeNumber}
              </p>
            )}

            <div className='profile-card__meta'>
              {employee.email && (
                <div className='profile-card__meta-item'>
                  <i className='bi bi-envelope'></i>
                  <span>{employee.email}</span>
                </div>
              )}
              {employee.phone && (
                <div className='profile-card__meta-item'>
                  <i className='bi bi-telephone'></i>
                  <span>{employee.phone}</span>
                </div>
              )}
              {employee.department?.name && (
                <div className='profile-card__meta-item'>
                  <i className='bi bi-building'></i>
                  <span>{employee.department.name}</span>
                </div>
              )}
              {employee.startDate && (
                <div className='profile-card__meta-item'>
                  <i className='bi bi-calendar3'></i>
                  <span>Since {employee.startDate}</span>
                </div>
              )}
            </div>

            <button onClick={() => navigate('/employees')} className='btn btn-ghost profile-card__back'>
              <i className='bi bi-arrow-left'></i> Back to Employees
            </button>
          </div>

          {/* ── Right Panel ── */}
          <div className='profile-form-card'>
            {error   && <p className='feedback feedback--error'><i className='bi bi-exclamation-circle'></i> {error}</p>}
            {success && <p className='feedback feedback--success'><i className='bi bi-check-circle'></i> {success}</p>}

            <div className='profile-tabs'>
              {TABS.map(tab => (
                <button
                  key={tab}
                  className={`profile-tab${activeTab === tab ? ' active' : ''}`}
                  onClick={() => setActiveTab(tab)}
                >
                  {tab}
                </button>
              ))}
            </div>

            <div className='profile-tab-body'>

              {activeTab === 'Personal' && (
                <div className='form-grid'>
                  <Field label='First Name'    name='firstName'   value={form.firstName}   onChange={set} />
                  <Field label='Last Name'     name='lastName'    value={form.lastName}    onChange={set} />
                  <Field label='Date of Birth' name='dateOfBirth' value={form.dateOfBirth} onChange={set} type='date' />
                  <Field label='Gender'        name='gender'      value={form.gender}      onChange={set}
                    options={['Male', 'Female', 'Non-binary', 'Prefer not to say']} />
                  <Field label='Nationality'   name='nationality' value={form.nationality} onChange={set} />
                  <Field label='SA ID Number'  name='idNumber'    value={form.idNumber}    onChange={set} />
                </div>
              )}

              {activeTab === 'Contact' && (
                <div className='form-grid'>
                  <Field label='Email'   name='email'   value={form.email}   onChange={set} type='email' />
                  <Field label='Phone'   name='phone'   value={form.phone}   onChange={set} />
                  <Field label='Address' name='address' value={form.address} onChange={set} />
                </div>
              )}

              {activeTab === 'Employment' && (
                <div className='form-grid'>
                  <Field label='Job Title'       name='jobTitle'         value={form.jobTitle}         onChange={set} />
                  <Field label='Employee Number' name='employeeNumber'   value={employee.employeeNumber} onChange={() => {}} disabled />
                  <Field label='Department'      name='departmentId'     value={form.departmentId}     onChange={set} options={deptOptions} />
                  <Field label='Team'            name='teamId'           value={form.teamId}           onChange={set} options={teamOptions} disabled={!form.departmentId} />
                  <Field label='Employment Type' name='employmentType'   value={form.employmentType}   onChange={set}
                    options={[
                      { value: 'FULL_TIME', label: 'Full Time' },
                      { value: 'PART_TIME', label: 'Part Time' },
                      { value: 'CONTRACT',  label: 'Contract' },
                    ]} />
                  <Field label='Status'          name='employmentStatus' value={form.employmentStatus} onChange={set}
                    options={[
                      { value: 'ACTIVE',      label: 'Active' },
                      { value: 'INACTIVE',    label: 'Inactive' },
                      { value: 'SUSPENDED',   label: 'Suspended' },
                      { value: 'TERMINATED',  label: 'Terminated' },
                    ]} />
                  <Field label='Role'            name='role'             value={form.role}             onChange={set}
                    options={[
                      { value: 'EMPLOYEE',      label: 'Employee' },
                      { value: 'MANAGER',       label: 'Manager' },
                      { value: 'HR_ADMIN',      label: 'HR Admin' },
                      { value: 'PAYROLL_ADMIN', label: 'Payroll Admin' },
                      { value: 'SUPER_ADMIN',   label: 'Super Admin' },
                    ]} />
                  <Field label='Start Date' name='startDate' value={form.startDate} onChange={set} type='date' />
                  <Field label='End Date'   name='endDate'   value={form.endDate}   onChange={set} type='date' />
                </div>
              )}

            </div>

            <div className='profile-form-actions'>
              {canDelete && (
                <button onClick={() => setConfirmDelete(true)} className='btn btn-danger btn-sm'>
                  <i className='bi bi-trash'></i> Delete
                </button>
              )}
              <button onClick={handleSave} className='btn btn-sm' disabled={saving}>
                {saving ? 'Saving...' : <><i className='bi bi-save'></i> Save Changes</>}
              </button>
            </div>
          </div>
        </div>
      </div>

      {confirmDelete && (
        <div className='confirm-overlay'>
          <div className='confirm-box'>
            <div className='confirm-box__icon'><i className='bi bi-exclamation-triangle'></i></div>
            <h3>Delete Employee</h3>
            <p>Are you sure you want to delete <strong>{fullName}</strong>? This cannot be undone.</p>
            <div className='confirm-box__actions'>
              <button onClick={() => setConfirmDelete(false)} className='btn btn-ghost'>Cancel</button>
              <button onClick={handleDelete} className='btn btn-danger'>Yes, Delete</button>
            </div>
          </div>
        </div>
      )}
    </>
  );
};

export default EmployeeDetailsPage;
