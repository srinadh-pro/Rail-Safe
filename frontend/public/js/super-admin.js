/* Super Admin account management: create, edit and deactivate ADMIN accounts.
   The backend enforces SUPER_ADMIN access and blocks role changes. */
const superAdminState = {
    user: null,
    admins: [],
    editingId: null,
    sourceRecords: [],
    sourceSummary: null,
    auditLogs: [],
    health: null,
    dataSources: [],
    security: null,
    config: null,
    backups: [],
    gates: [],
    overrideSelection: null,
    liveSource: null
};

document.addEventListener('DOMContentLoaded', async () => {
    const user = await RailSafeUI.initShell('super-admin');
    if (!RailSafeUI.guard(user, ['SUPER_ADMIN'])) return;

    superAdminState.user = user;
    document.getElementById('current-super-admin').textContent = user.fullName;

    bindSuperAdminUi();
    await Promise.allSettled([
        loadAdminAccounts(),
        loadSourceRecords(),
        loadSourceSummary(),
        loadAuditLogs(),
        loadHealth(),
        loadDataSources(),
        loadSecurity(),
        loadConfig(),
        loadBackups(),
        loadSuperAdminCounts(),
        loadOverrideGates()
    ]);
    superAdminState.liveSource = RailSafeUI.startLiveStream(loadSuperAdminCounts);
});

function bindSuperAdminUi() {
    document.getElementById('refresh-admins').addEventListener('click', loadAdminAccounts);
    document.getElementById('admin-form').addEventListener('submit', submitAdminForm);
    document.getElementById('admin-cancel').addEventListener('click', resetAdminForm);

    document.getElementById('admin-rows').addEventListener('click', event => {
        const button = event.target.closest('button[data-action]');
        if (!button) return;
        const id = Number(button.dataset.id);
        if (button.dataset.action === 'edit') {
            startEdit(id);
        } else if (button.dataset.action === 'toggle') {
            toggleAdmin(id);
        }
    });

    document.getElementById('refresh-source-records').addEventListener('click', () => {
        loadSourceRecords();
        loadSourceSummary();
    });
    document.getElementById('refresh-audit-logs').addEventListener('click', loadAuditLogs);
    document.getElementById('refresh-override-gates').addEventListener('click', loadOverrideGates);
    document.getElementById('override-form').addEventListener('submit', reviewOverride);
    document.getElementById('override-confirm-form').addEventListener('submit', submitOverride);

    document.getElementById('refresh-platform').addEventListener('click', () => {
        void Promise.allSettled([
            loadHealth(), loadDataSources(), loadSecurity(), loadConfig(), loadBackups()
        ]);
    });
    document.getElementById('refresh-health').addEventListener('click', loadHealth);
    document.getElementById('refresh-datasources').addEventListener('click', loadDataSources);
    document.getElementById('refresh-security').addEventListener('click', loadSecurity);
    document.getElementById('refresh-config').addEventListener('click', loadConfig);
    document.getElementById('refresh-backups').addEventListener('click', loadBackups);

    document.getElementById('source-record-rows').addEventListener('click', event => {
        const button = event.target.closest('button[data-source-action="review"]');
        if (!button) return;
        const record = superAdminState.sourceRecords[Number(button.dataset.index)];
        if (record) {
            showSourceRecord(record);
        }
    });
}

async function loadAdminAccounts() {
    const tbody = document.getElementById('admin-rows');
    tbody.innerHTML = '<tr><td colspan="4" class="text-center text-muted-rs py-4">Loading administrator accounts…</td></tr>';
    try {
        superAdminState.admins = await RailSafeAPI.apiFetch('/api/super-admin/admins');
        renderAdminAccounts();
    } catch (error) {
        RailSafeUI.inlineAlert(document.getElementById('super-admin-alert'), 'danger',
            RailSafeUI.escapeHtml(error.message));
        RailSafeUI.emptyState(tbody, 'bi-exclamation-triangle', 'Could not load administrators', error.message);
    }
}

