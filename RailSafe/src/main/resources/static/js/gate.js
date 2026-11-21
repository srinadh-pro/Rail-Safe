/* RailSafe gate details: status, source/verification labels, stale warning,
   mini map, nearby crossings, status history and commuter reports. */
const gateState = {
    id: null,
    gate: null,
    detail: null,
    user: null,
    map: null,
    marker: null,
    pendingReport: false,
    reports: [],
    durationTimer: null
};

document.addEventListener('DOMContentLoaded', async () => {
    gateState.user = await RailSafeUI.initShell('map');

    const params = new URLSearchParams(window.location.search);
    gateState.id = Number(params.get('id'));

    bindGateUi();

    if (!gateState.id || !Number.isInteger(gateState.id)) {
        showMissingGate();
        return;
    }

    renderReportLoginState();
    await loadGate();
});

function bindGateUi() {
    document.getElementById('refresh-gate').addEventListener('click', refreshGate);
    document.getElementById('copy-coords').addEventListener('click', copyCoordinates);
    document.getElementById('report-form').addEventListener('submit', submitReport);
}

function clearGateDetail() {
    if (gateState.durationTimer) {
        clearInterval(gateState.durationTimer);
        gateState.durationTimer = null;
    }
    gateState.detail = null;
    const status = document.getElementById('gate-detail-status');
    const list = document.getElementById('gate-detail-list');
    const trains = document.getElementById('gate-train-detail');
    const note = document.getElementById('gate-detail-note');
    const duration = document.getElementById('gate-duration');
    if (status) status.innerHTML = '';
    if (list) list.innerHTML = '<div><dt>Detail</dt><dd>Not Available</dd></div>';
    if (trains) trains.innerHTML = '<div><dt>Next train</dt><dd>Not Available</dd></div>';
    if (note) note.textContent = 'Not Available';
    if (duration) duration.textContent = 'Not Available';
}

function showMissingGate() {
    clearGateDetail();
    document.title = 'Crossing not specified – RailSafe';
    document.getElementById('gate-name').textContent = 'Crossing not specified';
    document.getElementById('gate-location').textContent =
        'Open the gate map and choose a crossing to see its details.';
    document.getElementById('gate-badges').innerHTML = '';
    RailSafeUI.inlineAlert(document.getElementById('gate-alert'), 'warning',
        '<i class="bi bi-signpost me-1"></i><strong>No crossing ID was provided.</strong> ' +
        '<a class="fw-bold" href="map.html">Open the gate map</a> to choose a configured crossing.');
    document.getElementById('gate-status-large').innerHTML =
        '<span class="status-badge status-unknown"><span class="dot"></span>UNKNOWN</span>';
    document.getElementById('gate-history').innerHTML = '';
    document.getElementById('nearby-gates').innerHTML = '';
    document.getElementById('report-form').classList.add('hidden');
    document.getElementById('report-login-prompt').classList.add('hidden');
}

async function loadGate() {
    try {
        gateState.gate = await RailSafeAPI.apiFetch(`/api/gates/${gateState.id}`);
        RailSafeUI.clearAlert(document.getElementById('gate-alert'));
        renderGate();
        await Promise.allSettled([
            loadGateDetail(),
            loadHistory(),
            loadNearbyGates(),
            loadMyReports()
        ]);
    } catch (error) {
        clearGateDetail();
        if (error.status === 404) {
            document.getElementById('gate-name').textContent = 'Crossing not found';
            document.getElementById('gate-location').textContent =
                'It may no longer be configured.';
            RailSafeUI.inlineAlert(document.getElementById('gate-alert'), 'warning',
                '<i class="bi bi-search me-1"></i><strong>This crossing could not be found.</strong> ' +
                '<a class="fw-bold" href="map.html">Return to the gate map</a>.');
        } else {
            document.getElementById('gate-name').textContent = 'Crossing could not load';
            RailSafeUI.inlineAlert(document.getElementById('gate-alert'), 'danger',
                '<i class="bi bi-wifi-off me-1"></i><strong>Could not load this crossing.</strong> ' +
                RailSafeUI.escapeHtml(error.message));
        }
        document.getElementById('gate-status-large').innerHTML =
            '<span class="status-badge status-unknown"><span class="dot"></span>UNKNOWN</span>';
        document.getElementById('gate-history').innerHTML = '';
        document.getElementById('nearby-gates').innerHTML = '';
    }
}

