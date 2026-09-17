import axios from 'axios';

const BASE_URL = process.env.REACT_APP_API_URL || 'http://localhost:8080';

const ACCESS_KEY = 'token';          // kept as 'token' so existing getToken() callers are unaffected
const REFRESH_KEY = 'refreshToken';

export async function login(email, password) {
    try {
        const res = await axios.post(`${BASE_URL}/auth/login`, { email, password });
        storeTokens(res.data.data);
        return getToken();
    } catch (err) {
        throw toLoginError(err);
    }
}

// Persist the access + refresh token pair returned by login/refresh.
function storeTokens(data) {
    localStorage.setItem(ACCESS_KEY, data.accessToken);
    if (data.refreshToken) localStorage.setItem(REFRESH_KEY, data.refreshToken);
}

// Build a user-facing Error from an auth failure. A locked account (HTTP 423)
// surfaces the backend's explanatory message and carries `retryAfterSeconds` so
// the UI can show a countdown; anything else stays generic so we don't reveal
// whether the email exists.
function toLoginError(err) {
    const status = err.response?.status;
    if (status === 423) {
        const error = new Error(
            err.response?.data?.message
            || 'Your account is temporarily locked due to repeated failed sign-in attempts. Please try again later.'
        );
        error.retryAfterSeconds = lockRetrySeconds(err);
        return error;
    }
    return new Error('Incorrect email or password. Please try again.');
}

// Prefer the retryAfterSeconds field in the body; fall back to the Retry-After
// header. Returns 0 when neither is present or parseable.
function lockRetrySeconds(err) {
    const fromBody = err.response?.data?.data?.retryAfterSeconds;
    if (Number.isFinite(fromBody)) return fromBody;
    const fromHeader = Number(err.response?.headers?.['retry-after']);
    return Number.isFinite(fromHeader) ? fromHeader : 0;
}

// Request a password reset email for the given address. Resolves with the
// server's generic message regardless of whether the account exists.
export async function forgotPassword(email) {
    const res = await axios.post(`${BASE_URL}/auth/forgot-password`, { email });
    return res.data.message;
}

// Complete a password reset using the emailed token.
export async function resetPassword(token, newPassword) {
    const res = await axios.post(`${BASE_URL}/auth/reset-password`, { token, newPassword });
    return res.data.message;
}

// Revoke the refresh token server-side (best-effort) and clear local tokens.
export async function logout() {
    const refreshToken = getRefreshToken();
    if (refreshToken) {
        try {
            await axios.post(`${BASE_URL}/auth/logout`, { refreshToken });
        } catch {
            // Best-effort: even if revoke fails, clear locally below.
        }
    }
    clearTokens();
}

function clearTokens() {
    localStorage.removeItem(ACCESS_KEY);
    localStorage.removeItem(REFRESH_KEY);
}

export function getToken() {
    return localStorage.getItem(ACCESS_KEY);
}

export function getRefreshToken() {
    return localStorage.getItem(REFRESH_KEY);
}

// ── Silent refresh ───────────────────────────────────────────────────────────

// Shared in-flight refresh so concurrent 401s trigger only one refresh call.
let refreshPromise = null;

// Exchange the stored refresh token for a new access + refresh pair. Returns the
// new access token, or throws if refresh is not possible (caller routes to login).
function refresh() {
    if (refreshPromise) return refreshPromise;

    const refreshToken = getRefreshToken();
    if (!refreshToken) return Promise.reject(new Error('No refresh token'));

    refreshPromise = axios
        .post(`${BASE_URL}/auth/refresh`, { refreshToken })
        .then((res) => {
            storeTokens(res.data.data);
            return getToken();
        })
        .finally(() => {
            refreshPromise = null;
        });

    return refreshPromise;
}

// Register once at module load: on any 401 (other than the auth endpoints
// themselves), attempt a single silent refresh and retry the original request
// once. If refresh fails, clear tokens and send the user to login.
axios.interceptors.response.use(
    (response) => response,
    async (error) => {
        const original = error.config;
        const status = error.response?.status;
        const url = original?.url ?? '';
        const isAuthCall = url.includes('/auth/login')
            || url.includes('/auth/refresh')
            || url.includes('/auth/logout');

        if (status === 401 && original && !original._retried && !isAuthCall) {
            original._retried = true;
            try {
                const newToken = await refresh();
                original.headers = { ...original.headers, Authorization: `Bearer ${newToken}` };
                return axios(original);
            } catch (refreshErr) {
                clearTokens();
                if (typeof window !== 'undefined') window.location.assign('/login');
                return Promise.reject(refreshErr);
            }
        }
        return Promise.reject(error);
    }
);

export function isLoggedIn() {
    return !!getToken();
}

export function getRole() {
    const token = getToken();
    if (!token) return null;
    try {
        const payload = JSON.parse(atob(token.split('.')[1]));
        return payload.role ?? null;
    } catch {
        return null;
    }
}

export function isHrOrAdmin() {
    const role = getRole();
    return role === 'HR_ADMIN' || role === 'SUPER_ADMIN' || role === 'PAYROLL_ADMIN';
}

export function isManager() {
    return getRole() === 'MANAGER';
}