function renderAdminAccounts() {
    const tbody = document.getElementById('admin-rows');
    const admins = superAdminState.admins;
    const countLabel = document.getElementById('admin-count');
    const adminCount = admins.filter(admin => admin.role === 'ADMIN').length;
    countLabel.textContent = `${adminCount} ADMIN account${adminCount === 1 ? '' : 's'} · ${admins.length} total`;

    if (!admins.length) {
        RailSafeUI.emptyState(tbody, 'bi-people', 'No administrator accounts yet',
            'Create the first ADMIN account using the form.');
        return;
    }

    tbody.innerHTML = admins.map(admin => {
        const isProtected = admin.role === 'SUPER_ADMIN';
        const statusBadge = admin.enabled
            ? '<span class="badge text-bg-success">Active</span>'
            : '<span class="badge text-bg-secondary">Disabled</span>';
        const roleBadge = isProtected
            ? '<span class="badge-soft">SUPER_ADMIN</span>'
            : '<span class="badge-soft">ADMIN</span>';
        const actions = isProtected
            ? '<span class="text-muted-rs small">Protected</span>'
            : `<button class="btn btn-sm btn-outline-navy me-1" type="button"
                       data-action="edit" data-id="${admin.id}">
                   <i class="bi bi-pencil-square me-1"></i>Edit
               </button>
               <button class="btn btn-sm ${admin.enabled ? 'btn-outline-danger' : 'btn-outline-success'}" type="button"
                       data-action="toggle" data-id="${admin.id}">
                   ${admin.enabled ? 'Deactivate' : 'Reactivate'}
               </button>`;

        return `<tr>
            <td>
                <div class="fw-semibold">${RailSafeUI.escapeHtml(admin.fullName)}</div>
                <div class="text-muted-rs" style="font-size:.78rem;">${RailSafeUI.escapeHtml(admin.email)}</div>
                ${admin.phone ? `<div class="text-muted-rs" style="font-size:.78rem;">${RailSafeUI.escapeHtml(admin.phone)}</div>` : ''}
            </td>
            <td>${roleBadge}</td>
            <td>${statusBadge}</td>
            <td class="text-end">${actions}</td>
        </tr>`;
    }).join('');
}

async function loadSourceRecords() {
    const tbody = document.getElementById('source-record-rows');
    const countLabel = document.getElementById('source-record-count');
    const button = document.getElementById('refresh-source-records');
    tbody.innerHTML = '<tr><td colspan="5" class="text-center text-muted-rs py-4">Loading restricted records…</td></tr>';
    button.disabled = true;

    try {
        superAdminState.sourceRecords = await RailSafeAPI.apiFetch('/api/super-admin/demo-data');
        renderSourceRecords();
    } catch (error) {
        renderSourceRecordsError(error);
        RailSafeUI.inlineAlert(document.getElementById('super-admin-alert'), 'danger',
            RailSafeUI.escapeHtml(error.message));
    } finally {
        button.disabled = false;
        countLabel.textContent = superAdminState.sourceRecords.length
            ? `${superAdminState.sourceRecords.length} retained row${superAdminState.sourceRecords.length === 1 ? '' : 's'}`
            : 'No retained rows';
    }
}

function renderSourceRecords() {
    const tbody = document.getElementById('source-record-rows');
    const rows = superAdminState.sourceRecords;

    if (!rows.length) {
        RailSafeUI.emptyState(tbody, 'bi-shield-check', 'No restricted records',
            'All retained records are currently clear.');
        return;
    }

    tbody.innerHTML = rows.map((row, index) => {
        const code = RailSafeUI.escapeHtml(row.code || '—');
        const tableName = RailSafeUI.escapeHtml(row.tableName || '—');
        const recordLabel = RailSafeUI.escapeHtml(row.recordLabel || '—');
        const status = RailSafeUI.escapeHtml(row.status || 'UNKNOWN');
        const type = RailSafeUI.escapeHtml(row.type || 'Record');

        return `<tr>
            <td><span class="crossing-code">${code}</span></td>
            <td><span class="table-name">${tableName}</span></td>
            <td>
                <div class="fw-semibold">${recordLabel}</div>
                <div class="text-muted-rs" style="font-size:.76rem;">${type} · ID ${RailSafeUI.escapeHtml(row.id ?? '—')}</div>
            </td>
            <td><span class="badge-soft">${status}</span></td>
            <td class="text-end">
                <button class="btn btn-sm btn-outline-navy" type="button"
                        data-source-action="review" data-index="${index}">
                    <i class="bi bi-eye me-1"></i>View / Review
                </button>
            </td>
        </tr>`;
    }).join('');
}