function renderGate() {
    const gate = gateState.gate;
    document.title = `${gate.name} – RailSafe`;
    document.getElementById('gate-code').textContent = gate.gateCode;
    document.getElementById('gate-name').textContent = gate.name;
    document.getElementById('gate-location').innerHTML =
        `<i class="bi bi-geo-alt me-1"></i>${RailSafeUI.escapeHtml(gate.location)}`;

    document.getElementById('gate-badges').innerHTML = `
        ${RailSafeUI.statusBadge(gate.displayStatus)}
        ${RailSafeUI.verificationBadge(gate.verificationStatus)}
        ${gate.stale ? '<span class="badge text-bg-warning">STALE / UNKNOWN</span>' : ''}
        <span class="badge-soft"><i class="bi bi-exclamation-triangle me-1"></i>REPORTED · NOT LIVE</span>`;

    document.getElementById('gate-status-large').innerHTML = `
        <div style="font-size:1.05rem;">${RailSafeUI.statusBadge(gate.displayStatus)}</div>
        <div class="mt-2 text-muted-rs" style="font-size:.78rem;line-height:1.5;">
            ${gate.stale
                ? 'Displayed as UNKNOWN because the last recorded status is stale.'
                : 'Displayed from the latest recorded status in the RailSafe dataset.'}
        </div>`;

    document.getElementById('gate-verification').innerHTML = `
        ${RailSafeUI.verificationBadge(gate.verificationStatus)}
        <div class="text-muted-rs mt-2" style="font-size:.76rem;">
            Source: ${RailSafeUI.escapeHtml(RailSafeUI.sourceLabel(gate.statusSource))}
        </div>`;

    document.getElementById('gate-details').innerHTML = `
        <div><dt>Displayed status</dt><dd>${RailSafeUI.statusBadge(gate.displayStatus)}</dd></div>
        <div><dt>Stored status</dt><dd>${RailSafeUI.statusBadge(gate.status)}</dd></div>
        <div><dt>Source</dt><dd>${RailSafeUI.escapeHtml(RailSafeUI.sourceLabel(gate.statusSource))}</dd></div>
        <div><dt>Verification</dt><dd>${RailSafeUI.verificationBadge(gate.verificationStatus)}</dd></div>
        <div><dt>Last updated</dt><dd>${RailSafeUI.escapeHtml(RailSafeUI.formatDateTime(gate.lastUpdatedAt))}
            <div class="text-muted-rs" style="font-size:.75rem;font-weight:500;">${RailSafeUI.timeAgo(gate.lastUpdatedAt)}</div>
        </dd></div>
        <div><dt>Updated by</dt><dd>${RailSafeUI.escapeHtml(gate.lastUpdatedBy || 'Not available')}</dd></div>
        <div><dt>Coordinates</dt><dd>${gate.latitude.toFixed(5)}, ${gate.longitude.toFixed(5)}</dd></div>
        <div><dt>Gate code</dt><dd>${RailSafeUI.escapeHtml(gate.gateCode)}</dd></div>`;

    document.getElementById('gate-notes').innerHTML = gate.notes
        ? `<i class="bi bi-journal-text me-1"></i>${RailSafeUI.escapeHtml(gate.notes)}`
        : '<i class="bi bi-journal me-1"></i>No additional notes were recorded for this crossing.';

    const staleContainer = document.getElementById('stale-banner');
    if (gate.stale) {
        RailSafeUI.inlineAlert(staleContainer, 'warning', `
            <div class="d-flex gap-2">
                <i class="bi bi-clock-history fs-5"></i>
                <div>
                    <strong>This status is stale, so the app displays UNKNOWN.</strong>
                    The stored value is ${RailSafeUI.statusBadge(gate.status)}, last recorded
                    ${RailSafeUI.timeAgo(gate.lastUpdatedAt)}.
                    Do not assume the crossing is open. Obey the physical barrier and railway staff.
                </div>
            </div>`);
    } else {
        RailSafeUI.clearAlert(staleContainer);
    }

    const focusLink = `map.html?focusGate=${gate.id}`;
    document.getElementById('gate-map-link').href = focusLink;
    document.getElementById('map-link-small').href = focusLink;
    document.getElementById('directions-link').href =
        `map.html?destLat=${gate.latitude}&destLng=${gate.longitude}` +
        `&destLabel=${encodeURIComponent(gate.name)}`;

    renderGateMap();
    updateReportAvailability();
}

