import { useState } from 'react';
import { login } from '../api/AuthService';

const LoginPage = ({ onLogin }) => {
  const [email, setEmail]       = useState('');
  const [password, setPassword] = useState('');
  const [showPw, setShowPw]     = useState(false);
  const [error, setError]       = useState('');
  const [loading, setLoading]   = useState(false);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setLoading(true);
    setError('');
    try {
      await login(email, password);
      onLogin();
    } catch {
      setError('Incorrect email or password. Please try again.');
    } finally {
      setLoading(false);
    }
  };

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
                <span>{error}</span>
              </div>
            )}

            <button type='submit' className='lp-submit' disabled={loading}>
              {loading
                ? <><span className='lp-submit__spinner'></span>Signing in…</>
                : <><i className='bi bi-box-arrow-in-right'></i>Sign In</>
              }
            </button>

          </form>

          <p className='lp-form-box__footer'>
            Contact HR if you need access or have forgotten your password.
          </p>
        </div>
      </div>

    </div>
  );
};

export default LoginPage;
