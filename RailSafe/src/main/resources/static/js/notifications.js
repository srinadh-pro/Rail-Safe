const notificationState = {
    items: [],
    filter: 'all',
    lastSeenId: Number(sessionStorage.getItem('railsafe.lastNotificationId') || 0),
    initialised: false,
    pollTimer: null
};

document.addEventListener('DOMContentLoaded', async () => {
    const user = await RailSafeUI.initShell('notifications');
    if (!RailSafeUI.guard(user)) return;

    document.querySelectorAll('.filter-btn').forEach(button => {
        button.addEventListener('click', () => {
            notificationState.filter = button.dataset.filter;
            document.querySelectorAll('.filter-btn').forEach(other => {
                other.classList.toggle('active', other === button);
                other.classList.toggle('btn-soft', other === button);
                other.classList.toggle('btn-outline-navy', other !== button);
            });
            renderNotifications();
        });
    });

    document.getElementById('mark-all-read').addEventListener('click', markAllRead);
    document.getElementById('refresh-notifications').addEventListener('click', loadNotifications);
    document.getElementById('enable-browser').addEventListener('click', requestBrowserPermission);

    updatePermissionButton();
    await loadNotifications();
    startPolling();
});

function notificationIcon(type) {
    switch (type) {
        case 'GATE_CLOSED': return ['closed', 'bi-x-octagon'];
        case 'GATE_OPENED': return ['open', 'bi-check-circle'];
        case 'GATE_UNKNOWN': return ['system', 'bi-question-circle'];
        case 'TRAIN_APPROACHING': return ['approaching', 'bi-train-front'];
        case 'GATE_CLOSING_SOON': return ['approaching', 'bi-hourglass-split'];
        case 'GATE_OPENING_SOON': return ['closed', 'bi-clock-history'];
        case 'ALTERNATIVE_ROUTE': return ['route', 'bi-signpost-split'];
        case 'REPORT': return ['report', 'bi-send-check'];
        default: return ['system', 'bi-info-circle'];
    }
}

async function loadNotifications() {
    const container = document.getElementById('notification-list');
    try {
        notificationState.items = await RailSafeAPI.apiFetch('/api/notifications');
        notificationState.initialised = true;
        const unread = notificationState.items.filter(item => !item.read);
        const unreadBadge = document.getElementById('unread-badge');
        unreadBadge.textContent = unread.length;
        unreadBadge.style.display = unread.length ? 'inline-block' : 'none';

        const maxId = notificationState.items.reduce((max, item) => Math.max(max, item.id), 0);
        if (!notificationState.lastSeenId) {
            notificationState.lastSeenId = maxId;
            sessionStorage.setItem('railsafe.lastNotificationId', String(maxId));
        }
        renderNotifications();
    } catch (error) {
        if (error.status === 401) {
            RailSafeAPI.redirectToLogin();
            return;
        }
        RailSafeUI.emptyState(container, 'bi-wifi-off', 'Could not load notifications', error.message);
    }
}

function renderNotifications() {
    const container = document.getElementById('notification-list');
    const items = notificationState.filter === 'unread'
        ? notificationState.items.filter(item => !item.read)
        : notificationState.items;

    if (!items.length) {
        RailSafeUI.emptyState(container, 'bi-bell-slash',
            notificationState.filter === 'unread' ? 'No unread notifications' : 'No notifications yet',
            'Gate updates and report decisions will appear here.');
        return;
    }

    container.innerHTML = items.map(item => {
        const [iconClass, icon] = notificationIcon(item.type);
        const gateLink = item.gateId
            ? `<a href="gate.html?id=${item.gateId}" class="small fw-bold">View gate</a>`
            : '';
        return `<div class="notification-item ${item.read ? '' : 'unread'}" data-id="${item.id}">
            <div class="notification-icon ${iconClass}"><i class="bi ${icon}"></i></div>
            <div class="flex-grow-1">
                <div class="d-flex flex-wrap justify-content-between gap-2">
                    <h3>${RailSafeUI.escapeHtml(item.title)}</h3>
                    <span class="text-muted-rs" style="font-size:.71rem;">${RailSafeUI.timeAgo(item.createdAt)}</span>
                </div>
                <p>${RailSafeUI.escapeHtml(item.message)}</p>
                <div class="d-flex flex-wrap align-items-center gap-2 mt-2">
                    ${gateLink}
                    <span class="text-muted-rs" style="font-size:.7rem;">${RailSafeUI.formatDateTime(item.createdAt)}</span>
                    ${item.read ? '' : `<button class="btn btn-sm btn-soft py-0 px-2 mark-read" data-id="${item.id}">
                        Mark read</button>`}
                </div>
            </div>
        </div>`;
    }).join('');

    container.querySelectorAll('.mark-read').forEach(button => {
        button.addEventListener('click', () => markRead(Number(button.dataset.id)));
    });
}