/* ------------------------------------------------------------------ */
/* GateDetailResponse: reference fields + local per-second timer        */
/* ------------------------------------------------------------------ */

function detailValue(value) {
    return RailSafeUI.escapeHtml(RailSafeUI.notAvailable(value));
}

function detailDate(value) {
    return value ? RailSafeUI.escapeHtml(RailSafeUI.formatDateTime(value)) : 'Not Available';
}

function detailNumber(value, suffix = '') {
    if (value === null || value === undefined || value === '') return 'Not Available';
    const number = Number(value);
    return Number.isFinite(number) ? `${number}${suffix}` : detailValue(value);
}

function detailListValue(value) {
    if (Array.isArray(value)) return value.length ? detailValue(value.join(', ')) : 'Not Available';
    return detailValue(value);
}

function detailStatusBadge(detail) {
    const status = String(RailSafeUI.notAvailable(detail.status));
    const unverified = status === 'Awaiting Verification' || detail.verificationStatus !== 'VERIFIED';
    if (unverified) return RailSafeUI.awaitingBadge();
    if (['OPEN', 'CLOSED', 'UNKNOWN'].includes(status)) return RailSafeUI.statusBadge(status);
    return `<span class="badge-soft">${detailValue(status)}</span>`;
}

async function loadGateDetail() {
    try {
        const detail = await RailSafeAPI.apiFetch(`/api/gates/${gateState.id}/detail`);
        gateState.detail = detail;
        renderGateDetail(detail);
    } catch (error) {
        console.warn('Gate detail could not be loaded:', error);
        clearGateDetail();
    }
}

