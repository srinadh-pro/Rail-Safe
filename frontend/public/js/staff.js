/* RailSafe staff dashboard: scoped to the crossings assigned to the signed-in STAFF user. */
/* eslint-disable no-unused-vars */

const staffState = {
    user: null,
    isStaff: false,
    gates: [],
    assignedGateIds: new Set(),
    reports: [],
    trains: [],
    maintenance: [],
    activity: [],
    statusGate: null,
    gateAction: null,
    reportDecision: null
};

document.addEventListener('DOMContentLoaded', () => {
    void init();
});

async function init() {
    const user = await RailSafeUI.initShell('staff');
    if (!RailSafeUI.guard(user, ['STAFF'])) return;
    staffState.user = user;
    staffState.isStaff = user.role === 'STAFF';

    bindUI();
    await refreshAll();
    RailSafeUI.startLiveStream(loadCounts);
    RailSafeUI.startSimulationPanel('simulation-body');
}

function bindUI() {
    document.getElementById('refresh-all').addEventListener('click', refreshAll);
    document.getElementById('open-gate-btn').addEventListener('click', openGateAction);
    document.getElementById('close-gate-btn').addEventListener('click', openGateAction);
    document.getElementById('gate-action-form').addEventListener('submit', submitGateAction);
    document.getElementById('refresh-activity').addEventListener('click', loadActivity);
    document.getElementById('refresh-gates').addEventListener('click', () => {
        void Promise.allSettled([loadGates(), loadCounts()]);
    });
    document.getElementById('gate-search').addEventListener('input', renderGates);
    document.getElementById('gate-table-body').addEventListener('click', onGateTableClick);

    document.getElementById('refresh-reports').addEventListener('click', loadReports);
    document.getElementById('report-filter').addEventListener('change', renderReports);
    document.getElementById('reports-body').addEventListener('click', onReportsTableClick);

    document.getElementById('refresh-trains').addEventListener('click', loadTrains);

    document.getElementById('refresh-maintenance').addEventListener('click', loadMaintenance);
    document.getElementById('add-maintenance').addEventListener('click', openMaintenanceModal);
    document.getElementById('maintenance-body').addEventListener('click', onMaintenanceTableClick);

    document.getElementById('status-form').addEventListener('submit', saveStatus);
    document.getElementById('report-decision-form').addEventListener('submit', submitReportDecision);
    document.getElementById('maintenance-form').addEventListener('submit', saveMaintenance);
}

async function refreshAll() {
    await Promise.allSettled([
        loadGates(), loadCounts(), loadReports(), loadTrains(),
        loadMaintenance(), loadActivity(), renderProfile()
    ]);
}

function isAssigned(gateId) {
    return staffState.assignedGateIds.has(Number(gateId));
}

function setText(id, value) {
    const element = document.getElementById(id);
    if (element) element.textContent = RailSafeUI.notAvailable(value);
}

function showLoadError(error, message) {
    RailSafeUI.inlineAlert(document.getElementById('staff-alert'), 'danger',
        RailSafeUI.escapeHtml(
            `${message} ${error && error.message ? error.message : ''}`.trim()));
}

function showActionError(error, fallback) {
    RailSafeUI.toast((error && error.message) || fallback, 'danger');
}

async function loadCounts() {
    try {
        const counts = await RailSafeAPI.apiFetch('/api/staff/counts');
        applyCounts(counts);
    } catch (error) {
        applyCounts(null);
        showLoadError(error, 'Could not load your scoped live counts.');
    }
}

function applyCounts(counts) {
    setText('stat-total', counts && counts.totalGates);
    setText('stat-open', counts && counts.openGates);
    setText('stat-closed', counts && counts.closedGates);
    setText('stat-closing', counts && counts.closingGates);
    setText('stat-opening', counts && counts.openingGates);
    setText('stat-awaiting', counts && counts.awaitingVerificationGates);
    setText('stat-approaching', counts && counts.approachingTrains);
    setText('stat-active', counts && counts.activeCrossings);
    RailSafeUI.renderLiveIndicator(document.getElementById('staff-live-indicator'), counts);
    RailSafeUI.renderSyncTime(document.getElementById('staff-last-sync'), counts);
}

