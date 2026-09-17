import { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { login } from '../api/AuthService';

// mm:ss for a countdown of whole seconds.
const formatCountdown = (totalSeconds) => {
  const m = Math.floor(totalSeconds / 60);
  const s = totalSeconds % 60;
  return `${m}:${String(s).padStart(2, '0')}`;
};

const LoginPage = ({ onLogin }) => {
  const [email, setEmail]       = useState('');
  const [password, setPassword] = useState('');
  const [error, setError]       = useState('');
  const [loading, setLoading]   = useState(false);
  const [lockSeconds, setLockSeconds] = useState(0);

  // While locked out, tick the countdown down once per second and clear it
  // (re-enabling the form) when it reaches zero.
  useEffect(() => {
    if (lockSeconds <= 0) return undefined;
    const id = setInterval(() => {
      setLockSeconds((s) => {
        if (s <= 1) {
          setError('');
          return 0;
        }
        return s - 1;
      });
    }, 1000);
    return () => clearInterval(id);
  }, [lockSeconds]);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setLoading(true);
    setError('');
    try {
      await login(email, password);
      onLogin();
    } catch (err) {
      setError(err.message || 'Invalid email or password.');
      if (err.retryAfterSeconds > 0) setLockSeconds(err.retryAfterSeconds);
    } finally {
      setLoading(false);
    }
  };

  const locked = lockSeconds > 0;

  return (
    <div className='login-wrapper'>
      <div className='login-box'>
        <div className='login-box__brand'>
          <div className='login-box__brand-icon'>
            <i className='bi bi-building'></i>
          </div>
          <div>
            <div className='login-box__brand-text'>Employee Hub</div>
            <div className='login-box__brand-sub'>HR Admin Portal</div>
          </div>
        </div>

        <h2>Welcome back</h2>
        <p>Sign in with your HR admin account</p>

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
              {locked && (
                <> You can try again in <strong>{formatCountdown(lockSeconds)}</strong>.</>
              )}
            </p>
          )}

          <button type='submit' className='btn' disabled={loading || locked}>
            {loading ? 'Signing in...' : locked ? `Locked — ${formatCountdown(lockSeconds)}` : 'Sign In'}
          </button>
        </form>

        <p style={{ marginTop: '1rem', textAlign: 'center' }}>
          <Link to='/forgot-password'>Forgot your password?</Link>
        </p>
      </div>
    </div>
  );
};

export default LoginPage;
