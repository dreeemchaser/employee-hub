import { useState, useEffect, useCallback } from 'react';
import EmployeeCard from '../components/EmployeeCard';
import Spinner from '../components/Spinner';
import TopBar from '../components/TopBar';
import NewEmployeeModal from '../components/NewEmployeeModal';
import { getEmployees, getDepartments, getTeams } from '../api/EmployeeService';

const EmployeesPage = () => {
  const [data, setData]           = useState({});
  const [currentPage, setCurrentPage] = useState(0);
  const [loading, setLoading]     = useState(true);
  const [viewMode, setViewMode]   = useState('cards'); // 'cards' or 'table'
  
  // Filter state
  const [filters, setFilters]     = useState({ departmentId: '', teamId: '', status: '' });
  const [departments, setDepartments] = useState([]);
  const [teams, setTeams]         = useState([]);
  const [showFilters, setShowFilters] = useState(false);

  const load = useCallback(async (page = 0, currentFilters = filters) => {
    setLoading(true);
    try {
      const filterParams = {};
      if (currentFilters.departmentId) filterParams.departmentId = currentFilters.departmentId;
      if (currentFilters.teamId) filterParams.teamId = currentFilters.teamId;
      if (currentFilters.status) filterParams.status = currentFilters.status;
      
      const res = await getEmployees(page, 20, filterParams);
      setData(res.data);
      setCurrentPage(page);
    } finally {
      setLoading(false);
    }
  }, [filters]);

  useEffect(() => { load(0); }, [load]);

  // Load departments on mount
  useEffect(() => {
    getDepartments()
      .then(r => setDepartments(r.data?.data ?? []))
      .catch(() => {});
  }, []);

  // Load teams when department filter changes
  useEffect(() => {
    if (!filters.departmentId) { 
      setTeams([]);
      return; 
    }
    getTeams(filters.departmentId)
      .then(r => setTeams(r.data?.data ?? []))
      .catch(() => {});
  }, [filters.departmentId]);

  // Reload data when filters change
  useEffect(() => {
    load(0, filters);
    if (currentPage !== 0) setCurrentPage(0);
  }, [filters, load]); // currentPage intentionally omitted to avoid infinite loop

  const handleFilterChange = (name, value) => {
    setFilters(prev => ({
      ...prev,
      [name]: value,
      // Reset team when department changes
      ...(name === 'departmentId' ? { teamId: '' } : {})
    }));
  };

  const clearFilters = () => {
    setFilters({ departmentId: '', teamId: '', status: '' });
  };

  const hasActiveFilters = filters.departmentId || filters.teamId || filters.status;

  const content    = data?.content ?? [];
  const totalPages = data?.totalPages ?? 0;

  return (
    <>
      <TopBar title='Employees' breadcrumb='Employee Hub / Employees'>
        <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'center' }}>
          {/* Filter Toggle */}
          <button 
            className={`btn btn-sm ${showFilters ? '' : 'btn-ghost'}`}
            onClick={() => setShowFilters(!showFilters)}
          >
            <i className='bi bi-funnel'></i> 
            {hasActiveFilters && <span className="badge" style={{ 
              background: 'var(--red)', color: '#fff', fontSize: '0.6rem', 
              marginLeft: '0.25rem', padding: '0.1rem 0.3rem' 
            }}>●</span>}
          </button>
          
          {/* View Toggle */}
          <div style={{ display: 'flex', border: '1px solid var(--border)', borderRadius: 'var(--radius)', overflow: 'hidden' }}>
            <button 
              className={`btn btn-sm ${viewMode === 'cards' ? '' : 'btn-ghost'}`}
              style={{ borderRadius: 0, border: 'none' }}
              onClick={() => setViewMode('cards')}
            >
              <i className='bi bi-grid-3x3-gap'></i> Cards
            </button>
            <button 
              className={`btn btn-sm ${viewMode === 'table' ? '' : 'btn-ghost'}`}
              style={{ borderRadius: 0, border: 'none' }}
              onClick={() => setViewMode('table')}
            >
              <i className='bi bi-table'></i> Table
            </button>
          </div>
          <NewEmployeeModal onEmployeeSaved={() => load(currentPage)} />
        </div>
      </TopBar>

      <div className='page'>
        {/* Filter Panel */}
        {showFilters && (
          <div className='card' style={{ marginBottom: '1.5rem' }}>
            <div className='card__header'>
              <span className='card__title'>
                <i className='bi bi-funnel'></i> Filter Employees
              </span>
              {hasActiveFilters && (
                <button className='btn btn-sm btn-ghost' onClick={clearFilters}>
                  <i className='bi bi-x-circle'></i> Clear All
                </button>
              )}
            </div>
            <div className='card__body'>
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: '1rem' }}>
                <div className='form-group' style={{ marginBottom: 0 }}>
                  <label className='form-label'>Department</label>
                  <select 
                    className='form-control' 
                    value={filters.departmentId} 
                    onChange={(e) => handleFilterChange('departmentId', e.target.value)}
                  >
                    <option value=''>All Departments</option>
                    {departments.map(d => (
                      <option key={d.id} value={d.id}>{d.name}</option>
                    ))}
                  </select>
                </div>

                <div className='form-group' style={{ marginBottom: 0 }}>
                  <label className='form-label'>Team</label>
                  <select 
                    className='form-control' 
                    value={filters.teamId} 
                    onChange={(e) => handleFilterChange('teamId', e.target.value)}
                    disabled={!filters.departmentId}
                  >
                    <option value=''>All Teams</option>
                    {teams.map(t => (
                      <option key={t.id} value={t.id}>{t.name}</option>
                    ))}
                  </select>
                </div>

                <div className='form-group' style={{ marginBottom: 0 }}>
                  <label className='form-label'>Employment Status</label>
                  <select 
                    className='form-control' 
                    value={filters.status} 
                    onChange={(e) => handleFilterChange('status', e.target.value)}
                  >
                    <option value=''>All Statuses</option>
                    <option value='ACTIVE'>Active</option>
                    <option value='INACTIVE'>Inactive</option>
                    <option value='SUSPENDED'>Suspended</option>
                    <option value='TERMINATED'>Terminated</option>
                  </select>
                </div>
              </div>

              {hasActiveFilters && (
                <div style={{ marginTop: '1rem', paddingTop: '1rem', borderTop: '1px solid var(--border)' }}>
                  <div style={{ display: 'flex', gap: '0.5rem', flexWrap: 'wrap', alignItems: 'center' }}>
                    <span style={{ fontSize: '0.85rem', color: 'var(--text-muted)' }}>Active filters:</span>
                    {filters.departmentId && (
                      <span className='badge' style={{ background: 'var(--brand-light)', color: 'var(--brand)' }}>
                        Department: {departments.find(d => d.id.toString() === filters.departmentId)?.name}
                        <button 
                          style={{ marginLeft: '0.25rem', background: 'none', border: 'none', color: 'inherit' }}
                          onClick={() => handleFilterChange('departmentId', '')}
                        >
                          ×
                        </button>
                      </span>
                    )}
                    {filters.teamId && (
                      <span className='badge' style={{ background: 'var(--green)', color: '#fff' }}>
                        Team: {teams.find(t => t.id.toString() === filters.teamId)?.name}
                        <button 
                          style={{ marginLeft: '0.25rem', background: 'none', border: 'none', color: 'inherit' }}
                          onClick={() => handleFilterChange('teamId', '')}
                        >
                          ×
                        </button>
                      </span>
                    )}
                    {filters.status && (
                      <span className='badge badge--active'>
                        Status: {filters.status}
                        <button 
                          style={{ marginLeft: '0.25rem', background: 'none', border: 'none', color: 'inherit' }}
                          onClick={() => handleFilterChange('status', '')}
                        >
                          ×
                        </button>
                      </span>
                    )}
                  </div>
                </div>
              )}
            </div>
          </div>
        )}
        {loading ? (
          <Spinner />
        ) : (
          <>
            {content.length === 0 && !hasActiveFilters && (
              <div className='empty-state'>
                <i className='bi bi-people'></i>
                <p>No employees yet. Add your first employee to get started.</p>
              </div>
            )}

            {content.length === 0 && hasActiveFilters && (
              <div className='empty-state'>
                <i className='bi bi-search'></i>
                <p>No employees match the selected filters.</p>
                <button className='btn btn-ghost btn-sm' onClick={clearFilters}>
                  <i className='bi bi-funnel'></i> Clear Filters
                </button>
              </div>
            )}

            {content.length > 0 && (
              <div style={{ 
                marginBottom: '1rem', 
                padding: '0.75rem', 
                background: 'var(--bg-card)', 
                border: '1px solid var(--border)', 
                borderRadius: 'var(--radius)',
                fontSize: '0.9rem',
                color: 'var(--text-muted)'
              }}>
                Showing {content.length} of {data?.totalElements || content.length} employee{(data?.totalElements || content.length) !== 1 ? 's' : ''}
                {hasActiveFilters && ' (filtered)'}
                {totalPages > 1 && ` • Page ${currentPage + 1} of ${totalPages}`}
              </div>
            )}

            {viewMode === 'cards' && content.length > 0 && (
              <div className='contact__list'>
                {content.map(contact => (
                  <EmployeeCard employee={contact} key={contact.id} />
                ))}
              </div>
            )}

            {viewMode === 'table' && content.length > 0 && (
              <div className='card'>
                <div className='card__header'>
                  <span className='card__title'>Employee Directory</span>
                </div>
                <div className='table-wrap'>
                  <table>
                    <thead>
                      <tr>
                        <th>Employee</th>
                        <th>Job Title</th>
                        <th>Department</th>
                        <th>Team</th>
                        <th>Contact</th>
                        <th>Status</th>
                        <th></th>
                      </tr>
                    </thead>
                    <tbody>
                      {content.map(employee => {
                        const fullName = `${employee.firstName ?? ''} ${employee.lastName ?? ''}`.trim();
                        const statusKey = (employee.employmentStatus ?? employee.status ?? '').toLowerCase();
                        return (
                          <tr key={employee.id}>
                            <td>
                              <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
                                <div style={{
                                  width: 36, height: 36, borderRadius: '50%',
                                  background: 'var(--brand)', color: '#fff',
                                  display: 'flex', alignItems: 'center', justifyContent: 'center',
                                  fontSize: '0.8rem', fontWeight: 600, flexShrink: 0,
                                }}>
                                  {employee.firstName?.[0]}{employee.lastName?.[0]}
                                </div>
                                <div>
                                  <div style={{ fontWeight: 600, fontSize: '0.9rem' }}>{fullName}</div>
                                  <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>
                                    {employee.employeeNumber}
                                  </div>
                                </div>
                              </div>
                            </td>
                            <td>
                              <span style={{ fontSize: '0.9rem' }}>
                                {employee.jobTitle || '—'}
                              </span>
                            </td>
                            <td>
                              <span style={{ fontSize: '0.9rem' }}>
                                {employee.department || '—'}
                              </span>
                            </td>
                            <td>
                              <span style={{ fontSize: '0.9rem' }}>
                                {employee.team || '—'}
                              </span>
                            </td>
                            <td>
                              <div style={{ fontSize: '0.85rem' }}>
                                <div>{employee.email}</div>
                                {employee.phone && (
                                  <div style={{ color: 'var(--text-muted)' }}>{employee.phone}</div>
                                )}
                              </div>
                            </td>
                            <td>
                              <span className={`badge badge--${statusKey === 'active' ? 'active' : statusKey === 'inactive' ? 'inactive' : 'pending'}`}>
                                {employee.employmentStatus || 'Unknown'}
                              </span>
                            </td>
                            <td>
                              <a 
                                href={`/employees/${employee.id}`} 
                                className='btn btn-sm btn-ghost'
                                style={{ fontSize: '0.8rem', padding: '0.25rem 0.5rem' }}
                              >
                                <i className='bi bi-eye'></i>
                              </a>
                            </td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                </div>
              </div>
            )}

            {totalPages > 1 && (
              <div className='pagination'>
                <button className={currentPage === 0 ? 'disabled' : ''} onClick={() => load(currentPage - 1)}>‹</button>
                {Array.from({ length: totalPages }, (_, i) => (
                  <button key={i} className={i === currentPage ? 'active' : ''} onClick={() => load(i)}>{i + 1}</button>
                ))}
                <button className={currentPage === totalPages - 1 ? 'disabled' : ''} onClick={() => load(currentPage + 1)}>›</button>
              </div>
            )}
          </>
        )}
      </div>
    </>
  );
};

export default EmployeesPage;