function renderGateDetail(detail) {
    if (!detail) {
        clearGateDetail();
        return;
    }

    const statusContainer = document.getElementById('gate-detail-status');
    if (statusContainer) {
        statusContainer.innerHTML = `
            <span class="small-label">Detail status</span>
            ${detailStatusBadge(detail)}
            ${RailSafeUI.verificationBadge(detail.verificationStatus)}`;
    }

    const coordinates = Number.isFinite(Number(detail.latitude)) && Number.isFinite(Number(detail.longitude)) &&
        detail.latitude !== null && detail.longitude !== null
        ? `${detailNumber(detail.latitude)}, ${detailNumber(detail.longitude)}`
        : 'Not Available';

    const rows = [
        ['Gate reference', detailValue(detail.gateId)],
        ['Crossing name', detailValue(detail.gateName)],
        ['Railway station', detailValue(detail.railwayStation)],
        ['District', detailValue(detail.district)],
        ['State', detailValue(detail.state)],
        ['Railway zone', detailValue(detail.railwayZone)],
        ['Division', detailValue(detail.division)],
        ['Coordinates', coordinates],
        ['Gate type', detailValue(detail.gateType)],
        ['Status', detailStatusBadge(detail)],
        ['Verification', RailSafeUI.verificationBadge(detail.verificationStatus)],
        ['Data source', detailValue(detail.dataSource)],
        ['Data classification', detailValue(detail.dataClassification)],
        ['Last updated', detailDate(detail.lastUpdatedAt)],
        ['Status since', detailDate(detail.statusSince)],
        ['Expected closing', detailDate(detail.expectedClosingAt)],
        ['Expected closing source', detailValue(detail.expectedClosingSource)],
        ['Expected opening', detailDate(detail.expectedOpeningAt)],
        ['Expected opening source', detailValue(detail.expectedOpeningSource)],
        ['Actual closing', detailDate(detail.actualClosingAt)],
        ['Actual opening', detailDate(detail.actualOpeningAt)],
        ['Responsible staff', detailValue(detail.responsibleStaff)],
        ['Associated routes', detailListValue(detail.associatedRoutes)]
    ];

    const list = document.getElementById('gate-detail-list');
    if (list) {
        list.innerHTML = rows.map(([label, value]) => `
            <div><dt>${RailSafeUI.escapeHtml(label)}</dt><dd>${value}</dd></div>
        `).join('');
    }

    const trainRows = [
        ['Train number', detailValue(detail.nextTrainNumber)],
        ['Train name', detailValue(detail.nextTrainName)],
        ['Direction', detailValue(detail.nextTrainDirection)],
        ['ETA', detailDate(detail.nextTrainEta)],
        ['Distance (km)', detailNumber(detail.nextTrainDistanceKm)],
        ['Train status', detailValue(detail.nextTrainStatus)]
    ];
    const trains = document.getElementById('gate-train-detail');
    if (trains) {
        trains.innerHTML = trainRows.map(([label, value]) => `
            <div><dt>${RailSafeUI.escapeHtml(label)}</dt><dd>${value}</dd></div>
        `).join('');
    }

    const note = document.getElementById('gate-detail-note');
    if (note) note.textContent = RailSafeUI.notAvailable(detail.note);

    startDurationTimer(detail);
}

function startDurationTimer(detail) {
    if (gateState.durationTimer) {
        clearInterval(gateState.durationTimer);
        gateState.durationTimer = null;
    }
    const element = document.getElementById('gate-duration');
    if (!element) return;

    const since = detail && detail.statusSince ? new Date(detail.statusSince).getTime() : NaN;
    if (!Number.isFinite(since)) {
        element.textContent = 'Not Available';
        return;
    }

    const serverSeconds = Number(detail.currentDurationSeconds);
    const baseSeconds = Number.isFinite(serverSeconds)
        ? Math.max(0, Math.floor(serverSeconds))
        : Math.max(0, Math.floor((Date.now() - since) / 1000));
    const localEpoch = Date.now();
    const tick = () => {
        element.textContent = RailSafeUI.formatDuration(
            baseSeconds + Math.floor((Date.now() - localEpoch) / 1000));
    };
    tick();
    gateState.durationTimer = setInterval(tick, 1000);
}

function renderGateMap() {
    const gate = gateState.gate;
    const element = document.getElementById('gate-map');
    if (!window.L) {
        element.innerHTML = `<div class="p-4 text-center text-muted-rs">
            The map library could not load. Gate coordinates are ${gate.latitude.toFixed(5)},
            ${gate.longitude.toFixed(5)}.
        </div>`;
        return;
    }

    const point = [gate.latitude, gate.longitude];
    if (!gateState.map) {
        gateState.map = L.map('gate-map', {
            zoomControl: true,
            scrollWheelZoom: true
        }).setView(point, 16);

        L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
            maxZoom: 19,
            attribution: '&copy; <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noopener">OpenStreetMap</a> contributors'
        }).addTo(gateState.map);

        gateState.marker = L.marker(point, {
            icon: gatePinIcon(gate.displayStatus)
        }).addTo(gateState.map);
    } else {
        gateState.map.setView(point, 16);
        gateState.marker.setLatLng(point);
        gateState.marker.setIcon(gatePinIcon(gate.displayStatus));
    }

    gateState.marker.bindPopup(buildGatePopup(gate), { maxWidth: 280 });
    gateState.marker.openPopup();
}

