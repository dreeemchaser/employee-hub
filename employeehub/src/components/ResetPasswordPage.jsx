import { useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { resetPassword } from '../api/AuthService';

const ResetPasswordPage = () => {
  const [params]              = useSearchParams();
  const token                 = params.get('token') || '';
  const [password, setPassword]   = useState('');
  const [confirm, setConfirm]     = useState('');
  const [showPw, setShowPw]       = useState(false);
  const [error, setError]         = useState('');
  const [done, setDone]           = useState(false);
  const [loading, setLoading]     = useState(false);

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
    <div className='lp-shell'>
      <div className='lp-brand'>
        <div className='lp-brand__inner'>
          <div className='lp-brand__logo'><i className='bi bi-building-fill'></i></div>
          <h1 className='lp-brand__name'>Employee Hub</h1>
          <p className='lp-brand__tagline'>Choose a new password</p>
        </div>
        <div className='lp-blob lp-blob--1' />
        <div className='lp-blob lp-blob--2' />
      </div>

      <div className='lp-form-panel'>
        <div className='lp-form-box'>
          <div className='lp-form-box__header'>
            <div className='lp-form-box__avatar'><i className='bi bi-shield-lock'></i></div>
            <h2>Reset password</h2>
            <p>Enter a new password for your account</p>
          </div>

          {done ? (
            <>
              <div className='lp-error' style={{ background: '#e7f6ec', color: '#1a7f37' }}>
                <i className='bi bi-check-circle-fill'></i>
                <span>Your password has been reset. You can now sign in.</span>
              </div>
              <p className='lp-form-box__footer'>
                <Link to='/login'>Go to sign in</Link>
              </p>
            </>
          ) : !token ? (
            <>
              <div className='lp-error'>
                <i className='bi bi-exclamation-circle-fill'></i>
                <span>This reset link is missing its token. Please use the link from your email.</span>
              </div>
              <p className='lp-form-box__footer'>
                <Link to='/forgot-password'>Request a new link</Link>
              </p>
            </>
          ) : (
            <form onSubmit={handleSubmit} className='lp-form'>
              <div className='lp-field'>
                <label>New password</label>
                <div className='lp-field__wrap'>
                  <i className='bi bi-lock lp-field__icon'></i>
                  <input
                    type={showPw ? 'text' : 'password'}
                    placeholder='••••••••'
                    value={password}
                    onChange={e => setPassword(e.target.value)}
                    required
                    autoFocus
                    autoComplete='new-password'
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

              <div className='lp-field'>
                <label>Confirm new password</label>
                <div className='lp-field__wrap'>
                  <i className='bi bi-lock lp-field__icon'></i>
                  <input
                    type={showPw ? 'text' : 'password'}
                    placeholder='••••••••'
                    value={confirm}
                    onChange={e => setConfirm(e.target.value)}
                    required
                    autoComplete='new-password'
                  />
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
                  ? <><span className='lp-submit__spinner'></span>Resetting…</>
                  : <><i className='bi bi-check2-circle'></i>Reset password</>
                }
              </button>
            </form>
          )}
        </div>
      </div>
    </div>
  );
};

export default ResetPasswordPage;
