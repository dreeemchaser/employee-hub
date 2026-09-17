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
      // The API returns the same generic message whether or not the account
      // exists, so we show exactly what it tells us.
      const msg = await forgotPassword(email);
      setMessage(msg || 'If an account exists for that email, a password reset link has been sent.');
    } catch {
      setError('Something went wrong. Please try again.');
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
          <p className='lp-brand__tagline'>Reset your password</p>
        </div>
        <div className='lp-blob lp-blob--1' />
        <div className='lp-blob lp-blob--2' />
      </div>

      <div className='lp-form-panel'>
        <div className='lp-form-box'>
          <div className='lp-form-box__header'>
            <div className='lp-form-box__avatar'><i className='bi bi-key'></i></div>
            <h2>Forgot password</h2>
            <p>Enter your email and we'll send you a reset link</p>
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

            {message && (
              <div className='lp-error' style={{ background: '#e7f6ec', color: '#1a7f37' }}>
                <i className='bi bi-check-circle-fill'></i>
                <span>{message}</span>
              </div>
            )}
            {error && (
              <div className='lp-error'>
                <i className='bi bi-exclamation-circle-fill'></i>
                <span>{error}</span>
              </div>
            )}

            <button type='submit' className='lp-submit' disabled={loading}>
              {loading
                ? <><span className='lp-submit__spinner'></span>Sending…</>
                : <><i className='bi bi-send'></i>Send reset link</>
              }
            </button>
          </form>

          <p className='lp-form-box__footer'>
            <Link to='/login'>Back to sign in</Link>
          </p>
        </div>
      </div>
    </div>
  );
};

export default ForgotPasswordPage;