function gatePinIcon(status) {
    return L.divIcon({
        className: 'gate-marker',
        html: `<span class="gate-pin ${(status || 'UNKNOWN').toLowerCase()}"></span>`,
        iconSize: [22, 22],
        iconAnchor: [11, 11],
        popupAnchor: [0, -12]
    });
}

function buildGatePopup(gate) {
    return `<div>
        <div class="d-flex justify-content-between align-items-start gap-2">
            ${RailSafeUI.statusBadge(gate.displayStatus)}
            <span class="badge-soft">${RailSafeUI.escapeHtml(gate.gateCode)}</span>
        </div>
        <h4>${RailSafeUI.escapeHtml(gate.name)}</h4>
        <div class="popup-meta">${RailSafeUI.escapeHtml(gate.location)}</div>
        <div class="popup-meta mt-1">
            ${RailSafeUI.escapeHtml(RailSafeUI.sourceLabel(gate.statusSource))} ·
            ${RailSafeUI.timeAgo(gate.lastUpdatedAt)}
        </div>
    </div>`;
}

async function loadHistory() {
    const container = document.getElementById('gate-history');
    try {
        const history = await RailSafeAPI.apiFetch(`/api/gates/${gateState.id}/history`);
        document.getElementById('history-count').textContent =
            `${history.length} event${history.length === 1 ? '' : 's'}`;

        if (!history.length) {
            RailSafeUI.emptyState(container, 'bi-clock-history', 'No status history yet',
                'Status changes will appear here with their source and reviewer.');
            return;
        }

        container.innerHTML = history.map(event => `
            <div class="history-item">
                <div class="d-flex flex-wrap align-items-center gap-2">
                    <span class="history-title">
                        ${event.oldStatus
                            ? `${RailSafeUI.statusBadge(event.oldStatus)} <i class="bi bi-arrow-right mx-1"></i>`
                            : '<span class="badge-soft">Initial</span> <i class="bi bi-arrow-right mx-1"></i>'}
                        ${RailSafeUI.statusBadge(event.newStatus)}
                    </span>
                    <span class="badge-soft">${RailSafeUI.escapeHtml(RailSafeUI.sourceLabel(event.source))}</span>
                </div>
                <div class="history-meta mt-1">
                    <i class="bi bi-clock me-1"></i>${RailSafeUI.escapeHtml(RailSafeUI.formatDateTime(event.changedAt))}
                    <span class="mx-1">·</span>
                    <i class="bi bi-person me-1"></i>${RailSafeUI.escapeHtml(event.changedBy || 'System')}
                </div>
                ${event.note
                    ? `<div class="history-meta mt-1"><i class="bi bi-chat-left-text me-1"></i>${RailSafeUI.escapeHtml(event.note)}</div>`
                    : ''}
            </div>
        `).join('');
    } catch (error) {
        RailSafeUI.emptyState(container, 'bi-wifi-off', 'Could not load status history', error.message);
    }
}

