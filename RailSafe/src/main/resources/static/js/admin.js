/* RailSafe staff/admin dashboard: gate CRUD, audited status updates,
   history viewer and commuter report moderation. */
const adminState = {
    user: null,
    isStaff: false,
    isAdmin: false,
    gates: [],
    assignedGateIds: new Set(),
    reports: [],
    trains: [],
    staff: [],
    staffActivity: [],
    maintenance: [],
    stats: null,
    liveCounts: null,
    liveSource: null,
    staffEditingId: null,
    gateSearch: '',
    gateStatus: 'ALL',
    reportFilter: 'PENDING'
};

document.addEventListener('DOMContentLoaded', async () => {
    const user = await RailSafeUI.initShell('admin');
    if (!RailSafeUI.guard(user, ['ADMIN', 'SUPER_ADMIN'])) return;
    adminState.user = user;
    adminState.isStaff = user.role === 'STAFF';
    adminState.isAdmin = ['ADMIN', 'SUPER_ADMIN'].includes(user.role);
    document.getElementById('admin-role').textContent = user.role;

    // STAFF never receives the admin-only controls, even if the server renders them.
    if (adminState.isStaff) {
        document.querySelectorAll('.admin-only').forEach(element => element.remove());
    }
    renderProfile();

    bindAdminUi();
    await refreshAll();
    adminState.liveSource = RailSafeUI.startLiveStream(loadStats);
    RailSafeUI.startSimulationPanel('simulation-body');
});

function bindAdminUi() {
    document.getElementById('refresh-all').addEventListener('click', async () => {
        await refreshAll();
        RailSafeUI.toast('Dashboard refreshed.', 'success');
    });
    document.getElementById('refresh-gates').addEventListener('click', loadGates);
    document.getElementById('refresh-reports').addEventListener('click', loadReports);
    document.getElementById('add-gate').addEventListener('click', () => openGateModal(null));

    document.getElementById('gate-search').addEventListener('input', event => {
        adminState.gateSearch = event.target.value.trim().toLowerCase();
        renderGates();
    });
    document.getElementById('gate-status-filter').addEventListener('change', event => {
        adminState.gateStatus = event.target.value;
        renderGates();
    });
    document.getElementById('gate-table-body').addEventListener('click', onGateTableClick);

    document.getElementById('report-filter').addEventListener('change', event => {
        adminState.reportFilter = event.target.value;
        renderReports();
    });
    document.getElementById('reports-body').addEventListener('click', onReportsTableClick);

    document.getElementById('gate-form').addEventListener('submit', saveGate);
    document.getElementById('status-form').addEventListener('submit', saveStatus);
    document.getElementById('report-decision-form').addEventListener('submit', submitReportDecision);

    const trainsRefresh = document.getElementById('refresh-trains');
    if (trainsRefresh) trainsRefresh.addEventListener('click', loadTrains);

    const staffRefresh = document.getElementById('refresh-staff');
    if (staffRefresh) staffRefresh.addEventListener('click', loadStaff);
    const staffForm = document.getElementById('staff-form');
    if (staffForm) {
        staffForm.addEventListener('submit', saveStaff);
        document.getElementById('staff-cancel').addEventListener('click', resetStaffForm);
        document.getElementById('staff-body').addEventListener('click', onStaffTableClick);
        document.getElementById('staff-assign-form').addEventListener('submit', saveStaffAssignments);
        document.getElementById('refresh-staff-activity').addEventListener('click', loadStaffActivity);
        document.getElementById('staff-activity-filter').addEventListener('change', renderStaffActivity);
    }

    const maintenanceRefresh = document.getElementById('refresh-maintenance');
    if (maintenanceRefresh) {
        maintenanceRefresh.addEventListener('click', loadMaintenance);
        document.getElementById('add-maintenance').addEventListener('click', openMaintenanceModal);
        document.getElementById('maintenance-form').addEventListener('submit', saveMaintenance);
        document.getElementById('maintenance-body').addEventListener('click', onMaintenanceTableClick);
    }
}

async function refreshAll() {
    await Promise.allSettled([
        loadStats(), loadGates(), loadReports(), loadTrains(),
        loadStaff(), loadStaffActivity(), loadMaintenance()
    ]);
}

/* ------------------------------------------------------------------ */
/* Statistics                                                          */
/* ------------------------------------------------------------------ */

async function loadStats() {
    const countsUrl = adminState.isStaff ? '/api/staff/counts' : '/api/live/counts';
    try {
        const counts = await RailSafeAPI.apiFetch(countsUrl);
        adminState.liveCounts = counts;
        applyLiveCounts(counts);
    } catch (error) {
        adminState.liveCounts = null;
        applyLiveCounts(null);
        handleLoadError(error, 'Could not load live counts.');
    }

    if (adminState.isStaff) return;
    try {
        const stats = await RailSafeAPI.apiFetch('/api/admin/stats');
        adminState.stats = stats;
        setStatText('stat-unknown', stats.unknownGates);
        setStatText('stat-stale', stats.staleGates);
        setStatText('stat-pending', stats.pendingReports);
        setStatText('stat-users', stats.totalUsers);

        const badge = document.getElementById('pending-tab-badge');
        if (badge) {
            badge.textContent = stats.pendingReports;
            badge.style.display = stats.pendingReports > 0 ? 'inline-block' : 'none';
        }
    } catch (error) {
        handleLoadError(error, 'Could not load dashboard statistics.');
    }
}

function setStatText(id, value) {
    const element = document.getElementById(id);
    if (element) element.textContent = value === null || value === undefined ? 'Not Available' : value;
}

