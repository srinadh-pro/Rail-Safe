document.addEventListener('DOMContentLoaded', async () => {
    await RailSafeUI.initShell('home');
    await loadHomeGates();
});

async function loadHomeGates() {
    const container = document.getElementById('home-gates');
    try {
        const gates = await RailSafeAPI.apiFetch('/api/gates');
        const counts = { OPEN: 0, CLOSED: 0, UNKNOWN: 0 };
        gates.forEach(gate => {
            const status = gate.displayStatus || 'UNKNOWN';
            counts[status] = (counts[status] || 0) + 1;
        });

        document.getElementById('stat-total').textContent = gates.length;
        document.getElementById('stat-open').textContent = counts.OPEN;
        document.getElementById('stat-closed').textContent = counts.CLOSED;
        document.getElementById('stat-unknown').textContent = counts.UNKNOWN;

        if (!gates.length) {
            RailSafeUI.emptyState(container, 'bi-signpost', 'No verified crossings are available yet',
                'Crossings will appear here once they have been configured and reported.');
            return;
        }

        container.innerHTML = gates.slice(0, 6).map(gate => `
            <div class="col-md-6 col-xl-4">
                <div class="gate-card ${RailSafeUI.statusClass(gate.displayStatus)}">
                    <div class="d-flex align-items-start justify-content-between gap-2">
                        ${RailSafeUI.statusBadge(gate.displayStatus)}
                        ${RailSafeUI.verificationBadge(gate.verificationStatus)}
                    </div>
                    <h3>${RailSafeUI.escapeHtml(gate.name)}</h3>
                    <div class="text-muted-rs" style="font-size:.78rem;">
                        <i class="bi bi-geo-alt me-1"></i>${RailSafeUI.escapeHtml(gate.location)}
                    </div>
                    <div class="gate-meta">
                        <span><i class="bi bi-upc-scan"></i>${RailSafeUI.escapeHtml(gate.gateCode)}</span>
                        <span><i class="bi bi-broadcast"></i>${RailSafeUI.sourceLabel(gate.statusSource)}</span>
                        <span><i class="bi bi-clock-history"></i>Updated ${RailSafeUI.timeAgo(gate.lastUpdatedAt)}</span>
                    </div>
                    ${gate.stale ? `<div class="stale-note">
                        <i class="bi bi-exclamation-triangle me-1"></i>
                        Status is stale, so it is displayed as UNKNOWN. Last update:
                        ${RailSafeUI.formatDateTime(gate.lastUpdatedAt)}.
                    </div>` : ''}
                    <div class="mt-3">
                        <a href="gate.html?id=${gate.id}" class="btn btn-sm btn-soft w-100">
                            View gate details <i class="bi bi-arrow-right ms-1"></i>
                        </a>
                    </div>
                </div>
            </div>
        `).join('');
    } catch (error) {
        RailSafeUI.emptyState(container, 'bi-wifi-off', 'Could not load crossings', error.message);
    }
}
