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
  const [showPw, setShowPw]     = useState(false);
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
      setError(err.message || 'Incorrect email or password. Please try again.');
      if (err.retryAfterSeconds > 0) setLockSeconds(err.retryAfterSeconds);
    } finally {
      setLoading(false);
    }
  };

  const locked = lockSeconds > 0;

  return (
    <div className='lp-shell'>

      {/* ── Left panel ── */}
      <div className='lp-brand'>
        <div className='lp-brand__inner'>
          <div className='lp-brand__logo'>
            <i className='bi bi-building-fill'></i>
          </div>
          <h1 className='lp-brand__name'>Employee Hub</h1>
          <p className='lp-brand__tagline'>Your all-in-one people platform</p>

          <div className='lp-brand__features'>
            {[
              { icon: 'bi-calendar-check', label: 'Leave & Attendance' },
              { icon: 'bi-cash-stack',     label: 'Payslips & Salary' },
              { icon: 'bi-bar-chart-line', label: 'Performance & Goals' },
              { icon: 'bi-file-earmark-text', label: 'Documents & Benefits' },
            ].map(f => (
              <div key={f.label} className='lp-brand__feature'>
                <div className='lp-brand__feature-icon'>
                  <i className={`bi ${f.icon}`}></i>
                </div>
                <span>{f.label}</span>
              </div>
            ))}
          </div>
        </div>

        {/* Decorative blobs */}
        <div className='lp-blob lp-blob--1' />
        <div className='lp-blob lp-blob--2' />
      </div>

      {/* ── Right panel ── */}
      <div className='lp-form-panel'>
        <div className='lp-form-box'>

          <div className='lp-form-box__header'>
            <div className='lp-form-box__avatar'>
              <i className='bi bi-person'></i>
            </div>
            <h2>Welcome back</h2>
            <p>Sign in to access your employee portal</p>
          </div>

          <form onSubmit={handleSubmit} className='lp-form'>

            <div className='lp-field'>
              <label>Email address</label>
              <div className='lp-field__wrap'>
                <i className='bi bi-envelope lp-field__icon'></i>
                <input
                  type='email'
                  placeholder='you@company.com'
                  value={email}
                  onChange={e => setEmail(e.target.value)}
                  required
                  autoFocus
                  autoComplete='email'
                />
              </div>
            </div>

            <div className='lp-field'>
              <label>Password</label>
              <div className='lp-field__wrap'>
                <i className='bi bi-lock lp-field__icon'></i>
                <input
                  type={showPw ? 'text' : 'password'}
                  placeholder='••••••••'
                  value={password}
                  onChange={e => setPassword(e.target.value)}
                  required
                  autoComplete='current-password'
                />
                <button
                  type='button'
                  className='lp-field__toggle'
                  onClick={() => setShowPw(v => !v)}
                  tabIndex={-1}
                  aria-label={showPw ? 'Hide password' : 'Show password'}
                >
                  <i className={`bi ${showPw ? 'bi-eye-slash' : 'bi-eye'}`}></i>
                </button>
              </div>
            </div>

            {error && (
              <div className='lp-error'>
                <i className='bi bi-exclamation-circle-fill'></i>
                <span>
                  {error}
                  {locked && (
                    <> You can try again in <strong>{formatCountdown(lockSeconds)}</strong>.</>
                  )}
                </span>
              </div>
            )}

            <button type='submit' className='lp-submit' disabled={loading || locked}>
              {loading
                ? <><span className='lp-submit__spinner'></span>Signing in…</>
                : locked
                  ? <><i className='bi bi-lock-fill'></i>Locked — {formatCountdown(lockSeconds)}</>
                  : <><i className='bi bi-box-arrow-in-right'></i>Sign In</>
              }
            </button>

          </form>

          <p className='lp-form-box__footer'>
            <Link to='/forgot-password'>Forgot your password?</Link>
          </p>
        </div>
      </div>

    </div>
  );
};

export default LoginPage;