function applyLiveCounts(counts) {
    setStatText('stat-total', counts && counts.totalGates);
    setStatText('stat-open', counts && counts.openGates);
    setStatText('stat-closed', counts && counts.closedGates);
    setStatText('stat-closing', counts && counts.closingGates);
    setStatText('stat-opening', counts && counts.openingGates);
    setStatText('stat-awaiting', counts && counts.awaitingVerificationGates);
    setStatText('stat-approaching', counts && counts.approachingTrains);
    setStatText('stat-active', counts && counts.activeCrossings);
    RailSafeUI.renderLiveIndicator(document.getElementById('admin-live-indicator'), counts);
    RailSafeUI.renderSyncTime(document.getElementById('admin-last-sync'), counts);
}

/* ------------------------------------------------------------------ */
/* Gates                                                               */
/* ------------------------------------------------------------------ */

async function loadGates() {
    try {
        adminState.gates = await RailSafeAPI.apiFetch(
            adminState.isStaff ? '/api/staff/gates' : '/api/gates');
        adminState.assignedGateIds = new Set(adminState.gates.map(gate => gate.id));
        renderGates();
        refreshGateOptions();
    } catch (error) {
        handleLoadError(error, 'Could not load crossings.');
        document.getElementById('gate-table-body').innerHTML = `
            <tr><td colspan="7"><div class="empty-state">
                <i class="bi bi-wifi-off"></i><p>${RailSafeUI.escapeHtml(error.message)}</p>
            </div></td></tr>`;
    }
}

function renderGates() {
    const body = document.getElementById('gate-table-body');
    const search = adminState.gateSearch;
    const filtered = adminState.gates.filter(gate => {
        const matchesStatus = adminState.gateStatus === 'ALL' ||
            gate.displayStatus === adminState.gateStatus;
        const haystack = `${gate.gateCode} ${gate.name} ${gate.location}`.toLowerCase();
        return matchesStatus && (!search || haystack.includes(search));
    });

    if (!filtered.length) {
        body.innerHTML = `<tr><td colspan="7">
            <div class="empty-state">
                <i class="bi bi-signpost"></i>
                <p class="fw-bold text-dark mb-1">No crossings match</p>
                <p>Adjust the search or status filter, or add a crossing.</p>
            </div>
        </td></tr>`;
        return;
    }

    body.innerHTML = filtered.map(gate => {
        const staleNote = gate.stale
            ? `<div class="text-muted-rs" style="font-size:.68rem;">stored ${gate.status} · stale</div>`
            : '';
        const awaitingBadge = gate.verificationStatus !== 'VERIFIED'
            ? RailSafeUI.awaitingBadge() : '';
        const canManage = adminState.isAdmin && !adminState.isStaff;
        const editButton = canManage
            ? `<button class="btn btn-sm btn-soft" data-action="edit" data-id="${gate.id}"
                        title="Edit crossing" aria-label="Edit ${RailSafeUI.escapeHtml(gate.name)}">
                    <i class="bi bi-pencil"></i>
                </button>`
            : '';
        const deleteButton = canManage
            ? `<button class="btn btn-sm btn-outline-danger" data-action="delete" data-id="${gate.id}"
                       title="Delete crossing" aria-label="Delete ${RailSafeUI.escapeHtml(gate.name)}">
                   <i class="bi bi-trash"></i>
               </button>`
            : '';
        return `<tr>
            <td><span class="badge-soft">${RailSafeUI.escapeHtml(gate.gateCode)}</span></td>
            <td>
                <div class="fw-bold">${RailSafeUI.escapeHtml(gate.name)}</div>
                <div class="text-muted-rs" style="font-size:.72rem;">${RailSafeUI.escapeHtml(gate.location)}</div>
            </td>
            <td>
                ${RailSafeUI.statusBadge(gate.displayStatus)}
                ${staleNote}
                 ${awaitingBadge}
            </td>
            <td class="d-none d-lg-table-cell">${RailSafeUI.escapeHtml(RailSafeUI.sourceLabel(gate.statusSource))}</td>
            <td class="d-none d-lg-table-cell">${RailSafeUI.verificationBadge(gate.verificationStatus)}</td>
            <td class="d-none d-md-table-cell">
                <div style="font-size:.76rem;">${RailSafeUI.escapeHtml(RailSafeUI.formatDateTime(gate.lastUpdatedAt))}</div>
                <div class="text-muted-rs" style="font-size:.68rem;">${RailSafeUI.timeAgo(gate.lastUpdatedAt)}</div>
            </td>
            <td class="text-end">
                <div class="d-flex justify-content-end gap-1">
                    ${editButton}
                    <button class="btn btn-sm btn-outline-navy" data-action="verify" data-id="${gate.id}"
                            title="Verify / set OPEN or CLOSED status"
                            aria-label="Verify status for ${RailSafeUI.escapeHtml(gate.name)}">
                        <i class="bi bi-check2-square me-1"></i>Verify / Set status
                    </button>
                    <button class="btn btn-sm btn-outline-navy" data-action="history" data-id="${gate.id}"
                            title="View status history" aria-label="History for ${RailSafeUI.escapeHtml(gate.name)}">
                        <i class="bi bi-clock-history"></i>
                    </button>
                    ${deleteButton}
                </div>
            </td>
        </tr>`;
    }).join('');
}

function onGateTableClick(event) {
    const button = event.target.closest('button[data-action]');
    if (!button) return;
    const gate = adminState.gates.find(item => item.id === Number(button.dataset.id));
    if (!gate) return;

    switch (button.dataset.action) {
        case 'edit':
            openGateModal(gate);
            break;
        case 'verify':
        case 'status':
            openStatusModal(gate);
            break;
        case 'history':
            openHistoryModal(gate);
            break;
        case 'delete':
            deleteGate(gate);
            break;
    }
}

function openGateModal(gate) {
    const form = document.getElementById('gate-form');
    form.reset();
    document.getElementById('gate-id').value = gate ? gate.id : '';
    document.getElementById('gate-modal-title').textContent = gate ? 'Edit crossing' : 'Add crossing';

    if (gate) {
        document.getElementById('gate-code').value = gate.gateCode;
        document.getElementById('gate-name').value = gate.name;
        document.getElementById('gate-latitude').value = gate.latitude;
        document.getElementById('gate-longitude').value = gate.longitude;
        document.getElementById('gate-location').value = gate.location;
        document.getElementById('gate-notes').value = gate.notes || '';
    } else {
        document.getElementById('gate-latitude').value = 17.3850;
        document.getElementById('gate-longitude').value = 78.4867;
    }

    bootstrap.Modal.getOrCreateInstance(document.getElementById('gateModal')).show();
}