async function loadGates() {
    try {
        staffState.gates = await RailSafeAPI.apiFetch('/api/staff/gates');
        staffState.assignedGateIds = new Set(staffState.gates.map(gate => Number(gate.id)));
        renderGates();
        refreshGateOptions();
    } catch (error) {
        showLoadError(error, 'Could not load your assigned crossings.');
        document.getElementById('gate-table-body').innerHTML = `
            <tr><td colspan="7"><div class="empty-state">
                <i class="bi bi-wifi-off"></i><p>${RailSafeUI.escapeHtml(error.message)}</p>
            </div></td></tr>`;
    }
}

function renderGates() {
    const body = document.getElementById('gate-table-body');
    if (!body) return;
    const term = document.getElementById('gate-search').value.trim().toLowerCase();
    const gates = staffState.gates.filter(gate => !term ||
        [gate.gateCode, gate.name, gate.location].some(value =>
            String(value || '').toLowerCase().includes(term)));

    if (!gates.length) {
        body.innerHTML = `<tr><td colspan="7"><div class="empty-state">
            <i class="bi bi-signpost-split"></i>
            <p class="fw-bold text-dark mb-1">${term ? 'No matching crossings' : 'No assigned crossings'}</p>
            <p>${term ? 'Try a different search.' : 'Ask an administrator to assign a crossing to your account.'}</p>
        </div></td></tr>`;
        return;
    }

    body.innerHTML = gates.map(gate => {
        const awaitingBadge = gate.verificationStatus !== 'VERIFIED'
            ? RailSafeUI.awaitingBadge() : '';
        const staleNote = gate.stale
            ? `<div class="text-muted-rs" style="font-size:.68rem;">stored ${RailSafeUI.escapeHtml(
                RailSafeUI.notAvailable(gate.status))} · stale</div>`
            : '';
        return `<tr>
            <td><span class="badge-soft">${RailSafeUI.escapeHtml(
                RailSafeUI.notAvailable(gate.gateCode))}</span></td>
            <td>
                <div class="fw-bold">${RailSafeUI.escapeHtml(RailSafeUI.notAvailable(gate.name))}</div>
                <div class="text-muted-rs" style="font-size:.72rem;">${RailSafeUI.escapeHtml(
                    RailSafeUI.notAvailable(gate.location))}</div>
            </td>
            <td>
                ${RailSafeUI.statusBadge(gate.displayStatus || gate.status)}
                ${staleNote}
                ${awaitingBadge}
            </td>
            <td class="d-none d-lg-table-cell" style="font-size:.76rem;">
                ${RailSafeUI.escapeHtml(RailSafeUI.notAvailable(gate.statusSource))}
                <div class="text-muted-rs">${RailSafeUI.escapeHtml(
                    RailSafeUI.notAvailable(gate.dataClassification))}</div>
            </td>
            <td class="d-none d-lg-table-cell" style="font-size:.76rem;">
                ${RailSafeUI.escapeHtml(RailSafeUI.notAvailable(gate.verificationStatus))}
            </td>
            <td class="d-none d-md-table-cell" style="font-size:.76rem;">
                ${gate.lastUpdatedAt ? RailSafeUI.escapeHtml(
                    RailSafeUI.formatDateTime(gate.lastUpdatedAt)) : 'Not Available'}
            </td>
            <td class="text-end">
                <div class="d-flex flex-wrap justify-content-end gap-1">
                    <button class="btn btn-sm btn-outline-navy" data-action="verify" data-id="${gate.id}"
                            title="Verify / set OPEN or CLOSED status"
                            aria-label="Verify status for ${RailSafeUI.escapeHtml(gate.name)}">
                        <i class="bi bi-check2-square me-1"></i>Verify / Set status
                    </button>
                    <button class="btn btn-sm btn-soft" data-action="history" data-id="${gate.id}"
                            title="Status history" aria-label="Status history for ${RailSafeUI.escapeHtml(gate.name)}">
                        <i class="bi bi-clock-history"></i>
                    </button>
                </div>
            </td>
        </tr>`;
    }).join('');
}

function onGateTableClick(event) {
    const button = event.target.closest('button[data-action]');
    if (!button) return;
    const gate = staffState.gates.find(item => Number(item.id) === Number(button.dataset.id));
    if (!gate || !isAssigned(gate.id)) {
        RailSafeUI.toast('You can only act on crossings assigned to you.', 'warning');
        return;
    }
    if (button.dataset.action === 'verify' || button.dataset.action === 'status') openStatusModal(gate);
    if (button.dataset.action === 'history') void loadHistory(gate);
}

