import { useState } from 'react';
import { login } from '../api/AuthService';

const LoginPage = ({ onLogin }) => {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setLoading(true);
    setError('');
    try {
      await login(email, password);
      onLogin();
    } catch {
      setError('Invalid username or password.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className='login-wrapper'>
      <div className='login-box'>
        <div className='login-box__brand'>
          <div className='login-box__brand-icon'>
            <i className='bi bi-building'></i>
          </div>
          <div>
            <div className='login-box__brand-text'>Employee Hub</div>
            <div className='login-box__brand-sub'>HR Portal</div>
          </div>
        </div>

        <h2>Welcome back</h2>
        <p>Sign in to your account to continue</p>

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
          <div className='form-group'>
            <label className='form-label'>Password</label>
            <input
              className='form-control'
              type='password'
              value={password}
              onChange={e => setPassword(e.target.value)}
              required
            />
          </div>

          {error && (
            <p className='login-error'>
              <i className='bi bi-exclamation-circle'></i> {error}
            </p>
          )}

          <button type='submit' className='btn' disabled={loading}>
            {loading ? 'Signing in...' : 'Sign In'}
          </button>
        </form>
      </div>
    </div>
  );
};

export default LoginPage;