async function saveGate(event) {
    event.preventDefault();
    const id = document.getElementById('gate-id').value;
    const latitude = Number(document.getElementById('gate-latitude').value);
    const longitude = Number(document.getElementById('gate-longitude').value);

    if (!Number.isFinite(latitude) || latitude < -90 || latitude > 90 ||
        !Number.isFinite(longitude) || longitude < -180 || longitude > 180) {
        RailSafeUI.toast('Enter valid latitude and longitude values.', 'warning');
        return;
    }

    const body = {
        gateCode: document.getElementById('gate-code').value.trim(),
        name: document.getElementById('gate-name').value.trim(),
        latitude,
        longitude,
        location: document.getElementById('gate-location').value.trim(),
        notes: document.getElementById('gate-notes').value.trim() || null
    };

    const button = document.getElementById('gate-save');
    const original = button.innerHTML;
    button.disabled = true;
    button.innerHTML = '<span class="spinner-border spinner-border-sm me-1"></span>Saving…';

    try {
        const saved = await RailSafeAPI.apiFetch(id ? `/api/gates/${id}` : '/api/gates', {
            method: id ? 'PUT' : 'POST',
            body: JSON.stringify(body)
        });

        bootstrap.Modal.getOrCreateInstance(document.getElementById('gateModal')).hide();
        RailSafeUI.toast(id ? 'Crossing updated.' : 'Crossing created as UNKNOWN.', 'success');
        await Promise.allSettled([loadGates(), loadStats()]);

        if (!id) {
            const created = adminState.gates.find(gate => gate.id === saved.id) || saved;
            RailSafeUI.toast('Record the first status when you are ready.', 'info');
            openStatusModal(created);
        }
    } catch (error) {
        handleActionError(error, 'Could not save the crossing.');
    } finally {
        button.disabled = false;
        button.innerHTML = original;
    }
}

async function deleteGate(gate) {
    if (!confirm(`Delete “${gate.name}”? This also removes its status history, reports and notifications.`)) {
        return;
    }
    try {
        await RailSafeAPI.apiFetch(`/api/gates/${gate.id}`, { method: 'DELETE' });
        RailSafeUI.toast('Crossing deleted.', 'success');
        await Promise.allSettled([loadGates(), loadStats(), loadReports()]);
    } catch (error) {
        handleActionError(error, 'Could not delete the crossing.');
    }
}

/* ------------------------------------------------------------------ */
/* Status updates and history                                          */
/* ------------------------------------------------------------------ */

function openStatusModal(gate) {
    document.getElementById('status-form').reset();
    document.getElementById('status-gate-id').value = gate.id;
    document.getElementById('status-gate-name').textContent = `${gate.name} (${gate.gateCode})`;
    const observable = ['OPEN', 'CLOSED', 'CLOSING', 'OPENING', 'MAINTENANCE'];
    document.getElementById('status-value').value = observable.includes(gate.status) ? gate.status : '';
    document.getElementById('status-source').value =
        gate.statusSource === 'SENSOR' ? 'SENSOR' : 'STAFF';
    document.getElementById('status-verification').value =
        gate.verificationStatus === 'VERIFIED' ? 'VERIFIED' : 'UNVERIFIED';
    document.getElementById('status-note').value = '';

    const note = document.getElementById('status-stale-note');
    if (gate.stale) {
        note.innerHTML = `<i class="bi bi-clock-history me-1"></i>
            This crossing is currently <strong>stale</strong> (stored ${RailSafeUI.statusBadge(gate.status)}).
            Saving a status refreshes the timestamp and clears the stale state.`;
    } else {
        note.innerHTML = `<i class="bi bi-info-circle me-1"></i>
            Updating the status refreshes the last-updated time. Only authorised sources and verified
            states are treated as current.`;
    }

    bootstrap.Modal.getOrCreateInstance(document.getElementById('statusModal')).show();
}

async function saveStatus(event) {
    event.preventDefault();
    const id = document.getElementById('status-gate-id').value;
    if (!id) return;

    const verificationStatus = document.getElementById('status-verification').value;
    const body = {
        status: document.getElementById('status-value').value,
        verificationStatus,
        note: document.getElementById('status-note').value.trim() || null
    };

    const button = document.getElementById('status-save');
    const original = button.innerHTML;
    button.disabled = true;
    button.innerHTML = '<span class="spinner-border spinner-border-sm me-1"></span>Saving…';

    try {
        await RailSafeAPI.apiFetch(`/api/gates/${id}/verification`, {
            method: 'PUT',
            body: JSON.stringify(body)
        });
        bootstrap.Modal.getOrCreateInstance(document.getElementById('statusModal')).hide();
        RailSafeUI.toast(verificationStatus === 'VERIFIED'
            ? 'Verified status saved. Dashboards and notifications were updated.'
            : 'Saved as unverified. The crossing remains Awaiting Verification.', 'success');
        await Promise.allSettled([loadGates(), loadStats()]);
    } catch (error) {
        handleActionError(error, 'Could not update the status.');
    } finally {
        button.disabled = false;
        button.innerHTML = original;
    }
}

