/* Shared UI helpers: navbar, footer, badges, toasts and formatting. */
(function () {
    'use strict';

    const STATUS_CLASS = {
        OPEN: 'status-open',
        CLOSED: 'status-closed',
        CLOSING: 'status-closing',
        OPENING: 'status-opening',
        MAINTENANCE: 'status-maintenance',
        UNKNOWN: 'status-unknown',
        AWAITING_VERIFICATION: 'status-awaiting'
    };

    const STATUS_LABEL = {
        OPEN: 'OPEN',
        CLOSED: 'CLOSED',
        CLOSING: 'CLOSING',
        OPENING: 'OPENING',
        MAINTENANCE: 'MAINTENANCE',
        UNKNOWN: 'UNVERIFIED / AWAITING VERIFICATION',
        AWAITING_VERIFICATION: 'UNVERIFIED / AWAITING VERIFICATION'
    };

    function escapeHtml(value) {
        if (value === null || value === undefined) return '';
        return String(value)
            .replaceAll('&', '&amp;')
            .replaceAll('<', '&lt;')
            .replaceAll('>', '&gt;')
            .replaceAll('"', '&quot;')
            .replaceAll("'", '&#039;');
    }

    function statusBadge(status) {
        const safeStatus = Object.prototype.hasOwnProperty.call(STATUS_CLASS, status) ? status : 'AWAITING_VERIFICATION';
        return `<span class="status-badge ${STATUS_CLASS[safeStatus]}"><span class="dot"></span>${STATUS_LABEL[safeStatus]}</span>`;
    }

    function statusClass(status) {
        const safeStatus = Object.prototype.hasOwnProperty.call(STATUS_CLASS, status) ? status : 'AWAITING_VERIFICATION';
        return 'gate-' + safeStatus.toLowerCase();
    }

    function verificationBadge(verification) {
        const value = verification || 'UNVERIFIED';
        const verified = value === 'VERIFIED';
        const label = verified ? 'STAFF VERIFIED' : 'UNVERIFIED';
        const icon = verified ? 'bi-patch-check-fill' : 'bi-question-circle';
        const cssClass = verified ? 'verify-verified' : 'verify-unverified';
        return `<span class="verify-badge ${cssClass}"><i class="bi ${icon}"></i>${label}</span>`;
    }

    function sourceLabel(source) {
        switch (source) {
            case 'STAFF': return 'Authorised staff';
            case 'SENSOR': return 'Authorised sensor';
            case 'REFERENCE': return 'Static reference record';
            case 'DEMO': return 'Unverified report';
            default: return 'Status unavailable';
        }
    }

    function formatDateTime(value) {
        if (!value) return 'Not available';
        const date = new Date(value);
        if (Number.isNaN(date.getTime())) return String(value);
        return date.toLocaleString(undefined, {
            day: '2-digit', month: 'short', year: 'numeric',
            hour: '2-digit', minute: '2-digit'
        });
    }

    function timeAgo(value) {
        if (!value) return 'not available';
        const date = new Date(value);
        if (Number.isNaN(date.getTime())) return String(value);
        const seconds = Math.round((date.getTime() - Date.now()) / 1000);
        const formatter = new Intl.RelativeTimeFormat(undefined, { numeric: 'auto' });
        const ranges = [
            ['year', 31536000], ['month', 2592000], ['week', 604800],
            ['day', 86400], ['hour', 3600], ['minute', 60]
        ];
        for (const [unit, size] of ranges) {
            if (Math.abs(seconds) >= size) {
                return formatter.format(Math.round(seconds / size), unit);
            }
        }
        return formatter.format(seconds, 'second');
    }

    function initials(name) {
        return (name || 'U').trim().split(/\s+/).slice(0, 2)
            .map(part => part.charAt(0).toUpperCase()).join('');
    }

    /* ---------- Additive helpers for detail / live-data views ---------- */
    const NOT_AVAILABLE = 'Not Available';
    const LIVE_UNAVAILABLE = 'Live data unavailable';

    function notAvailable(value, fallback = NOT_AVAILABLE) {
        if (value === null || value === undefined) return fallback;
        if (typeof value === 'string' && !value.trim()) return fallback;
        return value;
    }

    function awaitingBadge() {
        return '<span class="badge-awaiting"><i class="bi bi-hourglass-split me-1"></i>' +
            'UNVERIFIED / AWAITING VERIFICATION</span>';
    }

    function renderLiveIndicator(container, counts) {
        if (!container) return;
        const available = counts && counts.liveDataAvailable === true;
        container.classList.toggle('live-unavailable', !available);
        if (available) {
            container.innerHTML = '<span class="badge-live"><span class="live-dot"></span>LIVE</span>';
        } else {
            // The exact required copy when no verified live feed is available.
            container.textContent = LIVE_UNAVAILABLE;
        }
    }

    /**
     * Shows when the server last produced a count snapshot. This is never used to
     * claim a live railway feed; renderLiveIndicator still controls the LIVE badge.
     */
    function renderSyncTime(container, counts) {
        if (!container) return;
        const value = counts && (counts.generatedAt || counts.lastSuccessfulUpdate);
        container.textContent = value
            ? `Last synchronised: ${formatDateTime(value)}`
            : 'Last synchronised: Not Available';
    }

    function formatDuration(seconds) {
        const total = Number.isFinite(Number(seconds)) ? Math.max(0, Math.floor(Number(seconds))) : 0;
        const days = Math.floor(total / 86400);
        const hours = Math.floor((total % 86400) / 3600);
        const minutes = Math.floor((total % 3600) / 60);
        const secs = total % 60;
        const parts = [];
        if (days) parts.push(`${days}d`);
        if (hours) parts.push(`${hours}h`);
        if (minutes) parts.push(`${minutes}m`);
        parts.push(`${secs}s`);
        return parts.join(' ');
    }

    /**
     * Opens the shared SSE feed once per page. Both `counts` and `gate-status`
     * events trigger a refresh; the browser reconnects automatically on error.
     */
    function startLiveStream(onUpdate) {
        if (typeof EventSource === 'undefined') return null;
        try {
            const source = new EventSource('/api/live/stream');
            const refresh = () => { if (typeof onUpdate === 'function') onUpdate(); };
            source.addEventListener('counts', refresh);
            source.addEventListener('gate-status', refresh);
            source.addEventListener('connected', () => {});
            source.addEventListener('heartbeat', () => {});
            return source;
        } catch (error) {
            console.warn('Live stream could not start:', error);
            return null;
        }
    }

    function toast(message, type = 'success') {
        let root = document.getElementById('toast-root');
        if (!root) {
            root = document.createElement('div');
            root.id = 'toast-root';
            root.className = 'toast-container position-fixed bottom-0 end-0 p-3';
            document.body.appendChild(root);
        }
        const icon = type === 'danger' ? 'bi-x-circle-fill'
            : type === 'warning' ? 'bi-exclamation-triangle-fill'
                : type === 'info' ? 'bi-info-circle-fill' : 'bi-check-circle-fill';
        const color = type === 'danger' ? 'text-danger'
            : type === 'warning' ? 'text-warning'
                : type === 'info' ? 'text-primary' : 'text-success';

        const element = document.createElement('div');
        element.className = 'toast rs-toast';
        element.setAttribute('role', 'alert');
        element.setAttribute('aria-live', 'assertive');
        element.setAttribute('aria-atomic', 'true');
        element.innerHTML = `
            <div class="toast-body">
                <i class="bi ${icon} ${color} fs-5"></i>
                <div class="flex-grow-1">${escapeHtml(message)}</div>
                <button type="button" class="btn-close ms-1" data-bs-dismiss="toast" aria-label="Close"></button>
            </div>`;
        root.appendChild(element);
        const instance = new bootstrap.Toast(element, { delay: 4500 });
        element.addEventListener('hidden.bs.toast', () => element.remove());
        instance.show();
    }

    function loading(container, message = 'Loading…') {
        if (!container) return;
        container.innerHTML = `<div class="page-loading">
            <span class="spinner-border spinner-border-sm text-primary" role="status"></span>
            <span>${escapeHtml(message)}</span>
        </div>`;
    }

    function emptyState(container, icon, title, detail) {
        if (!container) return;
        container.innerHTML = `<div class="empty-state">
            <i class="bi ${icon}"></i>
            <p class="fw-bold text-dark mb-1">${escapeHtml(title)}</p>
            <p>${escapeHtml(detail || '')}</p>
        </div>`;
    }

    function inlineAlert(container, type, html) {
        if (!container) return;
        const cls = type === 'danger' ? 'alert-rs-danger'
            : type === 'success' ? 'alert-rs-success'
                : type === 'warning' ? 'alert-rs-warning' : 'alert-rs-info';
        container.innerHTML = `<div class="alert-rs ${cls}">${html}</div>`;
        container.classList.remove('hidden');
    }

    function clearAlert(container) {
        if (!container) return;
        container.innerHTML = '';
        container.classList.add('hidden');
    }

    const PAGE_TITLES = {
        home: 'Overview',
        map: 'Gate map & routes',
        live: 'Live monitoring',
        dashboard: 'Commuter dashboard',
        notifications: 'Alert centre',
        admin: 'Operations dashboard',
        staff: 'Staff dashboard',
        'super-admin': 'Platform administration'
    };

    function sidebarHtml(active, user) {
        const isAdminArea = user && (user.role === 'ADMIN' || user.role === 'SUPER_ADMIN');
        const isStaffArea = user && user.role === 'STAFF';
        const isSuperAdmin = user && user.role === 'SUPER_ADMIN';
        const navItem = (key, href, label, icon) => `
            <a class="nav-link${active === key ? ' active' : ''}" href="${href}"
               ${active === key ? 'aria-current="page"' : ''}>
                <i class="bi ${icon}"></i><span>${label}</span>
            </a>`;

        return `<aside class="rs-sidebar" aria-label="Primary navigation">
            <a class="rs-sidebar-brand" href="index.html">
                <span class="brand-mark"><i class="bi bi-shield-check"></i></span>
                <span>RailSafe<small>Railway safety platform</small></span>
            </a>
            <div class="rs-sidebar-section">Workspace</div>
            <nav class="rs-sidebar-nav">
                ${navItem('home', 'index.html', 'Overview', 'bi-house')}
                ${navItem('map', 'map.html', 'Gate map', 'bi-map')}
                ${navItem('live', 'live/', 'Live monitoring', 'bi-broadcast')}
                ${user ? navItem('dashboard', 'dashboard.html', 'Dashboard', 'bi-grid') : ''}
                ${user ? navItem('notifications', 'notifications.html', 'Alerts', 'bi-bell') : ''}
            </nav>
            ${isAdminArea ? `
                <div class="rs-sidebar-section">Administration</div>
                <nav class="rs-sidebar-nav">
                    ${navItem('admin', 'admin.html', 'Operations', 'bi-shield-lock')}
                    ${isSuperAdmin ? navItem('super-admin', 'super-admin.html', 'Super admin', 'bi-shield-check') : ''}
                </nav>` : ''}
            ${isStaffArea ? `
                <div class="rs-sidebar-section">Staff workspace</div>
                <nav class="rs-sidebar-nav">
                    ${navItem('staff', 'staff.html', 'Staff console', 'bi-person-badge')}
                </nav>` : ''}
            ${user ? `
                <div class="rs-sidebar-user">
                    <span class="nav-avatar">${escapeHtml(initials(user.fullName))}</span>
                    <div>
                        <div class="fw-semibold small">${escapeHtml(user.fullName)}</div>
                        <small>${escapeHtml(user.email)}</small>
                        <small>${escapeHtml(user.role)}</small>
                    </div>
                </div>` : ''}
        </aside>
        <div class="sidebar-backdrop" id="sidebar-backdrop" aria-hidden="true"></div>`;
    }

    function topbarHtml(active, user) {
        const title = PAGE_TITLES[active] || 'RailSafe';
        const authArea = user ? `
            <div class="dropdown">
                <button class="nav-user-pill dropdown-toggle border-0" data-bs-toggle="dropdown" aria-expanded="false">
                    <span class="nav-avatar">${escapeHtml(initials(user.fullName))}</span>
                    <span class="d-none d-md-inline">${escapeHtml(user.fullName)}</span>
                </button>
                <ul class="dropdown-menu dropdown-menu-end">
                    <li><span class="dropdown-item-text small text-muted">
                        ${escapeHtml(user.email)} · ${escapeHtml(user.role)}</span></li>
                    <li><hr class="dropdown-divider"></li>
                    <li><a class="dropdown-item" href="dashboard.html"><i class="bi bi-grid me-2"></i>Dashboard</a></li>
                    <li><a class="dropdown-item" href="notifications.html"><i class="bi bi-bell me-2"></i>Notifications</a></li>
                    <li><hr class="dropdown-divider"></li>
                    <li><button class="dropdown-item text-danger" id="nav-logout" type="button">
                        <i class="bi bi-box-arrow-right me-2"></i>Log out</button></li>
                </ul>
            </div>` : `
            <div class="d-flex align-items-center gap-2">
                <a class="btn btn-sm btn-outline-navy" href="staff-login.html">Staff sign in</a>
                <a class="btn btn-sm btn-outline-navy" href="login.html">Log in</a>
                <a class="btn btn-sm btn-primary" href="register.html">Register</a>
            </div>`;

        return `<header class="rs-topbar">
            <button class="sidebar-toggle" id="sidebar-toggle" type="button"
                    aria-label="Toggle navigation" aria-expanded="false">
                <i class="bi bi-list"></i>
            </button>
            <div class="rs-topbar-title">
                <strong>${escapeHtml(title)}</strong>
                <small>RailSafe operations console</small>
            </div>
            <div class="rs-topbar-actions">${authArea}</div>
        </header>`;
    }

    function footerHtml() {
        return `<footer class="rs-footer">
            <div class="container">
                <div class="row g-4">
                    <div class="col-lg-5">
                        <h5 class="d-flex align-items-center gap-2">
                            <span class="brand-mark" style="width:30px;height:30px;font-size:.9rem;">
                                <i class="bi bi-shield-check"></i></span>
                            RailSafe
                        </h5>
                        <p class="mb-2">Smart railway gate alerts and route assistance.
                            Know what has been reported before you reach a crossing.</p>
                        <span class="badge-soft"><i class="bi bi-exclamation-triangle me-1"></i>NOT A LIVE RAILWAY FEED</span>
                    </div>
                    <div class="col-6 col-lg-2">
                        <h5>Explore</h5>
                        <a href="index.html">Home</a>
                        <a href="map.html">Gate map</a>
                        <a href="live/">Live dashboard</a>
                        <a href="dashboard.html">Dashboard</a>
                    </div>
                    <div class="col-6 col-lg-2">
                        <h5>Account</h5>
                        <a href="login.html">Log in</a>
                        <a href="staff-login.html">Staff sign in</a>
                        <a href="register.html">Register</a>
                        <a href="notifications.html">Notifications</a>
                    </div>
                    <div class="col-lg-3">
                        <h5>Safety first</h5>
                        <p class="mb-0" style="font-size:.8rem;line-height:1.65;">
                            Never use this app to bypass a closed barrier. Obey railway signals,
                            gates and authorised staff at all times.
                        </p>
                    </div>
                </div>
                <div class="footer-note">
                    <strong>Important:</strong> RailSafe has no live Indian Railways feed. It presents reported
                    and verified crossing information. Estimates are advisory only. Unverified reports may be
                    stale. Do not make safety-critical travel decisions based only on this application.
                    Always obey physical barriers, signals and railway staff.
                </div>
            </div>
        </footer>`;
    }

    async function initShell(active) {
        const params = new URLSearchParams(window.location.search);
        const authPage = document.querySelector('main.auth-page');
        // Public auth pages have no session; avoid a needless 401 request there.
        let user = null;
        if (!authPage) {
            try {
                user = await RailSafeAPI.getCurrentUser();
            } catch (error) {
                console.warn('Could not load current user:', error);
            }
        }

        if (params.get('denied') === '1') {
            toast('You do not have permission to open that page.', 'danger');
            params.delete('denied');
            const query = params.toString();
            window.history.replaceState(null, '', window.location.pathname + (query ? '?' + query : ''));
        }

        const nav = document.getElementById('app-nav');

        if (!authPage) {
            document.body.classList.add('rs-has-sidebar');
            document.body.insertAdjacentHTML('afterbegin', sidebarHtml(active, user));
        }
        if (nav) nav.innerHTML = topbarHtml(active, user);
        const footer = document.getElementById('app-footer');
        if (footer) footer.innerHTML = footerHtml();

        const sidebarToggle = document.getElementById('sidebar-toggle');
        if (sidebarToggle) {
            sidebarToggle.addEventListener('click', () => {
                const open = document.body.classList.toggle('rs-sidebar-open');
                sidebarToggle.setAttribute('aria-expanded', String(open));
            });
        }
        const sidebarBackdrop = document.getElementById('sidebar-backdrop');
        if (sidebarBackdrop) {
            sidebarBackdrop.addEventListener('click', () => {
                document.body.classList.remove('rs-sidebar-open');
                if (sidebarToggle) sidebarToggle.setAttribute('aria-expanded', 'false');
            });
        }
        document.querySelectorAll('.rs-sidebar-nav .nav-link').forEach(link => {
            link.addEventListener('click', () => document.body.classList.remove('rs-sidebar-open'));
        });

        const logoutButton = document.getElementById('nav-logout');
        if (logoutButton) {
            logoutButton.addEventListener('click', () => RailSafeAPI.logout());
        }

        window.RailSafeUser = user;
        return user;
    }

    function guard(user, roles) {
        if (!user) {
            RailSafeAPI.redirectToLogin();
            return false;
        }
        if (roles && roles.length && !roles.includes(user.role)) {
            toast('You do not have permission to open that page.', 'danger');
            setTimeout(() => { window.location.href = 'dashboard.html'; }, 900);
            return false;
        }
        return true;
    }

    /* ---------- Clearly labelled demo simulation panel ---------- */

    function simulationCountdown(target) {
        if (!target) return 'Not Available';
        const millis = new Date(target).getTime() - Date.now();
        if (!Number.isFinite(millis)) return 'Not Available';
        const seconds = Math.max(0, Math.floor(millis / 1000));
        return seconds === 0 ? 'now' : formatDuration(seconds);
    }

    function tickSimulationCountdowns(container) {
        if (!container) return;
        container.querySelectorAll('[data-sim-target]').forEach(element => {
            element.textContent = simulationCountdown(element.dataset.simTarget);
        });
    }

    function simulationStat(label, value, icon) {
        return `<div class="col-6 col-md-4 col-xl-2">
            <div class="stat-card">
                <span class="stat-icon"><i class="bi ${icon}"></i></span>
                <div><div class="stat-value">${escapeHtml(value)}</div>
                <div class="stat-label">${escapeHtml(label)}</div></div>
            </div>
        </div>`;
    }

    function renderSimulationPanel(container, snapshot, errorMessage) {
        if (!container) return;
        if (errorMessage || !snapshot) {
            container.innerHTML = `<div class="alert-rs alert-rs-warning mb-0" style="font-size:.8rem;">
                <i class="bi bi-exclamation-triangle me-1"></i>
                Demo simulation unavailable. ${escapeHtml(errorMessage || 'No simulation snapshot returned.')}
            </div>`;
            return;
        }
        const counts = snapshot.counts || {};
        const gates = snapshot.gates || [];
        const trains = snapshot.trains || [];
        const gateRows = gates.slice(0, 16).map(gate => `
            <tr>
                <td><span class="badge-soft">${escapeHtml(gate.gateCode)}</span></td>
                <td>${escapeHtml(gate.name)}</td>
                <td>${statusBadge(gate.status)}</td>
            </tr>`).join('');
        const trainRows = trains.slice(0, 12).map(train => `
            <tr>
                <td><strong>${escapeHtml(train.trainName)}</strong><br>
                    <span class="text-muted-rs" style="font-size:.72rem;">#${escapeHtml(train.trainNumber)}</span></td>
                <td>${escapeHtml(train.gateName)}<br>
                    <span class="text-muted-rs" style="font-size:.72rem;">${escapeHtml(train.gateCode)}</span></td>
                <td>${escapeHtml(train.status)}</td>
                <td><span data-sim-target="${escapeHtml(train.expectedArrivalAt || '')}">Not Available</span></td>
                <td><span data-sim-target="${escapeHtml(train.expectedDepartureAt || '')}">Not Available</span></td>
            </tr>`).join('');

        container.innerHTML = `
            <div class="alert-rs alert-rs-warning mb-3" style="font-size:.8rem;">
                <i class="bi bi-exclamation-triangle-fill me-1"></i>
                <strong>${escapeHtml(snapshot.label || 'DEMO SIMULATION')}</strong>
                <span class="badge-live-unavailable ms-2">NOT LIVE</span>
                <div class="text-muted-rs mt-1">${escapeHtml(snapshot.warning || '')}</div>
            </div>
            <div class="row g-2 mb-3">
                ${simulationStat('Demo gates', counts.totalGates, 'bi-signpost-split')}
                ${simulationStat('Open', counts.openGates, 'bi-check-circle')}
                ${simulationStat('Closed', counts.closedGates, 'bi-x-octagon')}
                ${simulationStat('Closing', counts.closingGates, 'bi-arrow-down-right-circle')}
                ${simulationStat('Approaching trains', counts.approachingTrains, 'bi-train-front')}
                ${simulationStat('Active crossings', counts.activeCrossings, 'bi-activity')}
            </div>
            <div class="row g-3">
                <div class="col-lg-5">
                    <h3 class="h6 mb-2">Simulation gate board</h3>
                    <div class="table-responsive">
                        <table class="table table-rs align-middle mb-0">
                            <thead><tr><th>Code</th><th>Crossing</th><th>Status</th></tr></thead>
                            <tbody>${gateRows || '<tr><td colspan="3">No simulation gates available.</td></tr>'}</tbody>
                        </table>
                    </div>
                </div>
                <div class="col-lg-7">
                    <h3 class="h6 mb-2">Simulation train board</h3>
                    <div class="table-responsive">
                        <table class="table table-rs align-middle mb-0">
                            <thead><tr>
                                <th>Train</th><th>Demo crossing</th><th>Status</th>
                                <th>Expected arrival</th><th>Expected leaving</th>
                            </tr></thead>
                            <tbody>${trainRows || '<tr><td colspan="5">No simulation trains available.</td></tr>'}</tbody>
                        </table>
                    </div>
                </div>
            </div>
            <p class="text-muted-rs small mt-2 mb-0">
                Countdowns update every second. Snapshot refreshes every 10 seconds.
                Mode: ${escapeHtml(snapshot.dataMode || 'SIMULATION')} ·
                Generated: ${escapeHtml(formatDateTime(snapshot.generatedAt))} ·
                Live data available: NO
            </p>`;
        tickSimulationCountdowns(container);
    }

    /**
     * Fetches and renders the demo-simulation panel. This is deliberately separate
     * from the operational SSE stream: polling and countdowns must not imply a
     * genuine live feed or turn on the LIVE badge.
     */
    function startSimulationPanel(containerId) {
        const container = document.getElementById(containerId);
        if (!container) return null;
        const refresh = async () => {
            try {
                const snapshot = await RailSafeAPI.apiFetch('/api/simulation/snapshot');
                renderSimulationPanel(container, snapshot);
            } catch (error) {
                renderSimulationPanel(container, null, error.message);
            }
        };
        void refresh();
        const poll = setInterval(() => { void refresh(); }, 10000);
        const tick = setInterval(() => { tickSimulationCountdowns(container); }, 1000);
        return { refresh, stop: () => { clearInterval(poll); clearInterval(tick); } };
    }

    window.RailSafeUI = {
        escapeHtml,
        statusBadge,
        statusClass,
        verificationBadge,
        sourceLabel,
        formatDateTime,
        timeAgo,
        initials,
        notAvailable,
        awaitingBadge,
        renderLiveIndicator,
        renderSyncTime,
        formatDuration,
        startLiveStream,
        renderSimulationPanel,
        startSimulationPanel,
        toast,
        loading,
        emptyState,
        inlineAlert,
        clearAlert,
        initShell,
        guard
    };
})();
