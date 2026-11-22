document.addEventListener('DOMContentLoaded', async () => {
    await RailSafeUI.initShell(null);
    initLoginForm();
    initRegisterForm();
    showRegistrationSuccess();
    loadDemoAccounts();
});

function showFormAlert(message, type = 'danger') {
    const container = document.getElementById('auth-alert');
    RailSafeUI.inlineAlert(container, type, RailSafeUI.escapeHtml(message));
    container.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
}

function setButtonLoading(button, loading, label) {
    if (!button) return;
    if (loading) {
        button.dataset.original = button.innerHTML;
        button.disabled = true;
        button.innerHTML = `<span class="spinner-border spinner-border-sm me-2"></span>${label}`;
    } else {
        button.disabled = false;
        if (button.dataset.original) button.innerHTML = button.dataset.original;
    }
}

function safeNextPage(next) {
    if (!next) return null;
    let decoded;
    try {
        decoded = decodeURIComponent(next);
    } catch {
        return null;
    }
    // Only same-site pages are accepted, including the built React dashboard at live/.
    return /^(?:[a-z0-9_-]+\.html|live\/|live\/[a-z0-9_-]+\.html)(\?[^#]*)?$/i.test(decoded)
        ? decoded : null;
}

function destinationForRole(role) {
    switch (role) {
        case 'SUPER_ADMIN': return 'super-admin.html';
        case 'ADMIN': return 'admin.html';
        case 'STAFF': return 'staff.html';
        case 'CUSTOMER':
        case 'USER':
        default: return 'dashboard.html';
    }
}

/* ------------------------------------------------------------------ */
/* Demo accounts (public only in h2/dev)                               */
/* ------------------------------------------------------------------ */

// STAFF credentials are shown only on the dedicated staff-login.html page.
const DEMO_DISPLAY_ROLES = ['CUSTOMER', 'ADMIN', 'SUPER_ADMIN'];

async function loadDemoAccounts() {
    const container = document.getElementById('demo-account-cards');
    if (!container) return;

    let accounts = [];
    try {
        const fetched = await RailSafeAPI.apiFetch('/api/auth/demo-accounts');
        if (Array.isArray(fetched)) accounts = fetched;
    } catch (error) {
        console.warn('Demo accounts could not be loaded.', error);
    }

    const visible = accounts.filter(account => DEMO_DISPLAY_ROLES.includes(account.role));
    if (!visible.length) {
        container.innerHTML = '<div class="col-12 text-muted-rs small">' +
            'No demo accounts are available on this server.</div>';
        return;
    }

    container.innerHTML = visible.map((account, index) => `
        <div class="col-12 col-sm-6">
            <div class="demo-account-card h-100">
                <div class="d-flex flex-wrap align-items-center justify-content-between gap-2">
                    <span class="demo-role">${RailSafeUI.escapeHtml(account.role)}</span>
                    <span class="badge-soft">${RailSafeUI.escapeHtml(account.label || account.role)}</span>
                </div>
                ${account.username && account.username !== account.email
                    ? `<div class="demo-credential"><span>User</span><code>${RailSafeUI.escapeHtml(account.username)}</code></div>`
                    : ''}
                <div class="demo-credential">
                    <span>Email</span><code>${RailSafeUI.escapeHtml(account.email || account.username || '')}</code>
                </div>
                <div class="demo-credential">
                    <span>Password</span><code>${RailSafeUI.escapeHtml(account.password || '')}</code>
                </div>
                <p class="demo-description">${RailSafeUI.escapeHtml(
                    account.description || 'Demo sign-in for this role.')}</p>
                <button class="btn btn-sm btn-outline-navy w-100 demo-use" type="button"
                        data-demo-index="${index}">
                    <i class="bi bi-box-arrow-in-right me-1"></i>Use this demo account
                </button>
            </div>
        </div>
    `).join('');

    container.querySelectorAll('[data-demo-index]').forEach(button => {
        button.addEventListener('click', () => useDemoAccount(visible[Number(button.dataset.demoIndex)]));
    });
}

function useDemoAccount(account) {
    if (!account) return;
    const email = document.getElementById('login-email');
    const password = document.getElementById('login-password');
    if (!email || !password) return;
    email.value = account.email || account.username || '';
    password.value = account.password || '';
    RailSafeUI.clearAlert(document.getElementById('auth-alert'));

    const form = document.getElementById('login-form');
    if (typeof form.requestSubmit === 'function') {
        form.requestSubmit();
    } else {
        form.dispatchEvent(new Event('submit', { cancelable: true, bubbles: true }));
    }
}

function initLoginForm() {
    const form = document.getElementById('login-form');
    if (!form) return;

    form.addEventListener('submit', async event => {
        event.preventDefault();
        RailSafeUI.clearAlert(document.getElementById('auth-alert'));

        const email = document.getElementById('login-email').value.trim();
        const password = document.getElementById('login-password').value;
        if (!email || !password) {
            showFormAlert('Enter both your email and password.');
            return;
        }

        const button = document.getElementById('login-btn');
        setButtonLoading(button, true, 'Logging in…');
        try {
            const user = await RailSafeAPI.apiFetch('/api/auth/login', {
                method: 'POST',
                body: JSON.stringify({ email, password })
            });
            RailSafeUI.toast(`Welcome back, ${user.fullName}.`, 'success');

            const next = safeNextPage(new URLSearchParams(window.location.search).get('next'));
            const destination = next || destinationForRole(user.role);
            setTimeout(() => { window.location.href = destination; }, 350);
        } catch (error) {
            showFormAlert(error.message);
            RailSafeUI.toast(error.message, 'danger');
        } finally {
            setButtonLoading(button, false);
        }
    });
}

function showRegistrationSuccess() {
    const params = new URLSearchParams(window.location.search);
    if (params.get('registered') === '1') {
        showFormAlert('Account created successfully. You can log in now.', 'success');
    }
}

function initRegisterForm() {
    const form = document.getElementById('register-form');
    if (!form) return;

    form.addEventListener('submit', async event => {
        event.preventDefault();
        RailSafeUI.clearAlert(document.getElementById('auth-alert'));

        const fullName = document.getElementById('register-name').value.trim();
        const email = document.getElementById('register-email').value.trim();
        const phone = document.getElementById('register-phone').value.trim();
        const password = document.getElementById('register-password').value;
        const confirmPassword = document.getElementById('register-confirm').value;

        if (!fullName || !email || !password) {
            showFormAlert('Full name, email and password are required.');
            return;
        }
        if (password.length < 8) {
            showFormAlert('Password must contain at least 8 characters.');
            return;
        }
        if (password !== confirmPassword) {
            showFormAlert('Passwords do not match.');
            return;
        }

        const button = document.getElementById('register-btn');
        setButtonLoading(button, true, 'Creating account…');
        try {
            await RailSafeAPI.apiFetch('/api/auth/register', {
                method: 'POST',
                body: JSON.stringify({ fullName, email, phone: phone || null, password })
            });
            showFormAlert('Account created. Redirecting to the login page…', 'success');
            setTimeout(() => { window.location.href = 'login.html?registered=1'; }, 1200);
        } catch (error) {
            showFormAlert(error.message);
            RailSafeUI.toast(error.message, 'danger');
        } finally {
            setButtonLoading(button, false);
        }
    });
}
