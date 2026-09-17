import { useState } from 'react';
import { Link } from 'react-router-dom';
import { forgotPassword } from '../api/AuthService';

const ForgotPasswordPage = () => {
  const [email, setEmail]     = useState('');
  const [message, setMessage] = useState('');
  const [error, setError]     = useState('');
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setLoading(true);
    setError('');
    setMessage('');
    try {
      const msg = await forgotPassword(email);
      setMessage(msg || 'If an account exists for that email, a password reset link has been sent.');
    } catch {
      setError('Something went wrong. Please try again.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className='login-wrapper'>
      <div className='login-box'>
        <div className='login-box__brand'>
          <div className='login-box__brand-icon'><i className='bi bi-building'></i></div>
          <div>
            <div className='login-box__brand-text'>Employee Hub</div>
            <div className='login-box__brand-sub'>HR Admin Portal</div>
          </div>
        </div>

        <h2>Forgot password</h2>
        <p>Enter your email and we'll send you a reset link</p>

        <form onSubmit={handleSubmit}>
          <div className='form-group'>
            <label className='form-label'>Email</label>
            <input
              className='form-control'
              type='email'
              value={email}
              onChange={e => setEmail(e.target.value)}
              required
              autoFocus
            />
          </div>

          {message && (
            <p className='login-error' style={{ color: '#1a7f37' }}>
              <i className='bi bi-check-circle'></i> {message}
            </p>
          )}
          {error && (
            <p className='login-error'>
              <i className='bi bi-exclamation-circle'></i> {error}
            </p>
          )}

          <button type='submit' className='btn' disabled={loading}>
            {loading ? 'Sending...' : 'Send reset link'}
          </button>
        </form>

        <p style={{ marginTop: '1rem', textAlign: 'center' }}>
          <Link to='/login'>Back to sign in</Link>
        </p>
      </div>
    </div>
  );
};

export default ForgotPasswordPage;