async function openHistoryModal(gate) {
    document.getElementById('history-modal-title').textContent = `Status history – ${gate.name}`;
    const body = document.getElementById('history-body');
    RailSafeUI.loading(body, 'Loading status history…');
    bootstrap.Modal.getOrCreateInstance(document.getElementById('historyModal')).show();

    try {
        const history = await RailSafeAPI.apiFetch(`/api/gates/${gate.id}/history`);
        if (!history.length) {
            RailSafeUI.emptyState(body, 'bi-clock-history', 'No status history',
                'Status changes will appear here.');
            return;
        }

        body.innerHTML = history.map(item => `
            <div class="history-item">
                <div class="d-flex flex-wrap align-items-center gap-2">
                    <span class="history-title">
                        ${item.oldStatus
                            ? `${RailSafeUI.statusBadge(item.oldStatus)} <i class="bi bi-arrow-right mx-1"></i>`
                            : '<span class="badge-soft">Initial</span> <i class="bi bi-arrow-right mx-1"></i>'}
                        ${RailSafeUI.statusBadge(item.newStatus)}
                    </span>
                    <span class="badge-soft">${RailSafeUI.escapeHtml(RailSafeUI.sourceLabel(item.source))}</span>
                </div>
                <div class="history-meta mt-1">
                    <i class="bi bi-clock me-1"></i>${RailSafeUI.escapeHtml(RailSafeUI.formatDateTime(item.changedAt))}
                    <span class="mx-1">·</span>
                    <i class="bi bi-person me-1"></i>${RailSafeUI.escapeHtml(item.changedBy || 'System')}
                </div>
                ${item.note
                    ? `<div class="history-meta mt-1"><i class="bi bi-chat-left-text me-1"></i>${RailSafeUI.escapeHtml(item.note)}</div>`
                    : ''}
            </div>
        `).join('');
    } catch (error) {
        RailSafeUI.emptyState(body, 'bi-wifi-off', 'Could not load history', error.message);
    }
}

/* ------------------------------------------------------------------ */
/* Commuter reports                                                    */
/* ------------------------------------------------------------------ */

async function loadReports() {
    try {
        adminState.reports = await RailSafeAPI.apiFetch('/api/reports');
        renderReports();
        const pending = adminState.reports.filter(report => report.status === 'PENDING').length;
        const badge = document.getElementById('pending-tab-badge');
        badge.textContent = pending;
        badge.style.display = pending > 0 ? 'inline-block' : 'none';
    } catch (error) {
        handleLoadError(error, 'Could not load commuter reports.');
        document.getElementById('reports-body').innerHTML = `
            <tr><td colspan="6"><div class="empty-state">
                <i class="bi bi-wifi-off"></i><p>${RailSafeUI.escapeHtml(error.message)}</p>
            </div></td></tr>`;
    }
}

function renderReports() {
    const body = document.getElementById('reports-body');
    const filtered = adminState.reportFilter === 'ALL'
        ? adminState.reports
        : adminState.reports.filter(report => report.status === adminState.reportFilter);

    if (!filtered.length) {
        body.innerHTML = `<tr><td colspan="6">
            <div class="empty-state">
                <i class="bi bi-inbox"></i>
                <p class="fw-bold text-dark mb-1">No reports in this view</p>
                <p>${adminState.reportFilter === 'PENDING'
                    ? 'There are no commuter reports waiting for review.'
                    : 'Try another report filter.'}</p>
            </div>
        </td></tr>`;
        return;
    }

    body.innerHTML = filtered.map(report => {
        const statusClass = report.status === 'APPROVED' ? 'success'
            : report.status === 'REJECTED' ? 'danger' : 'warning';
        const actions = report.status === 'PENDING'
            ? `<div class="d-flex justify-content-end gap-1">
                   <button class="btn btn-sm btn-success" data-action="approve" data-id="${report.id}"
                           title="Approve report" aria-label="Approve report">
                       <i class="bi bi-check2"></i>
                   </button>
                   <button class="btn btn-sm btn-outline-danger" data-action="reject" data-id="${report.id}"
                           title="Reject report" aria-label="Reject report">
                       <i class="bi bi-x-lg"></i>
                   </button>
               </div>`
            : `<span class="text-muted-rs" style="font-size:.72rem;">Reviewed</span>`;
        const reviewNote = report.reviewNote
            ? `<div class="text-muted-rs mt-1" style="font-size:.68rem;">Note: ${RailSafeUI.escapeHtml(report.reviewNote)}</div>`
            : '';
        return `<tr>
            <td>
                <div class="fw-bold">${RailSafeUI.escapeHtml(report.gateName)}</div>
                <div class="text-muted-rs" style="font-size:.7rem;">${RailSafeUI.escapeHtml(report.gateCode)}</div>
                ${report.comment
                    ? `<div class="text-muted-rs mt-1" style="font-size:.7rem;max-width:280px;">“${RailSafeUI.escapeHtml(report.comment)}”</div>`
                    : ''}
            </td>
            <td>${RailSafeUI.statusBadge(report.reportedStatus)}</td>
            <td class="d-none d-lg-table-cell">
                <div style="font-size:.78rem;">${RailSafeUI.escapeHtml(report.userName)}</div>
            </td>
            <td class="d-none d-md-table-cell">
                <div style="font-size:.74rem;">${RailSafeUI.escapeHtml(RailSafeUI.formatDateTime(report.createdAt))}</div>
                <div class="text-muted-rs" style="font-size:.68rem;">${RailSafeUI.timeAgo(report.createdAt)}</div>
            </td>
            <td>
                <span class="badge text-bg-${statusClass}">${report.status}</span>
                ${report.reviewedBy
                    ? `<div class="text-muted-rs mt-1" style="font-size:.68rem;">by ${RailSafeUI.escapeHtml(report.reviewedBy)}</div>`
                    : ''}
                ${reviewNote}
            </td>
            <td class="text-end">${actions}</td>
        </tr>`;
    }).join('');
}

function onReportsTableClick(event) {
    const button = event.target.closest('button[data-action]');
    if (!button) return;
    const report = adminState.reports.find(item => item.id === Number(button.dataset.id));
    if (!report) return;
    openReportDecision(report, button.dataset.action);
}

