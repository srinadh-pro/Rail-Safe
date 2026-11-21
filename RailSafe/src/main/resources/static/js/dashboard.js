const DEFAULT_MAP_CENTER = { lat: 17.3850, lng: 78.4867, label: 'Default map centre' };

const dashboardState = {
    user: null,
    center: { ...DEFAULT_MAP_CENTER },
    radiusKm: 5,
    usingDefaultCenter: true,
    liveCounts: null,
    liveSource: null
};

document.addEventListener('DOMContentLoaded', async () => {
    const user = await RailSafeUI.initShell('dashboard');
    if (!RailSafeUI.guard(user)) return;
    dashboardState.user = user;

    document.getElementById('dashboard-name').textContent = user.fullName;
    document.getElementById('dashboard-role').textContent = user.role;

    document.getElementById('use-location').addEventListener('click', useMyLocation);
    document.getElementById('refresh-nearby').addEventListener('click', loadNearbyGates);
    document.getElementById('refresh-live').addEventListener('click', async () => {
        await loadLiveCounts();
        RailSafeUI.toast('Live overview refreshed.', 'success');
    });
    document.getElementById('radius-select').addEventListener('change', event => {
        dashboardState.radiusKm = Number(event.target.value);
        loadNearbyGates();
    });

    showLocationNotice();
    await Promise.allSettled([
        loadNearbyGates(),
        loadSavedRoutes(),
        loadUnreadCount(),
        loadRecentNotifications(),
        loadLiveCounts()
    ]);
    dashboardState.liveSource = RailSafeUI.startLiveStream(loadLiveCounts);
    RailSafeUI.startSimulationPanel('simulation-body');
});

/* ------------------------------------------------------------------ */
/* Live counts (SSE-backed, login-first)                               */
/* ------------------------------------------------------------------ */

async function loadLiveCounts() {
    try {
        const counts = await RailSafeAPI.apiFetch('/api/live/counts');
        dashboardState.liveCounts = counts;
        renderLiveCounts(counts);
    } catch (error) {
        dashboardState.liveCounts = null;
        renderLiveCounts(null);
    }
}

function renderLiveCounts(counts) {
    const setCount = (id, value) => {
        const element = document.getElementById(id);
        if (element) element.textContent = value === null || value === undefined
            ? 'Not Available' : value;
    };
    setCount('live-total', counts && counts.totalGates);
    setCount('live-open', counts && counts.openGates);
    setCount('live-closed', counts && counts.closedGates);
    setCount('live-closing', counts && counts.closingGates);
    setCount('live-opening', counts && counts.openingGates);
    setCount('live-awaiting', counts && counts.awaitingVerificationGates);
    setCount('live-approaching', counts && counts.approachingTrains);
    setCount('live-active', counts && counts.activeCrossings);

    RailSafeUI.renderLiveIndicator(document.getElementById('dashboard-live-indicator'), counts);
    RailSafeUI.renderSyncTime(document.getElementById('dashboard-last-sync'), counts);

    const meta = document.getElementById('live-meta');
    if (meta) {
        const source = counts && counts.dataSource ? counts.dataSource : 'Not Available';
        const mode = counts && counts.dataMode ? counts.dataMode : 'Not Available';
        const updated = counts && counts.lastSuccessfulUpdate
            ? RailSafeUI.formatDateTime(counts.lastSuccessfulUpdate) : 'Not Available';
        const emptyState = counts && counts.totalGates === 0
            ? ' No verified operational crossings are available yet; restricted simulation records are not shown.'
            : '';
        meta.textContent = `Source: ${source} · Mode: ${mode} · Last successful update: ${updated}` +
            (counts && counts.message ? ` · ${counts.message}` : '') + emptyState;
    }
}

function showLocationNotice() {
    RailSafeUI.inlineAlert(document.getElementById('location-alert'), 'warning',
        '<i class="bi bi-geo-alt me-1"></i><strong>Location not shared yet.</strong> ' +
        'Showing the default map centre. Click <em>Use my location</em> to search around you. ' +
        'Your browser will ask for permission first.');
}

