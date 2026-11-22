/**
 * Tiny same-origin JSON client. The Spring Boot app uses a session cookie, so
 * every request sends credentials. Errors keep the HTTP status so callers can
 * distinguish "not logged in" (401) from a real server failure.
 */
let csrfTokenPromise = null;

function readCsrfCookie() {
  const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]*)/);
  return match ? decodeURIComponent(match[1]) : null;
}

/** Fetch the double-submit CSRF token once per page load. */
function ensureCsrfToken() {
  if (readCsrfCookie()) return Promise.resolve();
  if (!csrfTokenPromise) {
    csrfTokenPromise = fetch('/api/auth/csrf', { credentials: 'same-origin' })
      .then(response => {
        if (!response.ok) throw new Error('CSRF token unavailable');
        return response.json();
      })
      .then(data => {
        if (!data || !data.token) throw new Error('CSRF token missing');
      })
      .catch(error => {
        csrfTokenPromise = null;
        throw error;
      });
  }
  return csrfTokenPromise;
}

export async function apiFetch(path, options = {}) {
  const headers = { ...(options.headers || {}) };
  if (options.body && !headers['Content-Type']) {
    headers['Content-Type'] = 'application/json';
  }

  const method = (options.method || 'GET').toUpperCase();
  if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) {
    await ensureCsrfToken();
    const token = readCsrfCookie();
    if (token) headers['X-XSRF-TOKEN'] = token;
  }

  let response;
  try {
    response = await fetch(path, {
      credentials: 'same-origin',
      ...options,
      headers
    });
  } catch (cause) {
    const error = new Error(
      'Could not reach the RailSafe server. Make sure Spring Boot is running on port 8080, then reload this page.'
    );
    error.status = 0;
    error.cause = cause;
    throw error;
  }

  if (response.status === 401 && !path.startsWith('/api/auth/')) {
    // Session expired or was invalidated: never keep protected data on screen.
    window.location.href = '/login.html?next=' + encodeURIComponent('live/');
    const error = new Error('Your session has expired. Please log in again.');
    error.status = 401;
    throw error;
  }

  if (response.status === 204) {
    return null;
  }

  const text = await response.text();
  let data = null;
  if (text) {
    try {
      data = JSON.parse(text);
    } catch {
      data = text;
    }
  }

  if (!response.ok) {
    let message = (data && (data.message || data.error)) || `Request failed with HTTP ${response.status}`;
    if (data && data.fieldErrors) {
      const first = Object.values(data.fieldErrors)[0];
      if (first) message = first;
    }
    const error = new Error(message);
    error.status = response.status;
    error.data = data;
    throw error;
  }

  return data;
}

export async function getCurrentUser() {
  try {
    return await apiFetch('/api/auth/me');
  } catch (error) {
    if (error.status === 401) return null;
    throw error;
  }
}
