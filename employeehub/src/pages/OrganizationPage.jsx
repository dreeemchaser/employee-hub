import { useCallback, useEffect, useState } from 'react';
import TopBar from '../components/TopBar';
import { getOrganizationTree, searchOrganization } from '../api/EmployeeService';

function OrganizationNode({ node, onSelect }) {
  return (
    <li className='organization-node'>
      <button className='card organization-node__button' onClick={() => onSelect(node.id)}>
        <strong>{node.displayName}</strong>
        <span>{node.jobTitle}</span>
        <small>{node.teamName || node.departmentName || 'Unassigned'} · {node.childCount} report{node.childCount === 1 ? '' : 's'}</small>
        {node.employmentStatus === 'TERMINATED' && <span className='badge badge--inactive'>Terminated</span>}
        {node.missingHierarchy && <span className='badge badge--pending'>Hierarchy review</span>}
      </button>
      {node.children?.length > 0 && (
        <ul className='organization-tree'>
          {node.children.map(child => <OrganizationNode key={child.id} node={child} onSelect={onSelect} />)}
        </ul>
      )}
    </li>
  );
}

export default function OrganizationPage() {
  const [nodes, setNodes] = useState([]);
  const [query, setQuery] = useState('');
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    try {
      const response = query.trim()
        ? await searchOrganization(query)
        : await getOrganizationTree();
      setNodes(response.data?.data?.content ?? []);
    } catch {
      setNodes([]);
    } finally {
      setLoading(false);
    }
  }, [query]);

  useEffect(() => { load(); }, [load]);

  return (
    <>
      <TopBar title='My Organization' breadcrumb='Employee Hub / Organization' />
      <div className='page'>
        <div className='card__header'>
          <div>
            <h2 className='card__title'>Organization chart</h2>
            <p className='text-muted'>Select a person to view their permitted reporting context.</p>
          </div>
          <input
            className='form-control'
            placeholder='Search people or job titles'
            value={query}
            onChange={event => setQuery(event.target.value)}
            aria-label='Search organization'
          />
        </div>
        {loading ? <div className='empty-state'>Loading organization…</div>
          : nodes.length === 0 ? <div className='empty-state'>No organization results found.</div>
            : <ul className='organization-tree'>{nodes.map(node => (
              <OrganizationNode key={node.id} node={node} onSelect={() => {}} />
            ))}</ul>}
      </div>
    </>
  );
}