function openStatusModal(gate) {
    staffState.statusGate = gate;
    document.getElementById('status-gate-id').value = gate.id;
    document.getElementById('status-gate-name').textContent = `${gate.gateCode} – ${gate.name}`;
    const observable = ['OPEN', 'CLOSED', 'CLOSING', 'OPENING', 'MAINTENANCE'];
    document.getElementById('status-value').value = observable.includes(gate.status) ? gate.status : '';
    document.getElementById('status-source').value = gate.statusSource === 'SENSOR' ? 'SENSOR' : 'STAFF';
    document.getElementById('status-verification').value =
        gate.verificationStatus === 'VERIFIED' ? 'VERIFIED' : 'UNVERIFIED';
    document.getElementById('status-note').value = '';
    bootstrap.Modal.getOrCreateInstance(document.getElementById('statusModal')).show();
}

async function saveStatus(event) {
    event.preventDefault();
    const gate = staffState.statusGate;
    if (!gate || !isAssigned(gate.id)) {
        RailSafeUI.toast('You can only update crossings assigned to you.', 'warning');
        return;
    }
    const payload = {
        status: document.getElementById('status-value').value,
        verificationStatus: document.getElementById('status-verification').value,
        note: document.getElementById('status-note').value.trim() || null
    };
    const button = document.getElementById('status-save');
    const original = button.innerHTML;
    button.disabled = true;
    button.innerHTML = '<span class="spinner-border spinner-border-sm me-1"></span>Saving…';
    try {
        await RailSafeAPI.apiFetch(`/api/gates/${gate.id}/verification`, {
            method: 'PUT',
            body: JSON.stringify(payload)
        });
        bootstrap.Modal.getOrCreateInstance(document.getElementById('statusModal')).hide();
        RailSafeUI.toast(payload.verificationStatus === 'VERIFIED'
            ? 'Verified status saved. Dashboards will update shortly.'
            : 'Report saved as unverified. It will remain Awaiting Verification.', 'success');
        await Promise.allSettled([loadGates(), loadCounts()]);
    } catch (error) {
        showActionError(error, 'Could not save the status.');
    } finally {
        button.disabled = false;
        button.innerHTML = original;
    }
}

function openGateAction(event) {
    const status = event.currentTarget.dataset.gateAction;
    const gateId = Number(document.getElementById('controlled-gate-select').value);
    const gate = staffState.gates.find(item => Number(item.id) === gateId);
    if (!gate || !isAssigned(gate.id)) {
        RailSafeUI.toast('Choose one of your assigned crossings first.', 'warning');
        return;
    }

    staffState.gateAction = { gate, status };
    document.getElementById('gate-action-gate-id').value = gate.id;
    document.getElementById('gate-action-status').value = status;
    const summary = document.getElementById('gate-action-summary');
    summary.innerHTML = `
        <div class="fw-bold fs-5">${status === 'OPEN' ? 'OPEN GATE' : 'CLOSE GATE'}</div>
        <div class="mt-1">${RailSafeUI.escapeHtml(
            `${gate.gateCode} – ${gate.name}`)}</div>
        <div class="text-muted-rs mt-1">
            Current displayed status: ${RailSafeUI.escapeHtml(
                RailSafeUI.notAvailable(gate.displayStatus || gate.status))}
        </div>`;
    document.getElementById('gate-action-note').value = '';
    const confirm = document.getElementById('gate-action-confirm');
    confirm.className = `btn ${status === 'OPEN' ? 'btn-success' : 'btn-danger'}`;
    bootstrap.Modal.getOrCreateInstance(document.getElementById('gateActionModal')).show();
}