async function loadNearbyGates() {
    const container = document.getElementById('nearby-gates');
    const gate = gateState.gate;
    if (!gate) return;

    try {
        const gates = await RailSafeAPI.apiFetch(
            `/api/gates/nearby?lat=${gate.latitude}&lng=${gate.longitude}&radiusKm=3`);
        const nearby = gates.filter(item => item.id !== gate.id).slice(0, 5);

        if (!nearby.length) {
            RailSafeUI.emptyState(container, 'bi-geo', 'No other configured crossings nearby',
                'Open the gate map to see all available crossings.');
            return;
        }

        container.innerHTML = nearby.map(item => `
            <a class="notification-item align-items-center" href="gate.html?id=${item.id}">
                <div class="notification-icon ${item.displayStatus === 'CLOSED' ? 'closed' : item.displayStatus === 'OPEN' ? 'open' : 'system'}">
                    <i class="bi ${item.displayStatus === 'CLOSED' ? 'bi-x-octagon' : item.displayStatus === 'OPEN' ? 'bi-check-circle' : 'bi-question-circle'}"></i>
                </div>
                <div class="flex-grow-1 min-w-0">
                    <div class="fw-bold text-dark" style="font-size:.82rem;">${RailSafeUI.escapeHtml(item.name)}</div>
                    <div class="text-muted-rs" style="font-size:.72rem;">
                        ${Number(item.distanceKm).toFixed(1)} km straight-line · ${RailSafeUI.timeAgo(item.lastUpdatedAt)}
                    </div>
                </div>
                ${RailSafeUI.statusBadge(item.displayStatus)}
            </a>
        `).join('');
    } catch (error) {
        RailSafeUI.emptyState(container, 'bi-wifi-off', 'Could not load nearby crossings', error.message);
    }
}

function renderReportLoginState() {
    const prompt = document.getElementById('report-login-prompt');
    const form = document.getElementById('report-form');
    const next = `gate.html?id=${gateState.id}`;

    if (!gateState.user) {
        prompt.classList.remove('hidden');
        prompt.innerHTML = `
            <div class="alert-rs alert-rs-info">
                <i class="bi bi-person-lock me-1"></i>
                <strong>Log in to submit a report.</strong>
                RailSafe needs an account so staff can review reports and notify you of the decision.
                <div class="mt-2">
                    <a class="btn btn-sm btn-primary" href="login.html?next=${encodeURIComponent(next)}">Log in</a>
                    <a class="btn btn-sm btn-outline-navy" href="register.html?next=${encodeURIComponent(next)}">Register</a>
                </div>
            </div>`;
        form.classList.add('hidden');
        document.getElementById('my-reports').innerHTML = `
            <div class="text-muted-rs" style="font-size:.8rem;">
                <a href="login.html?next=${encodeURIComponent(next)}" class="fw-bold">Log in</a>
                to see your reports for this crossing.
            </div>`;
        return;
    }

    prompt.classList.add('hidden');
    prompt.innerHTML = '';
    form.classList.remove('hidden');
}

async function loadMyReports() {
    if (!gateState.user || !gateState.gate) return;
    const container = document.getElementById('my-reports');

    try {
        const reports = await RailSafeAPI.apiFetch('/api/reports/mine');
        gateState.reports = reports.filter(report => report.gateId === gateState.gate.id);
        gateState.pendingReport = gateState.reports.some(report => report.status === 'PENDING');
        renderMyReports();
        updateReportAvailability();
    } catch (error) {
        if (error.status === 401) {
            return;
        }
        RailSafeUI.emptyState(container, 'bi-wifi-off', 'Could not load your reports', error.message);
    }
}

function renderMyReports() {
    const container = document.getElementById('my-reports');
    if (!gateState.reports.length) {
        RailSafeUI.emptyState(container, 'bi-send', 'You have not reported this crossing',
            'Submit an observation above. Staff will review it before it can affect the gate status.');
        return;
    }

    container.innerHTML = gateState.reports.map(report => {
        const statusClass = report.status === 'APPROVED' ? 'success'
            : report.status === 'REJECTED' ? 'danger' : 'warning';
        const statusIcon = report.status === 'APPROVED' ? 'bi-check-circle'
            : report.status === 'REJECTED' ? 'bi-x-circle' : 'bi-hourglass-split';
        return `<div class="notification-item">
            <div class="notification-icon ${report.status === 'APPROVED' ? 'open' : report.status === 'REJECTED' ? 'closed' : 'report'}">
                <i class="bi ${statusIcon}"></i>
            </div>
            <div class="flex-grow-1">
                <div class="d-flex flex-wrap align-items-center justify-content-between gap-2">
                    <div class="d-flex flex-wrap align-items-center gap-2">
                        <span class="badge text-bg-${statusClass}">${report.status}</span>
                        <span class="text-muted-rs" style="font-size:.72rem;">Reported ${RailSafeUI.statusBadge(report.reportedStatus)}</span>
                    </div>
                    <span class="text-muted-rs" style="font-size:.7rem;">${RailSafeUI.timeAgo(report.createdAt)}</span>
                </div>
                ${report.comment
                    ? `<p class="mb-0 mt-1">${RailSafeUI.escapeHtml(report.comment)}</p>`
                    : '<p class="mb-0 mt-1 text-muted-rs">No comment provided.</p>'}
                ${report.reviewNote
                    ? `<div class="text-muted-rs mt-1" style="font-size:.72rem;"><i class="bi bi-chat-left-text me-1"></i>Reviewer note: ${RailSafeUI.escapeHtml(report.reviewNote)}</div>`
                    : ''}
            </div>
        </div>`;
    }).join('');
}