async function markRead(id) {
    try {
        await RailSafeAPI.apiFetch(`/api/notifications/${id}/read`, { method: 'PUT' });
        const item = notificationState.items.find(notification => notification.id === id);
        if (item) item.read = true;
        updateUnreadBadges();
        renderNotifications();
    } catch (error) {
        RailSafeUI.toast(error.message, 'danger');
    }
}

async function markAllRead() {
    try {
        const result = await RailSafeAPI.apiFetch('/api/notifications/read-all', { method: 'PUT' });
        notificationState.items.forEach(item => { item.read = true; });
        updateUnreadBadges();
        renderNotifications();
        RailSafeUI.toast(result.message || 'All notifications marked as read.', 'success');
    } catch (error) {
        RailSafeUI.toast(error.message, 'danger');
    }
}

function updateUnreadBadges() {
    const unread = notificationState.items.filter(item => !item.read).length;
    const badge = document.getElementById('unread-badge');
    badge.textContent = unread;
    badge.style.display = unread ? 'inline-block' : 'none';
}

function updatePermissionButton() {
    const button = document.getElementById('enable-browser');
    if (!button) return;
    if (!('Notification' in window)) {
        button.disabled = true;
        button.innerHTML = '<i class="bi bi-bell-slash me-1"></i>Browser alerts unavailable';
        return;
    }
    if (Notification.permission === 'granted') {
        button.innerHTML = '<i class="bi bi-bell-fill me-1"></i>Browser alerts enabled';
        button.classList.remove('btn-outline-navy');
        button.classList.add('btn-success');
    } else if (Notification.permission === 'denied') {
        button.innerHTML = '<i class="bi bi-bell-slash me-1"></i>Browser alerts blocked';
        button.classList.remove('btn-outline-navy');
        button.classList.add('btn-outline-secondary');
    }
}

async function requestBrowserPermission() {
    if (!('Notification' in window)) {
        RailSafeUI.toast('This browser does not support desktop notifications.', 'warning');
        return;
    }
    const permission = await Notification.requestPermission();
    updatePermissionButton();
    if (permission === 'granted') {
        RailSafeUI.toast('Browser alerts enabled for this device.', 'success');
        new Notification('RailSafe browser alerts enabled', {
            body: 'New gate alerts will appear here while RailSafe is open in a tab.',
            icon: 'favicon.ico'
        });
    } else {
        RailSafeUI.toast('Browser alerts were not enabled. In-app notifications still work.', 'warning');
    }
}

function startPolling() {
    notificationState.pollTimer = window.setInterval(async () => {
        try {
            const unread = await RailSafeAPI.apiFetch('/api/notifications?unreadOnly=true');
            const maxId = unread.reduce((max, item) => Math.max(max, item.id), notificationState.lastSeenId);
            const newItems = unread.filter(item => item.id > notificationState.lastSeenId);

            if (notificationState.initialised && newItems.length) {
                newItems.forEach(showBrowserNotification);
                notificationState.lastSeenId = maxId;
                sessionStorage.setItem('railsafe.lastNotificationId', String(maxId));
                await loadNotifications();
                RailSafeUI.toast(`${newItems.length} new gate alert(s).`, 'info');
            } else if (!notificationState.lastSeenId && maxId) {
                notificationState.lastSeenId = maxId;
                sessionStorage.setItem('railsafe.lastNotificationId', String(maxId));
            }
        } catch (error) {
            if (error.status === 401) {
                window.clearInterval(notificationState.pollTimer);
                notificationState.pollTimer = null;
                return;
            }
            console.warn('Notification polling failed:', error);
        }
    }, 20000);
}

function showBrowserNotification(item) {
    if (!('Notification' in window) || Notification.permission !== 'granted') return;
    try {
        new Notification(item.title, {
            body: item.message,
            tag: `railsafe-notification-${item.id}`,
            icon: 'favicon.ico'
        });
    } catch (error) {
        console.warn('Could not show browser notification:', error);
    }
}
