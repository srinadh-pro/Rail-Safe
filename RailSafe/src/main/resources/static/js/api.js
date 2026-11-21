/* Shared JSON API client. All requests use the same-origin session cookie. */
(function () {
    'use strict';

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

    async function apiFetch(path, options = {}) {
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
        } catch (networkError) {
            const error = new Error('Could not reach the RailSafe server. Is Spring Boot running?');
            error.status = 0;
            error.cause = networkError;
            throw error;
        }

        if (response.status === 401 && !path.startsWith('/api/auth/')) {
            // The session has expired or was invalidated. Send the browser back to
            // the login page instead of leaving protected data on screen.
            redirectToLogin();
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
            let message = (data && (data.message || data.error)) ||
                `Request failed with HTTP ${response.status}`;
            if (data && data.fieldErrors) {
                const firstFieldError = Object.values(data.fieldErrors)[0];
                if (firstFieldError) {
                    message = firstFieldError;
                }
            }
            const error = new Error(message);
            error.status = response.status;
            error.data = data;
            throw error;
        }

        return data;
    }

    async function getCurrentUser() {
        try {
            return await apiFetch('/api/auth/me');
        } catch (error) {
            if (error.status === 401) {
                return null;
            }
            throw error;
        }
    }

    async function logout() {
        try {
            await apiFetch('/api/auth/logout', { method: 'POST' });
        } catch (error) {
            console.warn('Logout request failed:', error);
        } finally {
            window.location.href = 'login.html';
        }
    }

    function redirectToLogin(next) {
        const target = next || (window.location.pathname.split('/').pop() + window.location.search);
        window.location.href = 'login.html?next=' + encodeURIComponent(target);
    }

    window.RailSafeAPI = { apiFetch, getCurrentUser, logout, redirectToLogin };
})();