function renderSourceRecordsError(error) {
    const tbody = document.getElementById('source-record-rows');
    tbody.innerHTML = `<tr><td colspan="5" class="text-center text-muted-rs py-4">
        Could not load restricted records. ${RailSafeUI.escapeHtml(error.message)}
    </td></tr>`;
}

async function loadSourceSummary() {
    const container = document.getElementById('source-review-summary');
    try {
        superAdminState.sourceSummary = await RailSafeAPI.apiFetch('/api/super-admin/source-review-summary');
        renderSourceSummary(superAdminState.sourceSummary);
    } catch (error) {
        container.innerHTML = `<div class="review-summary-item">Could not load summary: ${RailSafeUI.escapeHtml(error.message)}</div>`;
    }
}

function renderSourceSummary(summary) {
    const container = document.getElementById('source-review-summary');
    if (!summary) {
        container.innerHTML = '';
        return;
    }

    const categories = Object.entries(summary.categoryCounts || {})
        .map(([name, count]) => `${name}: ${count}`)
        .join(' · ');
    const gates = Object.entries(summary.gateStatusCounts || {})
        .map(([name, count]) => `${name}: ${count}`)
        .join(' · ');
    const sensors = Object.entries(summary.sensorStatusCounts || {})
        .map(([name, count]) => `${name}: ${count}`)
        .join(' · ');

    const items = [
        ['Retained review rows', summary.totalRetainedRows],
        ['Restricted imported rows', summary.restrictedImportedRows],
        ['Gate review records', summary.gateReviewRows],
        ['Train review records', summary.trainReviewRows],
        ['Sensor review records', summary.sensorReviewRows],
        ['Data mode', summary.dataMode],
        ['Categories', categories || 'None'],
        ['Gate statuses', gates || 'None'],
        ['Sensor statuses', sensors || 'None'],
        ['Last imported', formatAuditTimestamp(summary.lastImportedAt)]
    ];

    container.innerHTML = items.map(([label, value]) => `
        <div class="review-summary-item">
            <span class="review-summary-label">${RailSafeUI.escapeHtml(label)}</span>
            <span class="review-summary-value">${RailSafeUI.escapeHtml(value ?? '—')}</span>
        </div>`).join('');
}

/* ------------------------------------------------------------------ */
/* Live synchronization indicator (never a live railway feed claim)     */
/* ------------------------------------------------------------------ */

async function loadSuperAdminCounts() {
    try {
        const counts = await RailSafeAPI.apiFetch('/api/live/counts');
        RailSafeUI.renderLiveIndicator(
            document.getElementById('super-admin-live-indicator'), counts);
        RailSafeUI.renderSyncTime(
            document.getElementById('super-admin-last-sync'), counts);
    } catch (error) {
        RailSafeUI.renderLiveIndicator(
            document.getElementById('super-admin-live-indicator'), null);
        RailSafeUI.renderSyncTime(
            document.getElementById('super-admin-last-sync'), null);
    }
}

/* ------------------------------------------------------------------ */
/* SUPER_ADMIN gate status override (reason required, fully audited)    */
/* ------------------------------------------------------------------ */

async function loadOverrideGates() {
    const select = document.getElementById('override-gate');
    if (!select) return;
    const previous = select.value;
    try {
        superAdminState.gates = await RailSafeAPI.apiFetch('/api/gates');
        select.innerHTML = superAdminState.gates.length
            ? superAdminState.gates.map(gate =>
                `<option value="${gate.id}">${RailSafeUI.escapeHtml(
                    `${gate.gateCode} – ${gate.name}`)}</option>`).join('')
            : '<option value="">No crossings available</option>';
        if (previous && superAdminState.gates.some(gate => String(gate.id) === previous)) {
            select.value = previous;
        }
    } catch (error) {
        select.innerHTML = '<option value="">Could not load crossings</option>';
        RailSafeUI.toast(error.message, 'danger');
    }
}

