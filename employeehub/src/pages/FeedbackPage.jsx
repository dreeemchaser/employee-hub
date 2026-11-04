import { useCallback, useEffect, useState } from 'react';
import TopBar from '../components/TopBar';
import { getReceivedFeedback, submitFeedback } from '../api/EmployeeService';

export default function FeedbackPage() {
  const [feedback, setFeedback] = useState([]);
  const [form, setForm] = useState({ subjectId: '', comment: '', rating: '', visibility: 'SUBJECT' });
  const [message, setMessage] = useState('');

  const load = useCallback(async () => {
    try {
      const response = await getReceivedFeedback();
      setFeedback(response.data?.data ?? []);
    } catch {
      setFeedback([]);
    }
  }, []);

  useEffect(() => { load(); }, [load]);

  const submit = async event => {
    event.preventDefault();
    try {
      await submitFeedback({ ...form, rating: form.rating ? Number(form.rating) : null });
      setForm({ subjectId: '', comment: '', rating: '', visibility: 'SUBJECT' });
      setMessage('Feedback submitted.');
    } catch {
      setMessage('Feedback could not be submitted.');
    }
  };

  return (
    <>
      <TopBar title='Feedback' breadcrumb='Employee Hub / Feedback' />
      <div className='page'>
        <div className='card'>
          <div className='card__header'><h2 className='card__title'>Give feedback</h2></div>
          <form className='form-grid' onSubmit={submit}>
            <input className='form-control' placeholder='Subject employee ID' value={form.subjectId}
              onChange={e => setForm({ ...form, subjectId: e.target.value })} required />
            <input className='form-control' type='number' min='1' max='5' placeholder='Rating (optional)' value={form.rating}
              onChange={e => setForm({ ...form, rating: e.target.value })} />
            <select className='form-control' value={form.visibility}
              onChange={e => setForm({ ...form, visibility: e.target.value })}>
              <option value='SUBJECT'>Visible to subject</option>
              <option value='ANONYMOUS'>Anonymous to subject</option>
              <option value='PRIVATE'>Private</option>
            </select>
            <textarea className='form-control' placeholder='Actionable feedback' value={form.comment}
              onChange={e => setForm({ ...form, comment: e.target.value })} required />
            <button className='btn btn-primary' type='submit'>Submit feedback</button>
          </form>
          {message && <p className='feedback'>{message}</p>}
        </div>
        <div className='card'>
          <div className='card__header'><h2 className='card__title'>Feedback received</h2></div>
          {feedback.length === 0 ? <div className='empty-state'>No feedback received yet.</div> : feedback.map(item => (
            <article className='card__body' key={item.id}>
              <strong>{item.authorName}</strong>{item.rating && <span> · {item.rating}/5</span>}
              <p>{item.comment}</p>
            </article>
          ))}
        </div>
      </div>
    </>
  );
}