async function submitGateAction(event) {
    event.preventDefault();
    const action = staffState.gateAction;
    if (!action || !isAssigned(action.gate.id)) {
        RailSafeUI.toast('You can only record a status for an assigned crossing.', 'warning');
        return;
    }

    const note = document.getElementById('gate-action-note').value.trim();
    const payload = {
        status: action.status,
        verificationStatus: 'VERIFIED',
        note: note || `Staff confirmed ${action.status} at ${action.gate.gateCode}.`
    };
    const button = document.getElementById('gate-action-confirm');
    const original = button.innerHTML;
    button.disabled = true;
    button.innerHTML = '<span class="spinner-border spinner-border-sm me-1"></span>Recording…';
    try {
        await RailSafeAPI.apiFetch(`/api/gates/${action.gate.id}/verification`, {
            method: 'PUT',
            body: JSON.stringify(payload)
        });
        bootstrap.Modal.getOrCreateInstance(document.getElementById('gateActionModal')).hide();
        RailSafeUI.toast(`${action.status === 'OPEN' ? 'OPEN' : 'CLOSED'} recorded and broadcast.`, 'success');
        staffState.gateAction = null;
        await Promise.allSettled([loadGates(), loadCounts(), loadActivity()]);
    } catch (error) {
        showActionError(error, 'Could not record the gate status.');
    } finally {
        button.disabled = false;
        button.innerHTML = original;
    }
}

async function loadHistory(gate) {
    try {
        const history = await RailSafeAPI.apiFetch(`/api/gates/${gate.id}/history`);
        const items = Array.isArray(history) ? history : (history && history.events) || [];
        const body = document.getElementById('history-body');
        document.getElementById('history-modal-title').textContent = `Status history – ${gate.name}`;
        body.innerHTML = items.length ? `<div class="timeline-rs">${items.map(item => `
            <div class="timeline-item-rs">
                <div class="fw-bold" style="font-size:.82rem;">
                    ${RailSafeUI.statusBadge(item.newStatus || item.status || item.toStatus)}
                </div>
                <div class="text-muted-rs" style="font-size:.74rem;">
                    ${item.changedAt || item.timestamp || item.createdAt
                        ? RailSafeUI.escapeHtml(RailSafeUI.formatDateTime(
                            item.changedAt || item.timestamp || item.createdAt))
                        : 'Not Available'}
                    · ${RailSafeUI.escapeHtml(RailSafeUI.notAvailable(item.action))}
                    · ${RailSafeUI.escapeHtml(RailSafeUI.notAvailable(item.source))}
                    · ${RailSafeUI.escapeHtml(RailSafeUI.notAvailable(item.changedBy || item.updatedBy))}
                </div>
                ${item.note ? `<div class="mt-1" style="font-size:.78rem;">${RailSafeUI.escapeHtml(item.note)}</div>` : ''}
            </div>`).join('')}</div>` : `
            <div class="empty-state">
                <i class="bi bi-clock-history"></i>
                <p class="fw-bold text-dark mb-1">No history recorded</p>
                <p>Status changes will appear here.</p>
            </div>`;
        bootstrap.Modal.getOrCreateInstance(document.getElementById('historyModal')).show();
    } catch (error) {
        showActionError(error, 'Could not load status history.');
    }
}

async function loadReports() {
    try {
        const reports = await RailSafeAPI.apiFetch('/api/reports');
        staffState.reports = Array.isArray(reports) ? reports : [];
        renderReports();
    } catch (error) {
        showLoadError(error, 'Could not load commuter reports.');
        document.getElementById('reports-body').innerHTML = `
            <tr><td colspan="6"><div class="empty-state">
                <i class="bi bi-wifi-off"></i><p>${RailSafeUI.escapeHtml(error.message)}</p>
            </div></td></tr>`;
    }
}

