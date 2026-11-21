/* Dedicated railway gate staff sign-in. STAFF role is enforced by the server. */
/* eslint-disable no-unused-vars */

document.addEventListener('DOMContentLoaded', async () => {
    const user = await RailSafeUI.initShell(null);
    if (user && user.role === 'STAFF') {
        window.location.href = 'staff.html';
        return;
    }
    initStaffLoginForm();
    await loadStaffDemoAccount();
});

function staffAlert(message, type = 'danger') {
    const container = document.getElementById('staff-auth-alert');
    RailSafeUI.inlineAlert(container, type, RailSafeUI.escapeHtml(message));
    container.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
}

function setStaffButtonLoading(button, loading, label) {
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

function safeStaffNext(value) {
    if (!value) return 'staff.html';
    let decoded;
    try {
        decoded = decodeURIComponent(value);
    } catch {
        return 'staff.html';
    }
    return /^staff\.html(\?[^#]*)?$/i.test(decoded) ? decoded : 'staff.html';
}

function initStaffLoginForm() {
    const form = document.getElementById('staff-login-form');
    if (!form) return;
    form.addEventListener('submit', async event => {
        event.preventDefault();
        RailSafeUI.clearAlert(document.getElementById('staff-auth-alert'));

        const email = document.getElementById('staff-login-email').value.trim();
        const password = document.getElementById('staff-login-password').value;
        if (!email || !password) {
            staffAlert('Enter both your staff email and password.');
            return;
        }

        const button = document.getElementById('staff-login-btn');
        setStaffButtonLoading(button, true, 'Signing in…');
        try {
            const user = await RailSafeAPI.apiFetch('/api/auth/staff-login', {
                method: 'POST',
                body: JSON.stringify({ email, password })
            });
            if (user.role !== 'STAFF') {
                throw new Error('This account is not authorised for the staff console.');
            }
            RailSafeUI.toast(`Welcome, ${user.fullName}.`, 'success');
            const next = safeStaffNext(new URLSearchParams(window.location.search).get('next'));
            setTimeout(() => { window.location.href = next; }, 350);
        } catch (error) {
            staffAlert(error.message);
            RailSafeUI.toast(error.message, 'danger');
        } finally {
            setStaffButtonLoading(button, false);
        }
    });
}

async function loadStaffDemoAccount() {
    const container = document.getElementById('staff-demo-card');
    if (!container) return;
    let staff = null;
    try {
        const accounts = await RailSafeAPI.apiFetch('/api/auth/demo-accounts');
        if (Array.isArray(accounts)) {
            staff = accounts.find(account => account.role === 'STAFF') || null;
        }
    } catch (error) {
        console.warn('Staff demo account could not be loaded.', error);
    }

    if (!staff) {
        container.innerHTML = '<div class="col-12 text-muted-rs small">' +
            'No demo accounts are available on this server.</div>';
        return;
    }

    container.innerHTML = `
        <div class="demo-account-card h-100">
            <div class="d-flex flex-wrap align-items-center justify-content-between gap-2">
                <span class="demo-role">STAFF</span>
                <span class="badge-soft">${RailSafeUI.escapeHtml(staff.label || 'Railway staff')}</span>
            </div>
            <div class="demo-credential"><span>Email</span><code>${RailSafeUI.escapeHtml(
                staff.email || staff.username || '')}</code></div>
            <div class="demo-credential"><span>Password</span><code>${RailSafeUI.escapeHtml(
                staff.password || '')}</code></div>
            <p class="demo-description">${RailSafeUI.escapeHtml(
                staff.description || 'Assigned crossings only.')}</p>
            <button class="btn btn-sm btn-outline-navy w-100" type="button" id="use-staff-demo">
                <i class="bi bi-box-arrow-in-right me-1"></i>Use staff demo account
            </button>
        </div>`;

    const button = document.getElementById('use-staff-demo');
    if (button) {
        button.addEventListener('click', () => {
            document.getElementById('staff-login-email').value = staff.email || staff.username || '';
            document.getElementById('staff-login-password').value = staff.password || '';
            RailSafeUI.clearAlert(document.getElementById('staff-auth-alert'));
            document.getElementById('staff-login-form').requestSubmit();
        });
    }
}