function openReportDecision(report, action) {
    const approving = action === 'approve';
    document.getElementById('report-decision-form').reset();
    document.getElementById('report-id').value = report.id;
    document.getElementById('report-action').value = action;
    document.getElementById('report-decision-title').textContent =
        approving ? 'Approve commuter report' : 'Reject commuter report';
    document.getElementById('report-decision-summary').innerHTML = `
        <strong>${RailSafeUI.escapeHtml(report.gateName)}</strong>
        <span class="badge-soft ms-1">${RailSafeUI.escapeHtml(report.gateCode)}</span>
        <div class="mt-2">Reported status: ${RailSafeUI.statusBadge(report.reportedStatus)}</div>
        <div class="mt-1">Reporter: ${RailSafeUI.escapeHtml(report.userName)} ·
            ${RailSafeUI.escapeHtml(RailSafeUI.formatDateTime(report.createdAt))}</div>
        ${report.comment
            ? `<div class="mt-2">“${RailSafeUI.escapeHtml(report.comment)}”</div>`
            : ''}`;

    const submit = document.getElementById('report-decision-submit');
    submit.className = approving ? 'btn btn-success' : 'btn btn-danger';
    submit.innerHTML = approving
        ? '<i class="bi bi-check2 me-1"></i>Approve report'
        : '<i class="bi bi-x-lg me-1"></i>Reject report';

    bootstrap.Modal.getOrCreateInstance(document.getElementById('reportDecisionModal')).show();
}

async function submitReportDecision(event) {
    event.preventDefault();
    const id = document.getElementById('report-id').value;
    const action = document.getElementById('report-action').value;
    if (!id || !['approve', 'reject'].includes(action)) return;

    const note = document.getElementById('report-review-note').value.trim() || null;
    const button = document.getElementById('report-decision-submit');
    const original = button.innerHTML;
    button.disabled = true;
    button.innerHTML = '<span class="spinner-border spinner-border-sm me-1"></span>Saving…';

    try {
        await RailSafeAPI.apiFetch(`/api/reports/${id}/${action}`, {
            method: 'PUT',
            body: JSON.stringify({ reviewNote: note })
        });
        bootstrap.Modal.getOrCreateInstance(document.getElementById('reportDecisionModal')).hide();
        RailSafeUI.toast(action === 'approve'
            ? 'Report approved. Gate status updated and reporter notified.'
            : 'Report rejected. Reporter notified.', 'success');
        await Promise.allSettled([loadReports(), loadGates(), loadStats()]);
    } catch (error) {
        handleActionError(error, 'Could not save the report decision.');
    } finally {
        button.disabled = false;
        button.innerHTML = original;
    }
}

/* ------------------------------------------------------------------ */
/* Shared error handling                                               */
/* ------------------------------------------------------------------ */

/* ------------------------------------------------------------------ */
/* Reference trains (read-only, never live positions)                  */
/* ------------------------------------------------------------------ */

function referenceValue(value) {
    return RailSafeUI.escapeHtml(RailSafeUI.notAvailable(value));
}

async function loadTrains() {
    const body = document.getElementById('trains-body');
    if (!body) return;
    try {
        adminState.trains = await RailSafeAPI.apiFetch('/api/trains');
        renderTrains();
    } catch (error) {
        handleLoadError(error, 'Could not load the train catalogue.');
        body.innerHTML = `<tr><td colspan="7"><div class="empty-state">
            <i class="bi bi-wifi-off"></i><p>${RailSafeUI.escapeHtml(error.message)}</p>
        </div></td></tr>`;
    }
}

function renderTrains() {
    const body = document.getElementById('trains-body');
    if (!body || !adminState.trains.length) {
        if (body) {
            body.innerHTML = `<tr><td colspan="7"><div class="empty-state">
                <i class="bi bi-train-front"></i>
                <p class="fw-bold text-dark mb-1">No reference trains</p>
                <p>The verified catalogue is empty.</p>
            </div></td></tr>`;
        }
        return;
    }

    body.innerHTML = adminState.trains.map(train => {
        const route = `${referenceValue(train.sourceStation)} → ${referenceValue(train.destinationStation)}`;
        const verified = train.lastVerifiedAt
            ? RailSafeUI.escapeHtml(RailSafeUI.formatDateTime(train.lastVerifiedAt))
            : 'Not Available';
        return `<tr>
            <td><span class="badge-soft">${referenceValue(train.trainNumber)}</span></td>
            <td>
                <div class="fw-bold">${referenceValue(train.trainName)}</div>
                <div class="text-muted-rs" style="font-size:.72rem;">
                    ${referenceValue(train.currentStation)} → ${referenceValue(train.nextStation)}
                </div>
                ${train.note ? `<div class="text-muted-rs mt-1" style="font-size:.7rem;">${referenceValue(train.note)}</div>` : ''}
            </td>
            <td class="d-none d-lg-table-cell" style="font-size:.8rem;">${route}</td>
            <td>
                <span class="badge-soft">${referenceValue(train.runningStatus)}</span>
                <div class="text-muted-rs mt-1" style="font-size:.7rem;">
                    Dep ${referenceValue(train.scheduledDeparture)} · Arr ${referenceValue(train.scheduledArrival)}
                </div>
                <div class="text-muted-rs" style="font-size:.7rem;">ETA ${referenceValue(train.estimatedArrival)}</div>
            </td>
            <td class="d-none d-lg-table-cell" style="font-size:.76rem;">
                ${referenceValue(train.referenceSource)}
                <div class="text-muted-rs">${referenceValue(train.dataClassification)}</div>
            </td>
            <td><span class="badge-live-unavailable">Live data unavailable</span></td>
            <td class="d-none d-md-table-cell" style="font-size:.76rem;">${verified}</td>
        </tr>`;
    }).join('');
}

/* ------------------------------------------------------------------ */
/* Staff accounts and gate assignments (ADMIN / SUPER_ADMIN)           */
/* ------------------------------------------------------------------ */

function staffAssignedIds(staff) {
    // StaffAccountResponse exposes `assignedGateIds`; accept the shorter alias too
    // so older cached payloads still render correctly.
    const ids = staff.assignedGateIds ?? staff.gateIds;
    if (!Array.isArray(ids)) return [];
    return ids.map(item => (item && typeof item === 'object' ? item.id : item));
}