function useMyLocation() {
    if (!navigator.geolocation) {
        RailSafeUI.toast('Geolocation is not supported by this browser.', 'warning');
        return;
    }
    const button = document.getElementById('use-location');
    button.disabled = true;
    button.innerHTML = '<span class="spinner-border spinner-border-sm me-1"></span>Locating…';

    navigator.geolocation.getCurrentPosition(position => {
        dashboardState.center = {
            lat: position.coords.latitude,
            lng: position.coords.longitude,
            label: 'your current location'
        };
        dashboardState.usingDefaultCenter = false;
        document.getElementById('location-label').innerHTML =
            `<i class="bi bi-crosshair text-success me-1"></i>Using ${dashboardState.center.lat.toFixed(4)}, ` +
            `${dashboardState.center.lng.toFixed(4)} (±${Math.round(position.coords.accuracy)} m)`;
        RailSafeUI.inlineAlert(document.getElementById('location-alert'), 'success',
            '<i class="bi bi-check-circle me-1"></i>Location permission granted. ' +
            'Nearby crossings are now measured from your approximate position.');
        loadNearbyGates();
    }, error => {
        const message = error.code === error.PERMISSION_DENIED
            ? 'Location permission was denied. The default area is still shown.'
            : 'Could not get your location. The default area is still shown.';
        RailSafeUI.inlineAlert(document.getElementById('location-alert'), 'warning',
            '<i class="bi bi-exclamation-triangle me-1"></i>' + RailSafeUI.escapeHtml(message));
        RailSafeUI.toast(message, 'warning');
    }, { enableHighAccuracy: true, timeout: 10000, maximumAge: 60000 });

    button.disabled = false;
    button.innerHTML = '<i class="bi bi-crosshair me-1"></i>Use my location';
}

async function loadNearbyGates() {
    const container = document.getElementById('nearby-gates');
    RailSafeUI.loading(container, 'Searching nearby crossings…');
    if (dashboardState.usingDefaultCenter) {
        document.getElementById('location-label').textContent =
            `Using default map centre (${DEFAULT_MAP_CENTER.lat.toFixed(4)}, ${DEFAULT_MAP_CENTER.lng.toFixed(4)})`;
    }

    try {
        const gates = await RailSafeAPI.apiFetch(
            `/api/gates/nearby?lat=${dashboardState.center.lat}&lng=${dashboardState.center.lng}` +
            `&radiusKm=${dashboardState.radiusKm}`
        );
        document.getElementById('nearby-count').textContent = gates.length;
        document.getElementById('nearby-closed').textContent =
            gates.filter(gate => gate.displayStatus === 'CLOSED').length;

        if (!gates.length) {
            RailSafeUI.emptyState(container, 'bi-geo', 'No verified crossings are available nearby',
                'Try a larger radius, or share your location to search around you.');
            return;
        }

        container.innerHTML = gates.map(gate => `
            <div class="col-md-6">
                <div class="gate-card ${RailSafeUI.statusClass(gate.displayStatus)}">
                    <div class="d-flex justify-content-between align-items-start gap-2">
                        ${RailSafeUI.statusBadge(gate.displayStatus)}
                        <span class="gate-distance">${gate.distanceKm.toFixed(1)} km</span>
                    </div>
                    <h3>${RailSafeUI.escapeHtml(gate.name)}</h3>
                    <div class="gate-meta">
                        <span><i class="bi bi-broadcast"></i>${RailSafeUI.sourceLabel(gate.statusSource)}</span>
                        <span><i class="bi bi-clock"></i>${RailSafeUI.timeAgo(gate.lastUpdatedAt)}</span>
                    </div>
                    ${gate.stale ? '<div class="stale-note"><i class="bi bi-exclamation-triangle me-1"></i>Stale data → displayed as UNKNOWN.</div>' : ''}
                    <div class="mt-3 d-flex gap-2">
                        <a href="gate.html?id=${gate.id}" class="btn btn-sm btn-soft flex-grow-1">Details</a>
                        <a href="map.html?focusGate=${gate.id}" class="btn btn-sm btn-outline-navy" title="Open on map">
                            <i class="bi bi-map"></i>
                        </a>
                    </div>
                </div>
            </div>
        `).join('');
    } catch (error) {
        if (error.status === 401) {
            RailSafeAPI.redirectToLogin();
            return;
        }
        RailSafeUI.emptyState(container, 'bi-wifi-off', 'Could not load nearby crossings', error.message);
    }
}