function reviewOverride(event) {
    event.preventDefault();
    const gateId = Number(document.getElementById('override-gate').value);
    const status = document.getElementById('override-status').value;
    const reason = document.getElementById('override-reason').value.trim();
    const gate = superAdminState.gates.find(item => Number(item.id) === gateId);

    if (!gate || !['OPEN', 'CLOSED'].includes(status) || !reason) {
        RailSafeUI.toast('Choose a crossing, a status and enter an authorization reason.', 'warning');
        return;
    }

    superAdminState.overrideSelection = { gate, status, reason };
    document.getElementById('override-confirm-summary').innerHTML = `
        <div class="d-flex flex-wrap align-items-center gap-2">
            <span class="fw-bold fs-5">${status === 'OPEN' ? 'OPEN GATE' : 'CLOSE GATE'}</span>
            ${RailSafeUI.statusBadge(status)}
        </div>
        <div class="mt-2">${RailSafeUI.escapeHtml(`${gate.gateCode} – ${gate.name}`)}</div>
        <div class="mt-2"><span class="text-muted-rs">Reason:</span>
            ${RailSafeUI.escapeHtml(reason)}</div>`;
    bootstrap.Modal.getOrCreateInstance(document.getElementById('override-modal')).show();
}

async function submitOverride(event) {
    event.preventDefault();
    const selection = superAdminState.overrideSelection;
    if (!selection || !selection.gate || !selection.reason) {
        RailSafeUI.toast('The override details are no longer available. Please review again.', 'warning');
        return;
    }

    const button = document.getElementById('override-confirm-btn');
    const original = button.innerHTML;
    button.disabled = true;
    button.innerHTML = '<span class="spinner-border spinner-border-sm me-1"></span>Recording…';
    try {
        await RailSafeAPI.apiFetch(`/api/gates/${selection.gate.id}/override`, {
            method: 'PUT',
            body: JSON.stringify({ status: selection.status, reason: selection.reason })
        });
        bootstrap.Modal.getOrCreateInstance(document.getElementById('override-modal')).hide();
        document.getElementById('override-form').reset();
        document.getElementById('override-status').value = 'OPEN';
        superAdminState.overrideSelection = null;
        RailSafeUI.toast('Override recorded, broadcast and added to the audit log.', 'success');
        await Promise.allSettled([
            loadOverrideGates(), loadAuditLogs(), loadSuperAdminCounts()
        ]);
    } catch (error) {
        RailSafeUI.inlineAlert(document.getElementById('super-admin-alert'), 'danger',
            RailSafeUI.escapeHtml(error.message));
        RailSafeUI.toast(error.message, 'danger');
    } finally {
        button.disabled = false;
        button.innerHTML = original;
    }
}

async function loadAuditLogs() {
    const tbody = document.getElementById('audit-log-rows');
    const button = document.getElementById('refresh-audit-logs');
    const countLabel = document.getElementById('audit-log-count');
    tbody.innerHTML = '<tr><td colspan="5" class="text-center text-muted-rs py-4">Loading audit events…</td></tr>';
    button.disabled = true;

    try {
        superAdminState.auditLogs = await RailSafeAPI.apiFetch('/api/super-admin/audit-logs');
        renderAuditLogs();
    } catch (error) {
        RailSafeUI.emptyState(tbody, 'bi-exclamation-triangle', 'Could not load audit events', error.message);
        countLabel.textContent = '';
    } finally {
        button.disabled = false;
    }
}

function renderAuditLogs() {
    const tbody = document.getElementById('audit-log-rows');
    const countLabel = document.getElementById('audit-log-count');
    const logs = superAdminState.auditLogs;
    countLabel.textContent = logs.length ? `${logs.length} most recent event${logs.length === 1 ? '' : 's'}` : '';

    if (!logs.length) {
        RailSafeUI.emptyState(tbody, 'bi-shield-check', 'No audit events yet',
            'Administrative and authentication events will appear here.');
        return;
    }

    tbody.innerHTML = logs.map(log => `
        <tr>
            <td class="text-nowrap">${RailSafeUI.escapeHtml(formatAuditTimestamp(log.createdAt))}</td>
            <td>${RailSafeUI.escapeHtml(log.actor || '—')}</td>
            <td><span class="badge-soft">${RailSafeUI.escapeHtml(log.action || '—')}</span></td>
            <td>
                <div>${RailSafeUI.escapeHtml(log.targetType || '—')}</div>
                <div class="text-muted-rs" style="font-size:.76rem;">${RailSafeUI.escapeHtml(log.targetId || '')}</div>
            </td>
            <td>${RailSafeUI.escapeHtml(log.details || '—')}</td>
        </tr>`).join('');
}

function formatAuditTimestamp(value) {
    if (!value) return '—';
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? String(value) : date.toLocaleString();
}