function renderReports() {
    const body = document.getElementById('reports-body');
    if (!body) return;
    const filter = document.getElementById('report-filter').value;
    const reports = staffState.reports.filter(report =>
        isAssigned(report.gateId) &&
        (filter === 'ALL' || String(report.status || 'PENDING').toUpperCase() === filter));

    const pending = staffState.reports.filter(report =>
        isAssigned(report.gateId) &&
        String(report.status || 'PENDING').toUpperCase() === 'PENDING').length;
    const badge = document.getElementById('pending-tab-badge');
    badge.textContent = pending;
    badge.style.display = pending > 0 ? 'inline-block' : 'none';

    if (!reports.length) {
        body.innerHTML = `<tr><td colspan="6"><div class="empty-state">
            <i class="bi bi-inbox"></i>
            <p class="fw-bold text-dark mb-1">No reports</p>
            <p>Reports for your assigned crossings appear here.</p>
        </div></td></tr>`;
        return;
    }

    body.innerHTML = reports.map(report => {
        const status = String(report.status || 'PENDING').toUpperCase();
        const badgeClass = status === 'APPROVED' ? 'text-bg-success'
            : status === 'REJECTED' ? 'text-bg-danger' : 'text-bg-warning';
        const canDecide = status === 'PENDING';
        return `<tr>
            <td>
                <div class="fw-bold">${RailSafeUI.escapeHtml(
                    RailSafeUI.notAvailable(report.gateName || report.gateCode))}</div>
                <div class="text-muted-rs" style="font-size:.72rem;">${RailSafeUI.escapeHtml(
                    RailSafeUI.notAvailable(report.gateCode))}</div>
            </td>
            <td style="font-size:.8rem;">${RailSafeUI.escapeHtml(
                RailSafeUI.notAvailable(report.reportType || report.category))}</td>
            <td class="d-none d-lg-table-cell" style="font-size:.78rem;">${RailSafeUI.escapeHtml(
                RailSafeUI.notAvailable(report.reportedBy || report.reporterName))}</td>
            <td class="d-none d-md-table-cell" style="font-size:.76rem;">
                ${report.createdAt ? RailSafeUI.escapeHtml(
                    RailSafeUI.formatDateTime(report.createdAt)) : 'Not Available'}
            </td>
            <td><span class="badge ${badgeClass}">${RailSafeUI.escapeHtml(status)}</span>
                ${report.reviewNote ? `<div class="text-muted-rs mt-1" style="font-size:.7rem;">${RailSafeUI.escapeHtml(
                    report.reviewNote)}</div>` : ''}
            </td>
            <td class="text-end">
                ${canDecide ? `<div class="d-flex justify-content-end gap-1">
                    <button class="btn btn-sm btn-outline-success" data-action="approve" data-id="${report.id}"
                            title="Approve"><i class="bi bi-check-lg"></i></button>
                    <button class="btn btn-sm btn-outline-danger" data-action="reject" data-id="${report.id}"
                            title="Reject"><i class="bi bi-x-lg"></i></button>
                </div>` : '<span class="text-muted-rs" style="font-size:.74rem;">Reviewed</span>'}
            </td>
        </tr>`;
    }).join('');
}

function onReportsTableClick(event) {
    const button = event.target.closest('button[data-action]');
    if (!button) return;
    const report = staffState.reports.find(item => item.id === Number(button.dataset.id));
    if (!report || !isAssigned(report.gateId)) {
        RailSafeUI.toast('You can only review reports for assigned crossings.', 'warning');
        return;
    }
    const approved = button.dataset.action === 'approve';
    staffState.reportDecision = { report, approved };
    document.getElementById('report-id').value = report.id;
    document.getElementById('report-action').value = approved ? 'APPROVED' : 'REJECTED';
    document.getElementById('report-decision-summary').innerHTML = `
        <div class="fw-bold">${RailSafeUI.escapeHtml(
            RailSafeUI.notAvailable(report.gateName || report.gateCode))}</div>
        <div class="text-muted-rs mt-1">${approved
            ? 'Approve this commuter report?' : 'Reject this commuter report?'}</div>`;
    document.getElementById('report-review-note').value = '';
    document.getElementById('report-decision-submit').className =
        `btn ${approved ? 'btn-success' : 'btn-danger'}`;
    bootstrap.Modal.getOrCreateInstance(document.getElementById('reportDecisionModal')).show();
}

async function submitReportDecision(event) {
    event.preventDefault();
    const decision = staffState.reportDecision;
    if (!decision) return;
    const action = decision.approved ? 'approve' : 'reject';
    const payload = { note: document.getElementById('report-review-note').value.trim() || null };
    const button = document.getElementById('report-decision-submit');
    const original = button.innerHTML;
    button.disabled = true;
    button.innerHTML = '<span class="spinner-border spinner-border-sm me-1"></span>Saving…';
    try {
        await RailSafeAPI.apiFetch(`/api/reports/${decision.report.id}/${action}`, {
            method: 'PUT',
            body: JSON.stringify(payload)
        });
        bootstrap.Modal.getOrCreateInstance(document.getElementById('reportDecisionModal')).hide();
        RailSafeUI.toast(`Report ${decision.approved ? 'approved' : 'rejected'}.`, 'success');
        await Promise.allSettled([loadReports(), loadCounts()]);
    } catch (error) {
        showActionError(error, 'Could not review the report.');
    } finally {
        button.disabled = false;
        button.innerHTML = original;
    }
}