async function loadStaff() {
    const body = document.getElementById('staff-body');
    if (!body || !adminState.isAdmin || adminState.isStaff) return;
    try {
        adminState.staff = await RailSafeAPI.apiFetch('/api/admin/staff');
        renderStaff();
    } catch (error) {
        handleLoadError(error, 'Could not load staff accounts.');
        body.innerHTML = `<tr><td colspan="4"><div class="empty-state">
            <i class="bi bi-wifi-off"></i><p>${RailSafeUI.escapeHtml(error.message)}</p>
        </div></td></tr>`;
    }
}

function renderStaff() {
    const body = document.getElementById('staff-body');
    if (!body) return;
    if (!adminState.staff.length) {
        body.innerHTML = `<tr><td colspan="4"><div class="empty-state">
            <i class="bi bi-people"></i>
            <p class="fw-bold text-dark mb-1">No staff accounts</p>
            <p>Create one with the form alongside.</p>
        </div></td></tr>`;
        return;
    }

    body.innerHTML = adminState.staff.map(staff => {
        const ids = staffAssignedIds(staff);
        const status = staff.enabled === false
            ? '<span class="badge text-bg-secondary">Disabled</span>'
            : '<span class="badge text-bg-success">Active</span>';
        return `<tr>
            <td>
                <div class="fw-bold">${referenceValue(staff.fullName)}</div>
                <div class="text-muted-rs" style="font-size:.76rem;">${referenceValue(staff.email)}</div>
            </td>
            <td>
                <span class="badge-soft">${ids.length} assigned</span>
                <div class="text-muted-rs" style="font-size:.72rem;">
                    ${ids.length ? ids.map(id => referenceValue(id)).join(', ') : 'None'}
                </div>
            </td>
            <td>${status}</td>
            <td class="text-end">
                <div class="d-flex flex-wrap justify-content-end gap-1">
                    <button class="btn btn-sm btn-soft" data-action="edit" data-id="${staff.id}" title="Edit account">
                        <i class="bi bi-pencil"></i>
                    </button>
                    <button class="btn btn-sm btn-outline-navy" data-action="assign" data-id="${staff.id}"
                            title="Assign crossings"><i class="bi bi-signpost-split"></i></button>
                    <button class="btn btn-sm ${staff.enabled === false ? 'btn-outline-success' : 'btn-outline-secondary'}"
                            data-action="toggle" data-id="${staff.id}" title="Enable or disable">
                        <i class="bi bi-power"></i>
                    </button>
                    <button class="btn btn-sm btn-outline-danger" data-action="delete" data-id="${staff.id}"
                            title="Delete account"><i class="bi bi-trash"></i></button>
                </div>
            </td>
        </tr>`;
    }).join('');
}

async function loadStaffActivity() {
    const body = document.getElementById('staff-activity-body');
    if (!body || !adminState.isAdmin || adminState.isStaff) return;
    try {
        adminState.staffActivity = await RailSafeAPI.apiFetch('/api/admin/staff/activity');
        refreshStaffActivityFilter();
        renderStaffActivity();
    } catch (error) {
        handleLoadError(error, 'Could not load staff activity history.');
        body.innerHTML = `<tr><td colspan="7"><div class="empty-state">
            <i class="bi bi-wifi-off"></i><p>${RailSafeUI.escapeHtml(error.message)}</p>
        </div></td></tr>`;
    }
}

function refreshStaffActivityFilter() {
    const select = document.getElementById('staff-activity-filter');
    if (!select) return;
    const previous = select.value;
    const options = new Map();
    adminState.staffActivity.forEach(item => {
        if (item.staffId && item.changedBy) {
            options.set(String(item.staffId), item.changedBy);
        }
    });
    adminState.staff.forEach(staff => options.set(String(staff.id), staff.fullName));
    select.innerHTML = '<option value="">All staff</option>' +
        Array.from(options, ([id, name]) =>
            `<option value="${id}">${referenceValue(name)}</option>`).join('');
    select.value = options.has(previous) ? previous : '';
}

function renderStaffActivity() {
    const body = document.getElementById('staff-activity-body');
    if (!body) return;
    const filter = document.getElementById('staff-activity-filter').value;
    const items = filter
        ? adminState.staffActivity.filter(item => String(item.staffId) === filter)
        : adminState.staffActivity;

    if (!items.length) {
        body.innerHTML = `<tr><td colspan="7"><div class="empty-state">
            <i class="bi bi-clock-history"></i>
            <p class="fw-bold text-dark mb-1">No staff activity recorded</p>
            <p>Verified gate updates made by STAFF accounts will appear here.</p>
        </div></td></tr>`;
        return;
    }

    body.innerHTML = items.map(item => `
        <tr>
            <td style="font-size:.76rem;">${item.changedAt
                ? RailSafeUI.escapeHtml(RailSafeUI.formatDateTime(item.changedAt)) : 'Not Available'}</td>
            <td>
                <div class="fw-bold" style="font-size:.78rem;">${referenceValue(
                    item.gateCode || `Gate ${item.gateId || ''}`)}</div>
                <div class="text-muted-rs" style="font-size:.68rem;">Crossing #${referenceValue(item.gateId)}</div>
            </td>
            <td style="font-size:.76rem;">${referenceValue(item.changedBy)}</td>
            <td style="font-size:.74rem;">${referenceValue(item.action)}</td>
            <td>${item.oldStatus ? RailSafeUI.statusBadge(item.oldStatus) : '<span class="text-muted-rs">–</span>'}</td>
            <td>${item.newStatus ? RailSafeUI.statusBadge(item.newStatus) : '<span class="text-muted-rs">–</span>'}</td>
            <td class="d-none d-lg-table-cell" style="font-size:.72rem;">
                ${item.note ? RailSafeUI.escapeHtml(item.note) : '—'}
            </td>
        </tr>`).join('');
}

