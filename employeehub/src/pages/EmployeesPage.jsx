import { useState, useEffect, useCallback } from 'react';
import EmployeeCard from '../components/EmployeeCard';
import Spinner from '../components/Spinner';
import TopBar from '../components/TopBar';
import NewEmployeeModal from '../components/NewEmployeeModal';
import { getEmployees } from '../api/EmployeeService';

const EmployeesPage = () => {
  const [data, setData]           = useState({});
  const [currentPage, setCurrentPage] = useState(0);
  const [loading, setLoading]     = useState(true);

  const load = useCallback(async (page = 0) => {
    setLoading(true);
    try {
      const res = await getEmployees(page);
      setData(res.data);
      setCurrentPage(page);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { load(0); }, [load]);

  const content    = data?.content ?? [];
  const totalPages = data?.totalPages ?? 0;

  return (
    <>
      <TopBar title='Employees' breadcrumb='Employee Hub / Employees'>
        <NewEmployeeModal onEmployeeSaved={() => load(currentPage)} />
      </TopBar>

      <div className='page'>
        {loading ? (
          <Spinner />
        ) : (
          <>
            {content.length === 0 && (
              <div className='empty-state'>
                <i className='bi bi-people'></i>
                <p>No employees yet. Add your first employee to get started.</p>
              </div>
            )}

            <div className='contact__list'>
              {content.map(contact => (
                <EmployeeCard employee={contact} key={contact.id} />
              ))}
            </div>

            {totalPages > 1 && (
              <div className='pagination'>
                <a className={currentPage === 0 ? 'disabled' : ''} onClick={() => load(currentPage - 1)}>‹</a>
                {Array.from({ length: totalPages }, (_, i) => (
                  <a key={i} className={i === currentPage ? 'active' : ''} onClick={() => load(i)}>{i + 1}</a>
                ))}
                <a className={currentPage === totalPages - 1 ? 'disabled' : ''} onClick={() => load(currentPage + 1)}>›</a>
              </div>
            )}
          </>
        )}
      </div>
    </>
  );
};

export default EmployeesPage;