async function loadTrains() {
    const body = document.getElementById('trains-body');
    if (!body) return;
    try {
        staffState.trains = await RailSafeAPI.apiFetch('/api/trains');
        renderTrains();
    } catch (error) {
        showLoadError(error, 'Could not load the train catalogue.');
        body.innerHTML = `<tr><td colspan="6"><div class="empty-state">
            <i class="bi bi-wifi-off"></i><p>${RailSafeUI.escapeHtml(error.message)}</p>
        </div></td></tr>`;
    }
}

function renderTrains() {
    const body = document.getElementById('trains-body');
    if (!body) return;
    if (!staffState.trains.length) {
        body.innerHTML = `<tr><td colspan="6"><div class="empty-state">
            <i class="bi bi-train-front"></i>
            <p class="fw-bold text-dark mb-1">No reference trains</p>
            <p>The verified catalogue is empty.</p>
        </div></td></tr>`;
        return;
    }
    body.innerHTML = staffState.trains.map(train => `<tr>
        <td><span class="badge-soft">${RailSafeUI.escapeHtml(
            RailSafeUI.notAvailable(train.trainNumber))}</span></td>
        <td>
            <div class="fw-bold">${RailSafeUI.escapeHtml(RailSafeUI.notAvailable(train.trainName))}</div>
            <div class="text-muted-rs" style="font-size:.72rem;">
                ${RailSafeUI.escapeHtml(RailSafeUI.notAvailable(train.currentStation))} →
                ${RailSafeUI.escapeHtml(RailSafeUI.notAvailable(train.nextStation))}
            </div>
        </td>
        <td class="d-none d-lg-table-cell" style="font-size:.8rem;">
            ${RailSafeUI.escapeHtml(RailSafeUI.notAvailable(train.sourceStation))} →
            ${RailSafeUI.escapeHtml(RailSafeUI.notAvailable(train.destinationStation))}
        </td>
        <td>
            <span class="badge-soft">${RailSafeUI.escapeHtml(
                RailSafeUI.notAvailable(train.runningStatus))}</span>
            <div class="text-muted-rs mt-1" style="font-size:.7rem;">
                ETA ${RailSafeUI.escapeHtml(RailSafeUI.notAvailable(train.estimatedArrival))}
            </div>
        </td>
        <td class="d-none d-lg-table-cell" style="font-size:.76rem;">
            ${RailSafeUI.escapeHtml(RailSafeUI.notAvailable(train.referenceSource))}
            <div class="text-muted-rs">${RailSafeUI.escapeHtml(
                RailSafeUI.notAvailable(train.dataClassification))}</div>
        </td>
        <td><span class="badge-live-unavailable">Live data unavailable</span></td>
    </tr>`).join('');
}

function refreshGateOptions() {
    const options = staffState.gates.length
        ? staffState.gates.map(gate => `<option value="${gate.id}">${RailSafeUI.escapeHtml(
            `${gate.gateCode} – ${gate.name}`)}</option>`).join('')
        : '<option value="">No assigned crossings</option>';

    const maintenanceSelect = document.getElementById('maintenance-gate');
    if (maintenanceSelect) {
        const previous = maintenanceSelect.value;
        maintenanceSelect.innerHTML = options;
        if (previous) maintenanceSelect.value = previous;
    }

    const controlSelect = document.getElementById('controlled-gate-select');
    if (controlSelect) {
        const previous = controlSelect.value;
        controlSelect.innerHTML = options;
        if (previous && staffState.gates.some(gate => String(gate.id) === previous)) {
            controlSelect.value = previous;
        }
    }

    const disabled = staffState.gates.length === 0;
    ['open-gate-btn', 'close-gate-btn'].forEach(id => {
        const button = document.getElementById(id);
        if (button) button.disabled = disabled;
    });
}