function onStaffTableClick(event) {
    const button = event.target.closest('button[data-action]');
    if (!button) return;
    const staff = adminState.staff.find(item => item.id === Number(button.dataset.id));
    if (!staff) return;
    switch (button.dataset.action) {
        case 'edit': startEditStaff(staff); break;
        case 'assign': openStaffAssignModal(staff); break;
        case 'toggle': toggleStaff(staff); break;
        case 'delete': deleteStaff(staff); break;
    }
}

function startEditStaff(staff) {
    adminState.staffEditingId = staff.id;
    document.getElementById('staff-form-title').textContent = `Edit ${staff.fullName}`;
    document.getElementById('staff-form-hint').textContent = 'Leave the password blank to keep the current one.';
    document.getElementById('staff-name').value = staff.fullName || '';
    document.getElementById('staff-email').value = staff.email || '';
    document.getElementById('staff-phone').value = staff.phone || '';
    document.getElementById('staff-password').value = '';
    document.getElementById('staff-password').required = false;
    document.getElementById('staff-password-hint').textContent =
        'Leave blank to keep the current password. New passwords need at least 8 characters.';
    document.getElementById('staff-submit').innerHTML = '<i class="bi bi-save me-1"></i>Save changes';
    document.getElementById('staff-cancel').classList.remove('hidden');
}

function resetStaffForm() {
    adminState.staffEditingId = null;
    document.getElementById('staff-form').reset();
    document.getElementById('staff-form-title').textContent = 'Create staff account';
    document.getElementById('staff-form-hint').textContent = 'New accounts receive the STAFF role.';
    document.getElementById('staff-password').required = true;
    document.getElementById('staff-password-hint').textContent = 'At least 8 characters.';
    document.getElementById('staff-submit').innerHTML =
        '<i class="bi bi-person-plus me-1"></i>Create staff account';
    document.getElementById('staff-cancel').classList.add('hidden');
}

async function saveStaff(event) {
    event.preventDefault();
    const editingId = adminState.staffEditingId;
    const fullName = document.getElementById('staff-name').value.trim();
    const email = document.getElementById('staff-email').value.trim();
    const phone = document.getElementById('staff-phone').value.trim();
    const password = document.getElementById('staff-password').value;

    if (!fullName || !email) {
        RailSafeUI.toast('Full name and email are required.', 'warning');
        return;
    }
    if ((!editingId || password) && password.length < 8) {
        RailSafeUI.toast('A password of at least 8 characters is required.', 'warning');
        return;
    }

    const payload = { fullName, email, phone: phone || null };
    if (password) payload.password = password;

    const button = document.getElementById('staff-submit');
    const original = button.innerHTML;
    button.disabled = true;
    button.innerHTML = '<span class="spinner-border spinner-border-sm me-1"></span>Saving…';
    try {
        await RailSafeAPI.apiFetch(editingId ? `/api/admin/staff/${editingId}` : '/api/admin/staff', {
            method: editingId ? 'PUT' : 'POST',
            body: JSON.stringify(payload)
        });
        RailSafeUI.toast(editingId ? 'Staff account updated.' : 'Staff account created.', 'success');
        resetStaffForm();
        await loadStaff();
    } catch (error) {
        handleActionError(error, 'Could not save the staff account.');
    } finally {
        button.disabled = false;
        button.innerHTML = original;
    }
}

async function toggleStaff(staff) {
    if (!window.confirm(`${staff.enabled === false ? 'Enable' : 'Disable'} ${staff.fullName}?`)) return;
    try {
        const enabled = staff.enabled === false;
        await RailSafeAPI.apiFetch(
            `/api/admin/staff/${staff.id}/enabled?enabled=${enabled}`, { method: 'PUT' });
        RailSafeUI.toast(enabled ? 'Staff account enabled.' : 'Staff account disabled.', 'success');
        await loadStaff();
    } catch (error) {
        handleActionError(error, 'Could not update the staff account.');
    }
}

async function deleteStaff(staff) {
    if (!window.confirm(`Delete the staff account for ${staff.fullName}?`)) return;
    try {
        await RailSafeAPI.apiFetch(`/api/admin/staff/${staff.id}`, { method: 'DELETE' });
        RailSafeUI.toast('Staff account deleted.', 'success');
        await loadStaff();
    } catch (error) {
        handleActionError(error, 'Could not delete the staff account.');
    }
}

function openStaffAssignModal(staff) {
    const ids = new Set(staffAssignedIds(staff).map(Number));
    document.getElementById('assign-staff-id').value = staff.id;
    document.getElementById('assign-staff-summary').textContent =
        `Choose the crossings ${staff.fullName} may update. Only assigned crossings appear on the staff dashboard.`;
    const container = document.getElementById('staff-assign-gates');
    if (!adminState.gates.length) {
        container.innerHTML = '<div class="text-muted-rs small">No crossings are available to assign.</div>';
    } else {
        container.innerHTML = adminState.gates.map(gate => `
            <div class="form-check staff-assign-item">
                <input class="form-check-input" type="checkbox" value="${gate.id}"
                       id="assign-gate-${gate.id}" ${ids.has(Number(gate.id)) ? 'checked' : ''}>
                <label class="form-check-label" for="assign-gate-${gate.id}">
                    <span class="badge-soft">${referenceValue(gate.gateCode)}</span>
                    ${referenceValue(gate.name)}
                </label>
            </div>`).join('');
    }
    bootstrap.Modal.getOrCreateInstance(document.getElementById('staffAssignModal')).show();
}