async function loadSavedRoutes() {
    const container = document.getElementById('saved-routes');
    try {
        const routes = await RailSafeAPI.apiFetch('/api/routes');
        document.getElementById('saved-count').textContent = routes.length;

        if (!routes.length) {
            RailSafeUI.emptyState(container, 'bi-bookmark', 'No saved routes yet',
                'Plan a route on the map and save it for quick access.');
            return;
        }

        container.innerHTML = routes.map(route => {
            const params = new URLSearchParams({
                startLat: route.startLat,
                startLng: route.startLng,
                startLabel: route.startLabel,
                destLat: route.destLat,
                destLng: route.destLng,
                destLabel: route.destLabel
            });
            return `<div class="route-saved-item align-items-center">
                <div class="notification-icon"><i class="bi bi-signpost-2"></i></div>
                <div class="flex-grow-1 min-w-0">
                    <div class="fw-bold" style="font-size:.9rem;">${RailSafeUI.escapeHtml(route.name)}</div>
                    <div class="text-muted-rs text-break-anywhere" style="font-size:.76rem;">
                        ${RailSafeUI.escapeHtml(route.startLabel)} → ${RailSafeUI.escapeHtml(route.destLabel)}
                    </div>
                    <div class="text-muted-rs" style="font-size:.72rem;">
                        ${route.gateIds.length} crossing(s) near the saved route · ${RailSafeUI.timeAgo(route.createdAt)}
                    </div>
                </div>
                <div class="d-flex gap-1">
                    <a href="map.html?${params.toString()}" class="btn btn-sm btn-soft" title="Open route">
                        <i class="bi bi-arrow-up-right"></i>
                    </a>
                    <button class="btn btn-sm btn-outline-danger delete-route" data-id="${route.id}" title="Delete route">
                        <i class="bi bi-trash"></i>
                    </button>
                </div>
            </div>`;
        }).join('');

        container.querySelectorAll('.delete-route').forEach(button => {
            button.addEventListener('click', async () => {
                if (!confirm('Delete this saved route?')) return;
                try {
                    await RailSafeAPI.apiFetch(`/api/routes/${button.dataset.id}`, { method: 'DELETE' });
                    RailSafeUI.toast('Saved route deleted.', 'success');
                    loadSavedRoutes();
                } catch (error) {
                    RailSafeUI.toast(error.message, 'danger');
                }
            });
        });
    } catch (error) {
        RailSafeUI.emptyState(container, 'bi-wifi-off', 'Could not load saved routes', error.message);
    }
}

async function loadUnreadCount() {
    try {
        const result = await RailSafeAPI.apiFetch('/api/notifications/unread-count');
        document.getElementById('unread-count').textContent = result.count;
        const badge = document.getElementById('header-unread');
        badge.textContent = result.count;
        badge.style.display = result.count > 0 ? 'inline-block' : 'none';
    } catch (error) {
        console.warn('Unread count failed', error);
    }
}

async function loadRecentNotifications() {
    const container = document.getElementById('dashboard-notifications');
    try {
        const notifications = await RailSafeAPI.apiFetch('/api/notifications');
        if (!notifications.length) {
            RailSafeUI.emptyState(container, 'bi-bell-slash', 'No alerts yet',
                'A gate status change will appear here.');
            return;
        }
        container.innerHTML = notifications.slice(0, 4).map(notification => `
            <a class="notification-item ${notification.read ? '' : 'unread'}" href="notifications.html">
                <div class="notification-icon ${notification.type === 'GATE_CLOSED' ? 'closed' : notification.type === 'GATE_OPENED' ? 'open' : notification.type === 'REPORT' ? 'report' : 'system'}">
                    <i class="bi ${notification.type === 'GATE_CLOSED' ? 'bi-x-octagon' : notification.type === 'GATE_OPENED' ? 'bi-check-circle' : notification.type === 'REPORT' ? 'bi-send-check' : 'bi-info-circle'}"></i>
                </div>
                <div class="flex-grow-1">
                    <div class="fw-bold" style="font-size:.82rem;">${RailSafeUI.escapeHtml(notification.title)}</div>
                    <div class="text-muted-rs" style="font-size:.73rem;">${RailSafeUI.timeAgo(notification.createdAt)}</div>
                </div>
                ${notification.read ? '' : '<span class="status-badge status-open" style="font-size:.62rem;padding:.2rem .45rem;">NEW</span>'}
            </a>
        `).join('');
    } catch (error) {
        RailSafeUI.emptyState(container, 'bi-wifi-off', 'Could not load alerts', error.message);
    }
}