async function loadMaintenance() {
    const body = document.getElementById('maintenance-body');
    if (!body) return;
    try {
        const items = await RailSafeAPI.apiFetch('/api/maintenance');
        staffState.maintenance = Array.isArray(items) ? items : [];
        renderMaintenance();
    } catch (error) {
        showLoadError(error, 'Could not load maintenance requests.');
        body.innerHTML = `<tr><td colspan="6"><div class="empty-state">
            <i class="bi bi-wifi-off"></i><p>${RailSafeUI.escapeHtml(error.message)}</p>
        </div></td></tr>`;
    }
}

function maintenanceStatusBadge(status) {
    const value = String(RailSafeUI.notAvailable(status));
    const cls = value === 'RESOLVED' ? 'text-bg-success'
        : value === 'IN_PROGRESS' ? 'text-bg-primary'
            : value === 'CANCELLED' ? 'text-bg-secondary' : 'text-bg-warning';
    return `<span class="badge ${cls}">${RailSafeUI.escapeHtml(value)}</span>`;
}

function renderMaintenance() {
    const body = document.getElementById('maintenance-body');
    if (!body) return;
    const items = staffState.maintenance.filter(item => isAssigned(item.gateId));
    if (!items.length) {
        body.innerHTML = `<tr><td colspan="6"><div class="empty-state">
            <i class="bi bi-tools"></i>
            <p class="fw-bold text-dark mb-1">No maintenance requests</p>
            <p>Requests for your assigned crossings appear here.</p>
        </div></td></tr>`;
        return;
    }
    const statuses = ['PENDING', 'IN_PROGRESS', 'RESOLVED', 'CANCELLED'];
    body.innerHTML = items.map(item => {
        const options = statuses.map(status => `<option value="${status}" ${
            String(item.status || '').toUpperCase() === status ? 'selected' : ''}>${status}</option>`).join('');
        return `<tr>
            <td>
                <div class="fw-bold">${RailSafeUI.escapeHtml(
                    RailSafeUI.notAvailable(item.title || item.description))}</div>
                ${item.description ? `<div class="text-muted-rs mt-1" style="font-size:.72rem;">${RailSafeUI.escapeHtml(
                    item.description)}</div>` : ''}
            </td>
            <td>${RailSafeUI.escapeHtml(RailSafeUI.notAvailable(item.gateName || item.gateCode || item.gateId))}</td>
            <td><span class="badge-soft">${RailSafeUI.escapeHtml(
                RailSafeUI.notAvailable(item.priority))}</span></td>
            <td>${maintenanceStatusBadge(item.status)}</td>
            <td class="d-none d-md-table-cell" style="font-size:.76rem;">
                ${item.createdAt ? RailSafeUI.escapeHtml(
                    RailSafeUI.formatDateTime(item.createdAt)) : 'Not Available'}
            </td>
            <td class="text-end">
                <div class="d-flex justify-content-end gap-1">
                    <select class="form-select form-select-sm" id="maint-status-${item.id}" style="width:auto;"
                            aria-label="Status for request ${item.id}">${options}</select>
                    <button class="btn btn-sm btn-outline-navy" data-action="status" data-id="${item.id}"
                            title="Update status"><i class="bi bi-check2"></i></button>
                </div>
            </td>
        </tr>`;
    }).join('');
}

function openMaintenanceModal() {
    refreshGateOptions();
    document.getElementById('maintenance-form').reset();
    if (!staffState.gates.length) {
        RailSafeUI.toast('No crossings are assigned to you.', 'warning');
        return;
    }
    bootstrap.Modal.getOrCreateInstance(document.getElementById('maintenanceModal')).show();
}

async function saveMaintenance(event) {
    event.preventDefault();
    const gateId = Number(document.getElementById('maintenance-gate').value);
    const title = document.getElementById('maintenance-title').value.trim();
    if (!isAssigned(gateId)) {
        RailSafeUI.toast('You can only raise requests for assigned crossings.', 'warning');
        return;
    }
    if (!gateId || !title) {
        RailSafeUI.toast('Choose a crossing and enter a title.', 'warning');
        return;
    }
    const payload = {
        gateId,
        title,
        description: document.getElementById('maintenance-description').value.trim() || null,
        priority: document.getElementById('maintenance-priority').value
    };
    const button = document.getElementById('maintenance-save');
    const original = button.innerHTML;
    button.disabled = true;
    button.innerHTML = '<span class="spinner-border spinner-border-sm me-1"></span>Saving…';
    try {
        await RailSafeAPI.apiFetch('/api/maintenance', {
            method: 'POST',
            body: JSON.stringify(payload)
        });
        bootstrap.Modal.getOrCreateInstance(document.getElementById('maintenanceModal')).hide();
        RailSafeUI.toast('Maintenance request created.', 'success');
        await loadMaintenance();
    } catch (error) {
        showActionError(error, 'Could not create the maintenance request.');
    } finally {
        button.disabled = false;
        button.innerHTML = original;
    }
}

