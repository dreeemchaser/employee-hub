import './index.css';
import { useState } from 'react';
import { Routes, Route, Navigate } from 'react-router-dom';
import { isLoggedIn } from './api/AuthService';
import LoginPage from './components/LoginPage';
import Sidebar from './components/Sidebar';
import DashboardPage from './pages/DashboardPage';
import EmployeesPage from './pages/EmployeesPage';
import LeaveApprovalsPage from './pages/LeaveApprovalsPage';
import TimesheetApprovalsPage from './pages/TimesheetApprovalsPage';
import DocumentsPage from './pages/DocumentsPage';
import AuditLogsPage from './pages/AuditLogsPage';
import SalaryPage from './pages/SalaryPage';

function App() {
  const [loggedIn, setLoggedIn] = useState(isLoggedIn());

  if (!loggedIn) return <LoginPage onLogin={() => setLoggedIn(true)} />;

  return (
    <div className='app-shell'>
      <Sidebar onLogout={() => setLoggedIn(false)} />
      <div className='main-content'>
        <Routes>
          <Route path='/' element={<Navigate to='/dashboard' />} />
          <Route path='/dashboard'            element={<DashboardPage />} />
          <Route path='/employees'            element={<EmployeesPage />} />
          <Route path='/leave-approvals'      element={<LeaveApprovalsPage />} />
          <Route path='/timesheet-approvals'  element={<TimesheetApprovalsPage />} />
          <Route path='/documents'            element={<DocumentsPage />} />
          <Route path='/salary'               element={<SalaryPage />} />
          <Route path='/audit-logs'           element={<AuditLogsPage />} />
          <Route path='*'                     element={<Navigate to='/dashboard' />} />
        </Routes>
      </div>
    </div>
  );
}

export default App;
