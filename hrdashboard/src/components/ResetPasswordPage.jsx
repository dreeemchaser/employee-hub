import { useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { resetPassword } from '../api/AuthService';

const ResetPasswordPage = () => {
  const [params]                = useSearchParams();
  const token                   = params.get('token') || '';
  const [password, setPassword] = useState('');
  const [confirm, setConfirm]   = useState('');
  const [error, setError]       = useState('');
  const [done, setDone]         = useState(false);
  const [loading, setLoading]   = useState(false);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    if (password.length < 8) {
      setError('Password must be at least 8 characters.');
      return;
    }
    if (password !== confirm) {
      setError('Passwords do not match.');
      return;
    }
    setLoading(true);
    try {
      await resetPassword(token, password);
      setDone(true);
    } catch (err) {
      setError(err.response?.data?.message || 'This reset link is invalid or has expired.');
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

        <h2>Reset password</h2>
        <p>Enter a new password for your account</p>

        {done ? (
          <>
            <p className='login-error' style={{ color: '#1a7f37' }}>
              <i className='bi bi-check-circle'></i> Your password has been reset. You can now sign in.
            </p>
            <p style={{ marginTop: '1rem', textAlign: 'center' }}>
              <Link to='/login'>Go to sign in</Link>
            </p>
          </>
        ) : !token ? (
          <>
            <p className='login-error'>
              <i className='bi bi-exclamation-circle'></i> This reset link is missing its token. Please use the link from your email.
            </p>
            <p style={{ marginTop: '1rem', textAlign: 'center' }}>
              <Link to='/forgot-password'>Request a new link</Link>
            </p>
          </>
        ) : (
          <form onSubmit={handleSubmit}>
            <div className='form-group'>
              <label className='form-label'>New password</label>
              <input
                className='form-control'
                type='password'
                value={password}
                onChange={e => setPassword(e.target.value)}
                required
                autoFocus
                autoComplete='new-password'
              />
            </div>
            <div className='form-group'>
              <label className='form-label'>Confirm new password</label>
              <input
                className='form-control'
                type='password'
                value={confirm}
                onChange={e => setConfirm(e.target.value)}
                required
                autoComplete='new-password'
              />
            </div>

            {error && (
              <p className='login-error'>
                <i className='bi bi-exclamation-circle'></i> {error}
              </p>
            )}

            <button type='submit' className='btn' disabled={loading}>
              {loading ? 'Resetting...' : 'Reset password'}
            </button>
          </form>
        )}
      </div>
    </div>
  );
};

export default ResetPasswordPage;
