/* ==========================================================================
   Login-page UI polish only.
   All authentication logic, element IDs, form names and API calls remain in
   js/auth.js. This file adds the password toggle and small accessibility /
   loading-state hooks used by css/login.css.
   ========================================================================== */
(function () {
    'use strict';

    function init() {
        /* ---------- Show / hide password ---------- */
        const toggle = document.getElementById('password-toggle');
        const password = document.getElementById('login-password');

        if (toggle && password) {
            toggle.addEventListener('click', function () {
                const revealing = password.type === 'password';
                password.type = revealing ? 'text' : 'password';
                toggle.classList.toggle('is-visible', revealing);
                toggle.setAttribute('aria-pressed', String(revealing));
                toggle.setAttribute('aria-label', revealing ? 'Hide password' : 'Show password');
                toggle.title = revealing ? 'Hide password' : 'Show password';
                password.focus();
            });
        }

        /* ---------- Loading hook for the Log in button ----------
           js/auth.js disables the button while /api/auth/login is pending.
           Mirror that state as a class so CSS can style the pressed/loading
           look without changing the original submit handler. */
        const submit = document.getElementById('login-btn');
        if (submit && 'MutationObserver' in window) {
            const syncLoading = function () {
                submit.classList.toggle('is-loading', submit.disabled);
            };
            new MutationObserver(syncLoading).observe(submit, {
                attributes: true,
                attributeFilter: ['disabled']
            });
            syncLoading();
        }

        /* ---------- Announce inline errors to assistive tech ---------- */
        const alertBox = document.getElementById('auth-alert');
        if (alertBox && 'MutationObserver' in window) {
            const syncAlert = function () {
                const message = alertBox.querySelector('.alert-rs');
                if (message && !message.hasAttribute('role')) {
                    message.setAttribute('role', 'alert');
                }
            };
            new MutationObserver(syncAlert).observe(alertBox, {
                childList: true,
                subtree: true
            });
            syncAlert();
        }
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();
