import { NavLink, useNavigate } from 'react-router-dom';
import { logout } from '../api/AuthService';

const NAV = [
  { section: 'Main', links: [
    { to: '/dashboard', icon: 'bi-grid-1x2', label: 'Dashboard' },
  ]},
  { section: 'HR Management', links: [
    { to: '/employees',           icon: 'bi-people',         label: 'Employees' },
    { to: '/leave-approvals',     icon: 'bi-calendar-check', label: 'Leave Approvals' },
    { to: '/timesheet-approvals', icon: 'bi-clock-history',  label: 'Timesheet Approvals' },
    { to: '/documents',           icon: 'bi-folder2-open',   label: 'Documents' },
    { to: '/salary',              icon: 'bi-cash-coin',      label: 'Salary' },
  ]},
  { section: 'System', links: [
    { to: '/audit-logs', icon: 'bi-journal-text', label: 'Audit Logs' },
  ]},
];

const Sidebar = ({ onLogout }) => {
  const navigate = useNavigate();

  const handleLogout = () => {
    logout();
    onLogout();
    navigate('/');
  };

  return (
    <aside className='sidebar'>
      <div className='sidebar__logo'>
        <div className='sidebar__logo-icon'>
          <i className='bi bi-building'></i>
        </div>
        <div>
          <div className='sidebar__logo-text'>Employee Hub</div>
          <div className='sidebar__logo-sub'>HR Admin Portal</div>
        </div>
      </div>

      <nav className='sidebar__nav'>
        {NAV.map(({ section, links }) => (
          <div key={section}>
            <div className='sidebar__section-label'>{section}</div>
            {links.map(({ to, icon, label }) => (
              <NavLink
                key={to}
                to={to}
                className={({ isActive }) => `sidebar__link${isActive ? ' active' : ''}`}
              >
                <i className={`bi ${icon}`}></i>
                {label}
              </NavLink>
            ))}
          </div>
        ))}
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