async function saveStaffAssignments(event) {
    event.preventDefault();
    const id = document.getElementById('assign-staff-id').value;
    if (!id) return;
    const gateIds = Array.from(document.querySelectorAll('#staff-assign-gates input:checked'))
        .map(input => Number(input.value));
    const button = document.getElementById('assign-save');
    const original = button.innerHTML;
    button.disabled = true;
    button.innerHTML = '<span class="spinner-border spinner-border-sm me-1"></span>Saving…';
    try {
        await RailSafeAPI.apiFetch(`/api/admin/staff/${id}/assignments`, {
            method: 'PUT',
            body: JSON.stringify({ gateIds })
        });
        bootstrap.Modal.getOrCreateInstance(document.getElementById('staffAssignModal')).hide();
        RailSafeUI.toast('Assignments saved.', 'success');
        await loadStaff();
    } catch (error) {
        handleActionError(error, 'Could not save the assignments.');
    } finally {
        button.disabled = false;
        button.innerHTML = original;
    }
}

/* ------------------------------------------------------------------ */
/* Maintenance requests                                                */
/* ------------------------------------------------------------------ */

function refreshGateOptions() {
    const select = document.getElementById('maintenance-gate');
    if (!select) return;
    const previous = select.value;
    select.innerHTML = adminState.gates.length
        ? adminState.gates.map(gate => `<option value="${gate.id}">${referenceValue(
            `${gate.gateCode} – ${gate.name}`)}</option>`).join('')
        : '<option value="">No crossings available</option>';
    if (previous) select.value = previous;
}

async function loadMaintenance() {
    const body = document.getElementById('maintenance-body');
    if (!body) return;
    try {
        adminState.maintenance = await RailSafeAPI.apiFetch('/api/maintenance');
        renderMaintenance();
    } catch (error) {
        handleLoadError(error, 'Could not load maintenance requests.');
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
    return `<span class="badge ${cls}">${referenceValue(value)}</span>`;
}

function renderMaintenance() {
    const body = document.getElementById('maintenance-body');
    if (!body) return;
    if (!adminState.maintenance.length) {
        body.innerHTML = `<tr><td colspan="6"><div class="empty-state">
            <i class="bi bi-tools"></i>
            <p class="fw-bold text-dark mb-1">No maintenance requests</p>
            <p>New requests will appear here.</p>
        </div></td></tr>`;
        return;
    }

    const statuses = ['PENDING', 'IN_PROGRESS', 'RESOLVED', 'CANCELLED'];
    body.innerHTML = adminState.maintenance.map(item => {
        const options = statuses.map(status => `<option value="${status}" ${
            String(item.status || '').toUpperCase() === status ? 'selected' : ''}>${status}</option>`).join('');
        return `<tr>
            <td>
                <div class="fw-bold">${referenceValue(item.title || item.description)}</div>
                ${item.description ? `<div class="text-muted-rs mt-1" style="font-size:.72rem;">${referenceValue(item.description)}</div>` : ''}
            </td>
            <td>
                <div>${referenceValue(item.gateName || item.gateCode || item.gateId)}</div>
                <div class="text-muted-rs" style="font-size:.72rem;">${referenceValue(item.gateCode)}</div>
            </td>
            <td><span class="badge-soft">${referenceValue(item.priority)}</span></td>
            <td>${maintenanceStatusBadge(item.status)}</td>
            <td class="d-none d-md-table-cell" style="font-size:.76rem;">
                ${item.createdAt ? RailSafeUI.escapeHtml(RailSafeUI.formatDateTime(item.createdAt)) : 'Not Available'}
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
    if (!adminState.gates.length) {
        RailSafeUI.toast('Add a crossing before raising a maintenance request.', 'warning');
        return;
    }
    bootstrap.Modal.getOrCreateInstance(document.getElementById('maintenanceModal')).show();
}

async function saveMaintenance(event) {
    event.preventDefault();
    const gateId = Number(document.getElementById('maintenance-gate').value);
    const title = document.getElementById('maintenance-title').value.trim();
    if (!Number.isFinite(gateId) || !gateId || !title) {
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
        handleActionError(error, 'Could not create the maintenance request.');
    } finally {
        button.disabled = false;
        button.innerHTML = original;
    }
}

function onMaintenanceTableClick(event) {
    const button = event.target.closest('button[data-action]');
    if (!button || button.dataset.action !== 'status') return;
    const select = document.getElementById(`maint-status-${button.dataset.id}`);
    if (!select) return;
    updateMaintenanceStatus(button.dataset.id, select.value);
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
        handleActionError(error, 'Could not update the maintenance status.');
    }
}

/* ------------------------------------------------------------------ */
/* Profile                                                             */
/* ------------------------------------------------------------------ */

function renderProfile() {
    const container = document.getElementById('admin-profile-detail');
    if (!container || !adminState.user) return;
    const user = adminState.user;
    const rows = [
        ['Full name', user.fullName],
        ['Email', user.email],
        ['Role', user.role],
        ['Phone', user.phone || 'Not Available']
    ];
    container.innerHTML = rows.map(([label, value]) => `
        <div><dt>${RailSafeUI.escapeHtml(label)}</dt><dd>${referenceValue(value)}</dd></div>
    `).join('');
}

function handleLoadError(error, message) {
    if (error.status === 401) {
        RailSafeAPI.redirectToLogin('admin.html');
        return;
    }
    if (error.status === 403) {
        RailSafeUI.inlineAlert(document.getElementById('admin-alert'), 'danger',
            `<i class="bi bi-shield-lock me-1"></i><strong>Permission denied.</strong>
             Only STAFF and ADMIN accounts can use this dashboard.`);
        return;
    }
    RailSafeUI.inlineAlert(document.getElementById('admin-alert'), 'warning',
        `<i class="bi bi-wifi-off me-1"></i><strong>${RailSafeUI.escapeHtml(message)}</strong>
         ${RailSafeUI.escapeHtml(error.message)}`);
}

function handleActionError(error, message) {
    if (error.status === 401) {
        RailSafeAPI.redirectToLogin('admin.html');
        return;
    }
    if (error.status === 403) {
        RailSafeUI.toast('You do not have permission to perform that action.', 'danger');
        return;
    }
    RailSafeUI.toast(`${message} ${error.message}`, 'danger');
}