function showSourceRecord(row) {
    const detail = document.getElementById('source-record-detail');
    const title = document.getElementById('source-record-modal-title');
    const fields = [
        ['Record type', row.type],
        ['Record ID', row.id],
        ['Code', row.code],
        ['Source table', row.tableName],
        ['Record', row.recordLabel],
        ['Status', row.status],
        ['Additional detail', row.detail]
    ];

    title.textContent = row.recordLabel || 'Record review';
    detail.innerHTML = fields.map(([label, value]) => `
        <div>
            <dt>${RailSafeUI.escapeHtml(label)}</dt>
            <dd class="${label === 'Code' ? 'crossing-code' : ''}">${RailSafeUI.escapeHtml(value ?? '—')}</dd>
        </div>`).join('');

    const modalElement = document.getElementById('source-record-modal');
    // Recreate the instance so a previously dismissed dialog cannot leave a stale
    // transition state that prevents the next review from opening.
    const existingModal = bootstrap.Modal.getInstance(modalElement);
    if (existingModal) existingModal.dispose();
    const modal = new bootstrap.Modal(modalElement);
    modal.show();
}

function startEdit(id) {
    const admin = superAdminState.admins.find(item => item.id === id);
    if (!admin || admin.role !== 'ADMIN') return;

    superAdminState.editingId = id;
    document.getElementById('admin-form-title').textContent = `Edit ${admin.fullName}`;
    document.getElementById('admin-form-hint').textContent =
        'Leave the password blank to keep the current one. The role cannot be changed.';
    document.getElementById('admin-name').value = admin.fullName;
    document.getElementById('admin-email').value = admin.email;
    document.getElementById('admin-phone').value = admin.phone || '';
    document.getElementById('admin-password').value = '';
    document.getElementById('admin-password').required = false;
    document.getElementById('admin-password-hint').textContent =
        'Leave blank to keep the current password. New passwords need at least 8 characters.';
    document.getElementById('admin-submit').innerHTML =
        '<i class="bi bi-save me-1"></i>Save changes';
    document.getElementById('admin-cancel').classList.remove('hidden');
    document.getElementById('admin-form-card').scrollIntoView({ behavior: 'smooth', block: 'nearest' });
    document.getElementById('admin-name').focus();
}

function resetAdminForm() {
    superAdminState.editingId = null;
    document.getElementById('admin-form').reset();
    document.getElementById('admin-form-title').textContent = 'Create an ADMIN account';
    document.getElementById('admin-form-hint').textContent =
        'New accounts receive the ADMIN role. SUPER_ADMIN can never be granted here.';
    document.getElementById('admin-password').required = true;
    document.getElementById('admin-password-hint').textContent =
        'At least 8 characters. Stored as a BCrypt hash.';
    document.getElementById('admin-submit').innerHTML =
        '<i class="bi bi-person-plus me-1"></i>Create admin';
    document.getElementById('admin-cancel').classList.add('hidden');
}

async function submitAdminForm(event) {
    event.preventDefault();
    const alertBox = document.getElementById('super-admin-alert');
    RailSafeUI.clearAlert(alertBox);

    const fullName = document.getElementById('admin-name').value.trim();
    const email = document.getElementById('admin-email').value.trim();
    const phone = document.getElementById('admin-phone').value.trim();
    const password = document.getElementById('admin-password').value;
    const editingId = superAdminState.editingId;
    const button = document.getElementById('admin-submit');

    if (!fullName || !email) {
        RailSafeUI.inlineAlert(alertBox, 'danger', 'Full name and email are required.');
        return;
    }
    if (!editingId && password.length < 8) {
        RailSafeUI.inlineAlert(alertBox, 'danger', 'A password of at least 8 characters is required.');
        return;
    }
    if (editingId && password && password.length < 8) {
        RailSafeUI.inlineAlert(alertBox, 'danger', 'A new password must be at least 8 characters.');
        return;
    }

    const payload = {
        fullName,
        email,
        phone: phone || null
    };
    if (password) payload.password = password;

    button.disabled = true;
    button.innerHTML = '<span class="spinner-border spinner-border-sm me-2"></span>Saving…';

    try {
        if (editingId) {
            await RailSafeAPI.apiFetch(`/api/super-admin/admins/${editingId}`, {
                method: 'PUT',
                body: JSON.stringify(payload)
            });
            RailSafeUI.toast('Administrator account updated.', 'success');
        } else {
            await RailSafeAPI.apiFetch('/api/super-admin/admins', {
                method: 'POST',
                body: JSON.stringify(payload)
            });
            RailSafeUI.toast('ADMIN account created.', 'success');
        }
        resetAdminForm();
        await Promise.all([loadAdminAccounts(), loadAuditLogs()]);
    } catch (error) {
        RailSafeUI.inlineAlert(alertBox, 'danger', RailSafeUI.escapeHtml(error.message));
        RailSafeUI.toast(error.message, 'danger');
    } finally {
        button.disabled = false;
        button.innerHTML = superAdminState.editingId
            ? '<i class="bi bi-save me-1"></i>Save changes'
            : '<i class="bi bi-person-plus me-1"></i>Create admin';
    }
}

