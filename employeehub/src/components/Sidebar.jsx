import { NavLink, useNavigate } from 'react-router-dom';
import { logout, isHrOrAdmin } from '../api/AuthService';

const Sidebar = ({ onLogout }) => {
  const navigate = useNavigate();
  const hrOrAdmin = isHrOrAdmin();

  const handleLogout = () => {
    logout();
    onLogout();
  };

  return (
    <aside className='sidebar'>
      <div className='sidebar__logo'>
        <div className='sidebar__logo-icon'>
          <i className='bi bi-building'></i>
        </div>
        <div>
          <div className='sidebar__logo-text'>Employee Hub</div>
          <div className='sidebar__logo-sub'>HR Portal</div>
        </div>
      </div>

      <nav className='sidebar__nav'>
        <div>
          <div className='sidebar__section-label'>Main</div>
          <NavLink to='/dashboard' className={({ isActive }) => `sidebar__link${isActive ? ' active' : ''}`}>
            <i className='bi bi-grid-1x2'></i> Dashboard
          </NavLink>
          {hrOrAdmin && (
            <NavLink to='/employees' className={({ isActive }) => `sidebar__link${isActive ? ' active' : ''}`}>
              <i className='bi bi-people'></i> Employees
            </NavLink>
          )}
        </div>
        <div>
          <div className='sidebar__section-label'>Self Service</div>
          {[
            { to: '/leave',        icon: 'bi-calendar-check',  label: 'Leave' },
            { to: '/timesheets',   icon: 'bi-clock-history',   label: 'Timesheets' },
            { to: '/documents',    icon: 'bi-folder2-open',    label: 'Documents' },
            { to: '/performance',  icon: 'bi-graph-up-arrow',  label: 'Performance' },
            { to: '/salary',       icon: 'bi-cash-coin',       label: 'Salary' },
            { to: '/benefits',     icon: 'bi-shield-check',    label: 'Benefits' },
            { to: '/notifications',icon: 'bi-bell',            label: 'Notifications' },
          ].map(({ to, icon, label }) => (
            <NavLink key={to} to={to} className={({ isActive }) => `sidebar__link${isActive ? ' active' : ''}`}>
              <i className={`bi ${icon}`}></i> {label}
            </NavLink>
          ))}
        </div>
        <div>
          <div className='sidebar__section-label'>Account</div>
          <NavLink to='/profile' className={({ isActive }) => `sidebar__link${isActive ? ' active' : ''}`}>
            <i className='bi bi-person-circle'></i> My Profile
          </NavLink>
        </div>
      </nav>

      <div className='sidebar__footer'>
        <button className='sidebar__logout' onClick={handleLogout}>
          <i className='bi bi-box-arrow-left'></i> Sign Out
        </button>
      </div>
    </aside>
  );
};

export default Sidebar;
