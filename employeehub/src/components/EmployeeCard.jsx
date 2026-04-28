import { Link } from 'react-router-dom';

const API_URL = process.env.REACT_APP_API_URL || 'http://localhost:8080';
const FALLBACK = 'https://ui-avatars.com/api/?background=1a3a5c&color=fff&name=';

const EmployeeCard = ({ employee }) => {
  const photoSrc = employee.photoURL
    ? `${API_URL}/employees/photo/${employee.photoURL}`
    : `${FALLBACK}${encodeURIComponent(employee.name)}`;

  const isActive = employee.status?.toLowerCase() === 'active';

  return (
    <Link to={`/employees/${employee.id}`} className='employee__item'>
      <div className='employee__header'>
        <img src={photoSrc} alt={employee.name} className='avatar avatar--md' />
        <div style={{ flex: 1, minWidth: 0 }}>
          <p style={{ fontWeight: 600, fontSize: '0.9rem', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
            {employee.name}
          </p>
          {employee.title && (
            <span style={{ fontSize: '0.72rem', color: 'var(--brand)', background: 'var(--brand-light)', borderRadius: 'var(--radius-full)', padding: '1px 8px', display: 'inline-block', marginTop: 2, fontWeight: 500 }}>
              {employee.title}
            </span>
          )}
        </div>
      </div>
      <div className='employee__body'>
        <p><i className='bi bi-envelope'></i>{employee.email?.substring(0, 24)}</p>
        <p><i className='bi bi-telephone'></i>{employee.phone}</p>
        <p><i className='bi bi-geo'></i>{employee.address}</p>
        <p>
          <span className={`badge badge--${isActive ? 'active' : 'inactive'}`}>
            <i className={`bi ${isActive ? 'bi-check-circle' : 'bi-x-circle'}`}></i>
            {employee.status}
          </span>
        </p>
      </div>
    </Link>
  );
};

export default EmployeeCard;
