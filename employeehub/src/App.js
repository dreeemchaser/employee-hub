import './index.css';
import { useState } from 'react';
import { Routes, Route, Navigate } from 'react-router-dom';
import { isLoggedIn, isHrOrAdmin } from './api/AuthService';
import LoginPage from './components/LoginPage';
import Sidebar from './components/Sidebar';
import DashboardPage from './pages/DashboardPage';
import EmployeesPage from './pages/EmployeesPage';
import EmployeeDetailsPage from './pages/EmployeeDetailsPage';
import LeavePage from './pages/LeavePage';
import TimesheetsPage from './pages/TimesheetsPage';
import DocumentsPage from './pages/DocumentsPage';
import PerformancePage from './pages/PerformancePage';
import SalaryPage from './pages/SalaryPage';
import BenefitsPage from './pages/BenefitsPage';
import ProfilePage from './pages/ProfilePage';
import NotificationsPage from './pages/NotificationsPage';

function App() {
  const [loggedIn, setLoggedIn] = useState(isLoggedIn());

  if (!loggedIn) return <LoginPage onLogin={() => setLoggedIn(true)} />;

  return (
    <div className='app-shell'>
      <Sidebar onLogout={() => setLoggedIn(false)} />
      <div className='main-content'>
        <Routes>
          <Route path='/' element={<Navigate to='/dashboard' />} />
          <Route path='/dashboard' element={<DashboardPage />} />
          {isHrOrAdmin() && (
            <>
              <Route path='/employees' element={<EmployeesPage />} />
              <Route path='/employees/:id' element={<EmployeeDetailsPage />} />
            </>
          )}
          <Route path='/leave' element={<LeavePage />} />
          <Route path='/timesheets' element={<TimesheetsPage />} />
          <Route path='/documents' element={<DocumentsPage />} />
          <Route path='/performance' element={<PerformancePage />} />
          <Route path='/salary' element={<SalaryPage />} />
          <Route path='/benefits' element={<BenefitsPage />} />
          <Route path='/notifications' element={<NotificationsPage />} />
          <Route path='/profile' element={<ProfilePage />} />
          <Route path='*' element={<Navigate to='/dashboard' />} />
        </Routes>
      </div>
    </div>
  );
}

export default App;