async function toggleAdmin(id) {
    const admin = superAdminState.admins.find(item => item.id === id);
    if (!admin || admin.role !== 'ADMIN') return;

    const action = admin.enabled ? 'deactivate' : 'reactivate';
    if (!window.confirm(`${action === 'deactivate' ? 'Deactivate' : 'Reactivate'} ${admin.fullName}?`)) {
        return;
    }

    const alertBox = document.getElementById('super-admin-alert');
    RailSafeUI.clearAlert(alertBox);
    try {
        await RailSafeAPI.apiFetch(`/api/super-admin/admins/${admin.id}`, {
            method: 'PUT',
            body: JSON.stringify({
                fullName: admin.fullName,
                email: admin.email,
                phone: admin.phone || null,
                enabled: !admin.enabled
            })
        });
        RailSafeUI.toast(`Account ${action}d.`, 'success');
        await Promise.all([loadAdminAccounts(), loadAuditLogs()]);
    } catch (error) {
        RailSafeUI.inlineAlert(alertBox, 'danger', RailSafeUI.escapeHtml(error.message));
        RailSafeUI.toast(error.message, 'danger');
    }
}

/* ------------------------------------------------------------------ */
/* Platform oversight panels (read-only)                              */
/* ------------------------------------------------------------------ */

function humanizeLabel(key) {
    return String(key)
        .replace(/([a-z0-9])([A-Z])/g, '$1 $2')
        .replace(/[_-]+/g, ' ')
        .replace(/^./, char => char.toUpperCase());
}

function displayValue(value) {
    if (value === null || value === undefined || value === '') return 'Not Available';
    if (typeof value === 'boolean') return value ? 'Yes' : 'No';
    if (Array.isArray(value)) return value.map(displayValue).join(', ');
    if (typeof value === 'object') return JSON.stringify(value);
    return String(value);
}

function renderDetailPanel(container, data, loadingMessage) {
    if (!container) return;
    if (!data) {
        container.innerHTML = `<div class="text-muted-rs small">${RailSafeUI.escapeHtml(
            loadingMessage || 'Not Available')}</div>`;
        return;
    }
    const entries = Array.isArray(data)
        ? data.map((item, index) => [item.label || item.name || `Item ${index + 1}`, item.value !== undefined
            ? item.value : item])
        : Object.entries(data);
    container.innerHTML = entries.length ? entries.map(([label, value]) => `
        <div>
            <dt>${RailSafeUI.escapeHtml(humanizeLabel(label))}</dt>
            <dd>${RailSafeUI.escapeHtml(displayValue(value))}</dd>
        </div>`).join('')
        : '<div class="text-muted-rs small">No values reported.</div>';
}

function renderPanelError(container, error, loadingMessage) {
    if (!container) return;
    container.innerHTML = `<div class="alert-rs alert-rs-warning" style="font-size:.8rem;">
        <i class="bi bi-exclamation-triangle me-1"></i>
        ${RailSafeUI.escapeHtml(`${loadingMessage} ${error && error.message ? error.message : ''}`.trim())}
    </div>`;
}

async function loadHealth() {
    const container = document.getElementById('health-detail');
    if (!container) return;
    try {
        superAdminState.health = await RailSafeAPI.apiFetch('/api/super-admin/system/health');
        renderDetailPanel(container, superAdminState.health, 'Health data unavailable.');
    } catch (error) {
        superAdminState.health = null;
        renderPanelError(container, error, 'Could not load system health.');
    }
}

function isRestrictedClassification(value) {
    return /demo|simulat|restricted|sample|test|unverified/i.test(String(value || ''));
}