function onMaintenanceTableClick(event) {
    const button = event.target.closest('button[data-action]');
    if (!button || button.dataset.action !== 'status') return;
    const item = staffState.maintenance.find(row => row.id === Number(button.dataset.id));
    if (!item || !isAssigned(item.gateId)) {
        RailSafeUI.toast('You can only update requests for assigned crossings.', 'warning');
        return;
    }
    const select = document.getElementById(`maint-status-${button.dataset.id}`);
    if (select) void updateMaintenanceStatus(item.id, select.value);
}

async function updateMaintenanceStatus(id, status) {
    if (!status) return;
    try {
        await RailSafeAPI.apiFetch(`/api/maintenance/${id}/status`, {
            method: 'PUT',
            body: JSON.stringify({ status })
        });
        RailSafeUI.toast('Maintenance status updated.', 'success');
        await loadMaintenance();
    } catch (error) {
        showActionError(error, 'Could not update the maintenance status.');
    }
}

async function loadActivity() {
    const body = document.getElementById('staff-activity-body');
    if (!body) return;
    try {
        staffState.activity = await RailSafeAPI.apiFetch('/api/staff/activity');
        renderActivity();
    } catch (error) {
        showLoadError(error, 'Could not load your activity history.');
        body.innerHTML = `<tr><td colspan="6"><div class="empty-state">
            <i class="bi bi-wifi-off"></i><p>${RailSafeUI.escapeHtml(error.message)}</p>
        </div></td></tr>`;
    }
}

function renderActivity() {
    const body = document.getElementById('staff-activity-body');
    if (!body) return;
    if (!staffState.activity.length) {
        body.innerHTML = `<tr><td colspan="6"><div class="empty-state">
            <i class="bi bi-clock-history"></i>
            <p class="fw-bold text-dark mb-1">No activity recorded yet</p>
            <p>Your OPEN and CLOSE confirmations will appear here.</p>
        </div></td></tr>`;
        return;
    }
    body.innerHTML = staffState.activity.map(item => {
        const gate = staffState.gates.find(candidate => Number(candidate.id) === Number(item.gateId));
        const crossing = gate
            ? `${gate.gateCode} – ${gate.name}`
            : (item.gateCode || `Gate ${item.gateId || ''}`).trim();
        return `<tr>
            <td style="font-size:.76rem;">
                ${item.changedAt ? RailSafeUI.escapeHtml(RailSafeUI.formatDateTime(item.changedAt)) : 'Not Available'}
            </td>
            <td style="font-size:.78rem;">${RailSafeUI.escapeHtml(crossing)}</td>
            <td style="font-size:.76rem;">${RailSafeUI.escapeHtml(
                RailSafeUI.notAvailable(item.action))}</td>
            <td>${item.oldStatus ? RailSafeUI.statusBadge(item.oldStatus) : '<span class="text-muted-rs">–</span>'}</td>
            <td>${item.newStatus ? RailSafeUI.statusBadge(item.newStatus) : '<span class="text-muted-rs">–</span>'}</td>
            <td class="d-none d-lg-table-cell" style="font-size:.74rem;">
                ${item.note ? RailSafeUI.escapeHtml(item.note) : '—'}
            </td>
        </tr>`;
    }).join('');
}

function renderProfile() {
    const container = document.getElementById('staff-profile-detail');
    if (!container || !staffState.user) return;
    const user = staffState.user;
    const rows = [
        ['Full name', user.fullName],
        ['Email', user.email],
        ['Role', user.role],
        ['Phone', user.phone || 'Not Available']
    ];
    container.innerHTML = rows.map(([label, value]) => `
        <div><dt>${RailSafeUI.escapeHtml(label)}</dt><dd>${RailSafeUI.escapeHtml(
            RailSafeUI.notAvailable(value))}</dd></div>
    `).join('');
}
