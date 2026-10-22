import { useCallback, useEffect, useState } from 'react';
import TopBar from '../components/TopBar';
import { createTaxBracket, getTaxBrackets, updateTaxBracket } from '../api/HrService';

const EMPTY = {
  taxYear: new Date().getFullYear(),
  minIncome: '',
  maxIncome: '',
  baseTax: '',
  marginalRate: '',
  rebate: '',
};

export default function TaxBracketsPage() {
  const [year, setYear] = useState('');
  const [brackets, setBrackets] = useState([]);
  const [form, setForm] = useState(EMPTY);
  const [editingId, setEditingId] = useState(null);
  const [feedback, setFeedback] = useState(null);
  const [loading, setLoading] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const res = await getTaxBrackets(year || undefined);
      setBrackets(res.data?.data?.content ?? []);
    } catch (error) {
      setFeedback({ type: 'error', message: error.response?.data?.message ?? 'Could not load tax brackets.' });
    } finally {
      setLoading(false);
    }
  }, [year]);

  useEffect(() => { load(); }, [load]);

  const updateField = event => setForm(prev => ({ ...prev, [event.target.name]: event.target.value }));

  const submit = async event => {
    event.preventDefault();
    setFeedback(null);
    const payload = {
      ...form,
      taxYear: Number(form.taxYear),
      minIncome: Number(form.minIncome),
      maxIncome: form.maxIncome === '' ? null : Number(form.maxIncome),
      baseTax: Number(form.baseTax),
      marginalRate: Number(form.marginalRate),
      rebate: Number(form.rebate),
    };
    try {
      if (editingId) await updateTaxBracket(editingId, payload);
      else await createTaxBracket(payload);
      setFeedback({ type: 'success', message: editingId ? 'Tax bracket updated.' : 'Tax bracket created.' });
      setForm(EMPTY);
      setEditingId(null);
      await load();
    } catch (error) {
      setFeedback({ type: 'error', message: error.response?.data?.message ?? 'Could not save tax bracket.' });
    }
  };

  const edit = bracket => {
    setEditingId(bracket.id);
    setForm({
      taxYear: bracket.taxYear,
      minIncome: bracket.minIncome,
      maxIncome: bracket.maxIncome ?? '',
      baseTax: bracket.baseTax,
      marginalRate: bracket.marginalRate,
      rebate: bracket.rebate,
    });
  };

  return (
    <>
      <TopBar title='Tax Tables' breadcrumb='HR Admin / Tax Tables' />
      <div className='page'>
        {feedback && <div className={`alert alert--${feedback.type}`}>{feedback.message}</div>}
        <div className='card'>
          <div className='card__header'>
            <span className='card__title'>PAYE tax brackets</span>
            <input
              aria-label='Filter tax year'
              placeholder='Tax year'
              value={year}
              onChange={event => setYear(event.target.value)}
              style={{ width: 110 }}
            />
          </div>
          <div className='table-wrap'>
            <table>
              <thead><tr><th>Tax year</th><th>From</th><th>To</th><th>Base tax</th><th>Marginal rate</th><th>Rebate</th><th /></tr></thead>
              <tbody>
                {loading ? <tr><td colSpan={7}>Loading...</td></tr> : brackets.map(bracket => (
                  <tr key={bracket.id}>
                    <td>{bracket.taxYear}</td>
                    <td>{bracket.minIncome}</td>
                    <td>{bracket.maxIncome ?? 'No limit'}</td>
                    <td>{bracket.baseTax}</td>
                    <td>{bracket.marginalRate}%</td>
                    <td>{bracket.rebate}</td>
                    <td><button className='btn btn-ghost btn-sm' onClick={() => edit(bracket)}>Edit</button></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
        <div className='card' style={{ marginTop: '1rem' }}>
          <div className='card__header'><span className='card__title'>{editingId ? 'Edit bracket' : 'Add bracket'}</span></div>
          <form className='card__body form-grid' onSubmit={submit}>
            {[
              ['taxYear', 'Tax year'], ['minIncome', 'Minimum income'], ['maxIncome', 'Maximum income (optional)'],
              ['baseTax', 'Base tax'], ['marginalRate', 'Marginal rate (%)'], ['rebate', 'Annual rebate'],
            ].map(([name, label]) => (
              <label className='form-group' key={name}>
                <span className='form-label'>{label}</span>
                <input name={name} type='number' min='0' step='0.01' value={form[name]} onChange={updateField} required={name !== 'maxIncome'} />
              </label>
            ))}
            <div style={{ gridColumn: '1 / -1', display: 'flex', gap: '0.5rem' }}>
              <button className='btn btn-primary' type='submit'>{editingId ? 'Save changes' : 'Add bracket'}</button>
              {editingId && <button className='btn btn-ghost' type='button' onClick={() => { setEditingId(null); setForm(EMPTY); }}>Cancel</button>}
            </div>
          </form>
        </div>
      </div>
    </>
  );
}