function classificationBadge(value) {
    const text = displayValue(value);
    const cls = isRestrictedClassification(value) ? 'badge-awaiting' : 'badge-soft';
    return `<span class="${cls}">${RailSafeUI.escapeHtml(text)}</span>`;
}

async function loadDataSources() {
    const tbody = document.getElementById('datasources-body');
    if (!tbody) return;
    try {
        const data = await RailSafeAPI.apiFetch('/api/super-admin/data-sources');
        superAdminState.dataSources = Array.isArray(data)
            ? data : (data && Array.isArray(data.sources)) ? data.sources : [];
        if (!superAdminState.dataSources.length) {
            RailSafeUI.emptyState(tbody, 'bi-database', 'No data sources declared',
                'Configured sources will appear here.');
            return;
        }
        tbody.innerHTML = superAdminState.dataSources.map(source => `<tr>
            <td>
                <div class="fw-semibold">${RailSafeUI.escapeHtml(displayValue(
                    source.name || source.id || source.sourceName))}</div>
                <div class="text-muted-rs" style="font-size:.74rem;">${RailSafeUI.escapeHtml(
                    displayValue(source.description || source.url || source.endpoint))}</div>
            </td>
            <td>${RailSafeUI.escapeHtml(displayValue(source.type || source.kind || source.provider))}</td>
            <td>${classificationBadge(source.classification || source.dataClassification || source.mode)}</td>
            <td>${RailSafeUI.escapeHtml(displayValue(source.status || source.health))}</td>
            <td class="d-none d-md-table-cell" style="font-size:.76rem;">
                ${source.lastCheckedAt || source.checkedAt
                    ? RailSafeUI.escapeHtml(RailSafeUI.formatDateTime(
                        source.lastCheckedAt || source.checkedAt))
                    : 'Not Available'}
            </td>
        </tr>`).join('');
    } catch (error) {
        superAdminState.dataSources = [];
        RailSafeUI.emptyState(tbody, 'bi-exclamation-triangle', 'Could not load data sources', error.message);
    }
}

async function loadSecurity() {
    const container = document.getElementById('security-detail');
    if (!container) return;
    try {
        superAdminState.security = await RailSafeAPI.apiFetch('/api/super-admin/security');
        renderDetailPanel(container, superAdminState.security, 'Security data unavailable.');
    } catch (error) {
        superAdminState.security = null;
        renderPanelError(container, error, 'Could not load security settings.');
    }
}

async function loadConfig() {
    const container = document.getElementById('config-detail');
    if (!container) return;
    try {
        superAdminState.config = await RailSafeAPI.apiFetch('/api/super-admin/config');
        renderDetailPanel(container, superAdminState.config, 'Configuration unavailable.');
    } catch (error) {
        superAdminState.config = null;
        renderPanelError(container, error, 'Could not load configuration.');
    }
}

async function loadBackups() {
    const tbody = document.getElementById('backups-body');
    if (!tbody) return;
    try {
        const data = await RailSafeAPI.apiFetch('/api/super-admin/backups');
        superAdminState.backups = Array.isArray(data)
            ? data : (data && Array.isArray(data.backups)) ? data.backups : [];
        if (!superAdminState.backups.length) {
            RailSafeUI.emptyState(tbody, 'bi-archive', 'No backup records',
                'Recorded backups will appear here.');
            return;
        }
        tbody.innerHTML = superAdminState.backups.map(backup => `<tr>
            <td>
                <div class="fw-semibold">${RailSafeUI.escapeHtml(displayValue(
                    backup.name || backup.id || backup.fileName))}</div>
                <div class="text-muted-rs" style="font-size:.74rem;">${RailSafeUI.escapeHtml(
                    displayValue(backup.location || backup.type || backup.status))}</div>
            </td>
            <td>${backup.createdAt || backup.timestamp
                ? RailSafeUI.escapeHtml(RailSafeUI.formatDateTime(backup.createdAt || backup.timestamp))
                : 'Not Available'}</td>
            <td><span class="badge-soft">${RailSafeUI.escapeHtml(displayValue(backup.status))}</span></td>
            <td class="d-none d-md-table-cell">${RailSafeUI.escapeHtml(displayValue(backup.size))}</td>
        </tr>`).join('');
    } catch (error) {
        superAdminState.backups = [];
        RailSafeUI.emptyState(tbody, 'bi-exclamation-triangle', 'Could not load backups', error.message);
    }
}
