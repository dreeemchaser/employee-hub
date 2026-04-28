import { Link } from 'react-router-dom';

const API_URL = process.env.REACT_APP_API_URL || 'http://localhost:8080';
const FALLBACK = 'https://ui-avatars.com/api/?background=1a3a5c&color=fff&name=';

const STATUS_MAP = {
  active:     { cls: 'active',     icon: 'bi-circle-fill' },
  inactive:   { cls: 'inactive',   icon: 'bi-circle-fill' },
  suspended:  { cls: 'suspended',  icon: 'bi-circle-fill' },
  terminated: { cls: 'terminated', icon: 'bi-circle-fill' },
};

const EmployeeCard = ({ employee }) => {
  const fullName  = `${employee.firstName ?? ''} ${employee.lastName ?? ''}`.trim() || employee.name || '—';
  const statusKey = (employee.employmentStatus ?? employee.status ?? '').toLowerCase();
  const status    = STATUS_MAP[statusKey] ?? STATUS_MAP.inactive;

  const photoSrc = employee.profilePhoto
    ? `${API_URL}/employees/photo/${employee.profilePhoto}`
    : employee.photoURL
      ? `${API_URL}/employees/photo/${employee.photoURL}`
      : `${FALLBACK}${encodeURIComponent(fullName)}`;

  const dept = employee.department?.name ?? employee.department ?? null;

  return (
    <Link to={`/employees/${employee.id}`} className='emp-card'>
      <div className='emp-card__status-dot'>
        <i className={`bi ${status.icon} emp-card__dot emp-card__dot--${status.cls}`}></i>
      </div>

      <div className='emp-card__top'>
        <img src={photoSrc} alt={fullName} className='emp-card__avatar' />
        <div className='emp-card__identity'>
          <p className='emp-card__name'>{fullName}</p>
          {(employee.jobTitle ?? employee.title) && (
            <span className='emp-card__title'>{employee.jobTitle ?? employee.title}</span>
          )}
          {dept && <span className='emp-card__dept'>{dept}</span>}
        </div>
      </div>

      <div className='emp-card__divider' />

      <div className='emp-card__meta'>
        {(employee.email) && (
          <div className='emp-card__meta-row'>
            <i className='bi bi-envelope'></i>
            <span>{employee.email}</span>
          </div>
        )}
        {(employee.phone) && (
          <div className='emp-card__meta-row'>
            <i className='bi bi-telephone'></i>
            <span>{employee.phone}</span>
          </div>
        )}
      </div>

      <div className='emp-card__footer'>
        {employee.employeeNumber && (
          <span className='emp-card__empnum'>{employee.employeeNumber}</span>
        )}
        <span className={`emp-card__badge emp-card__badge--${status.cls}`}>
          {(employee.employmentStatus ?? employee.status) || 'Unknown'}
        </span>
      </div>
    </Link>
  );
};

export default EmployeeCard;