function updateReportAvailability() {
    if (!gateState.user) return;
    const button = document.getElementById('report-submit');
    const note = document.getElementById('report-pending-note');
    if (!button || !note) return;

    if (gateState.pendingReport) {
        button.disabled = true;
        note.classList.remove('hidden');
        note.innerHTML = `<i class="bi bi-hourglass-split me-1"></i>
            You already have a <strong>pending report</strong> for this crossing. Wait for staff to review it
            before submitting another.`;
    } else {
        button.disabled = false;
        note.classList.add('hidden');
        note.innerHTML = '';
    }
}

async function submitReport(event) {
    event.preventDefault();

    if (!gateState.user) {
        RailSafeAPI.redirectToLogin(`gate.html?id=${gateState.id}`);
        return;
    }
    if (gateState.pendingReport) {
        RailSafeUI.toast('A pending report already exists for this crossing.', 'warning');
        return;
    }

    const status = document.getElementById('report-status').value;
    const comment = document.getElementById('report-comment').value.trim();
    if (!status) {
        RailSafeUI.toast('Choose what you observed before submitting.', 'warning');
        return;
    }

    const button = document.getElementById('report-submit');
    const original = button.innerHTML;
    button.disabled = true;
    button.innerHTML = '<span class="spinner-border spinner-border-sm me-1"></span>Submitting…';

    try {
        await RailSafeAPI.apiFetch('/api/reports', {
            method: 'POST',
            body: JSON.stringify({
                gateId: gateState.gate.id,
                reportedStatus: status,
                comment: comment || null
            })
        });
        document.getElementById('report-form').reset();
        RailSafeUI.inlineAlert(document.getElementById('report-feedback'), 'success',
            '<i class="bi bi-check-circle me-1"></i><strong>Report submitted for review.</strong> ' +
            'It stays unverified and does not change the public gate status until a staff member approves it.');
        RailSafeUI.toast('Report submitted for staff review.', 'success');
        await loadMyReports();
    } catch (error) {
        if (error.status === 401) {
            RailSafeAPI.redirectToLogin(`gate.html?id=${gateState.id}`);
            return;
        }
        RailSafeUI.inlineAlert(document.getElementById('report-feedback'), 'danger',
            `<i class="bi bi-exclamation-triangle me-1"></i>${RailSafeUI.escapeHtml(error.message)}`);
        RailSafeUI.toast(error.message, 'danger');
        if (error.status === 409) {
            await loadMyReports();
        }
    } finally {
        button.innerHTML = original;
        updateReportAvailability();
    }
}

async function refreshGate() {
    if (!gateState.id) return;
    await loadGate();
    RailSafeUI.toast('Crossing refreshed.', 'success');
}

async function copyCoordinates() {
    if (!gateState.gate) return;
    const text = `${gateState.gate.latitude}, ${gateState.gate.longitude}`;
    try {
        await navigator.clipboard.writeText(text);
        RailSafeUI.toast('Coordinates copied.', 'success');
    } catch {
        RailSafeUI.toast(`Coordinates: ${text}`, 'info');
    }
}
