/* RailSafe map page: gate markers, geocoding, OSRM route alternatives,
   ­150 m route/crossing checks, saved routes and optional route watching.
   All gate status is reported or verified information, never a live railway signal. */
const RAILSAFE_MAP_CENTER = { lat: 17.3850, lng: 78.4867 };
const ROUTE_GATE_RADIUS_M = 150;
const ROUTE_WATCH_KEY = 'railsafe.watchedRoute';
const ROUTE_COLORS = ['#2563eb', '#dc2626', '#16a34a', '#f97316', '#eab308'];
const DEMO_ROUTE = {
    start: {
        lat: 17.3600,
        lng: 78.4700,
        label: 'Demo start – Hyderabad west'
    },
    dest: {
        lat: 17.4300,
        lng: 78.5100,
        label: 'Demo destination – Hyderabad east'
    }
};

const mapState = {
    map: null,
    markerLayer: null,
    routeLayer: null,
    gates: [],
    markers: new Map(),
    statusFilter: 'ALL',
    start: null,
    dest: null,
    routes: [],
    selectedRouteIndex: -1,
    pickMode: null,
    startMarker: null,
    destMarker: null,
    user: null,
    watchTimer: null,
    notifiedRouteKeys: new Set(),
    demoRouteActive: false
};

document.addEventListener('DOMContentLoaded', async () => {
    mapState.user = await RailSafeUI.initShell('map');

    initMap();
    bindUi();
    await loadGates();

    if (mapState.user) {
        loadSavedRoutes();
    } else {
        document.getElementById('saved-routes-mini').innerHTML = `
            <div class="text-muted-rs" style="font-size:.8rem;">
                <a href="login.html?next=map.html" class="fw-bold">Log in</a> to save routes and watch them later.
            </div>`;
    }

    restoreRouteWatch();
    applyUrlState();
});

/* ------------------------------------------------------------------ */
/* Map setup                                                           */
/* ------------------------------------------------------------------ */

function initMap() {
    const element = document.getElementById('map');
    if (!window.L) {
        element.innerHTML = `<div class="p-4 text-center text-muted-rs">
            <i class="bi bi-wifi-off fs-3 d-block mb-2"></i>
            The map library could not load. Check your internet connection, then refresh the page.
        </div>`;
        return;
    }

    mapState.map = L.map('map', {
        zoomControl: true,
        scrollWheelZoom: true
    }).setView([RAILSAFE_MAP_CENTER.lat, RAILSAFE_MAP_CENTER.lng], 13);

    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
        maxZoom: 19,
        attribution: '&copy; <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noopener">OpenStreetMap</a> contributors'
    }).addTo(mapState.map);

    L.control.scale({ imperial: false }).addTo(mapState.map);
    mapState.markerLayer = L.layerGroup().addTo(mapState.map);
    mapState.routeLayer = L.layerGroup().addTo(mapState.map);

    mapState.map.on('click', event => {
        if (!mapState.pickMode) return;
        const { lat, lng } = event.latlng;
        setWaypoint(mapState.pickMode, {
            lat,
            lng,
            label: `Map point (${lat.toFixed(5)}, ${lng.toFixed(5)})`
        });
        cancelPickMode();
    });

    const legend = L.control({ position: 'bottomleft' });
    legend.onAdd = function () {
        const div = L.DomUtil.create('div', 'map-legend');
        div.innerHTML = `
            <div><span class="legend-dot" style="background:#16a34a"></span>OPEN (reported)</div>
            <div><span class="legend-dot" style="background:#dc2626"></span>CLOSED (reported)</div>
            <div><span class="legend-dot" style="background:#6b7280"></span>UNKNOWN / stale</div>
            <div class="map-legend-divider"></div>
            <div><span class="legend-line" style="background:${ROUTE_COLORS[0]}"></span>Primary route</div>
            <div><span class="legend-line" style="background:${ROUTE_COLORS[1]}"></span>Alternative 1</div>
            <div><span class="legend-line" style="background:${ROUTE_COLORS[2]}"></span>Alternative 2</div>
            <div><span class="legend-line" style="background:${ROUTE_COLORS[3]}"></span>Alternative 3</div>
            <div><span class="legend-line" style="background:${ROUTE_COLORS[4]}"></span>Alternative 4</div>
            <div class="text-muted-rs" style="line-height:1.35;margin-top:.25rem;">Reported data only</div>`;
        return div;
    };
    legend.addTo(mapState.map);
}

function bindUi() {
    document.getElementById('refresh-gates').addEventListener('click', async () => {
        await loadGates();
        RailSafeUI.toast('Crossing status refreshed.', 'success');
    });
    document.getElementById('fit-gates').addEventListener('click', fitAllGates);
    document.getElementById('status-filter').addEventListener('change', event => {
        mapState.statusFilter = event.target.value;
        applyGateFilter();
    });

    document.getElementById('search-start').addEventListener('click', () => searchPlace('start'));
    document.getElementById('search-dest').addEventListener('click', () => searchPlace('dest'));
    document.getElementById('start-input').addEventListener('keydown', event => {
        if (event.key === 'Enter') {
            event.preventDefault();
            searchPlace('start');
        }
    });
    document.getElementById('dest-input').addEventListener('keydown', event => {
        if (event.key === 'Enter') {
            event.preventDefault();
            searchPlace('dest');
        }
    });

    document.getElementById('start-input').addEventListener('input', event => {
        if (mapState.start && mapState.start.label !== event.target.value) {
            mapState.start = null;
            updateWaypointLabel('start');
            renderWaypointMarkers();
            clearRouteResult();
        }
    });
    document.getElementById('dest-input').addEventListener('input', event => {
        if (mapState.dest && mapState.dest.label !== event.target.value) {
            mapState.dest = null;
            updateWaypointLabel('dest');
            renderWaypointMarkers();
            clearRouteResult();
        }
    });

    document.getElementById('locate-start').addEventListener('click', useMyLocationAsStart);
    document.getElementById('pick-start').addEventListener('click', () => togglePickMode('start'));
    document.getElementById('pick-dest').addEventListener('click', () => togglePickMode('dest'));
    document.getElementById('clear-route').addEventListener('click', clearRoute);
    document.getElementById('plan-route').addEventListener('click', planRoute);
    document.getElementById('load-demo-route').addEventListener('click', loadDemoRoute);

    document.getElementById('save-route').addEventListener('click', openSaveRouteModal);
    document.getElementById('save-route-form').addEventListener('submit', saveRoute);
    document.getElementById('watch-route').addEventListener('click', toggleRouteWatch);
}

/* ------------------------------------------------------------------ */
/* Gate data, markers and list                                         */
/* ------------------------------------------------------------------ */

async function loadGates() {
    const list = document.getElementById('map-gate-list');
    if (!mapState.gates.length) {
        RailSafeUI.loading(list, 'Loading crossings…');
    }

    try {
        mapState.gates = await RailSafeAPI.apiFetch('/api/gates');
        renderGateMarkers();
        applyGateFilter();
        if (mapState.routes.length) {
            reanalyseRoutes();
        }
    } catch (error) {
        RailSafeUI.emptyState(list, 'bi-wifi-off', 'Could not load crossings', error.message);
        RailSafeUI.inlineAlert(document.getElementById('route-alert'), 'warning',
            '<i class="bi bi-wifi-off me-1"></i><strong>Crossing data could not be loaded.</strong> ' +
            RailSafeUI.escapeHtml(error.message));
    }
}

function renderGateMarkers() {
    if (!mapState.map) return;
    mapState.markerLayer.clearLayers();
    mapState.markers.clear();

    mapState.gates.forEach(gate => {
        const status = (gate.displayStatus || 'UNKNOWN').toLowerCase();
        const icon = L.divIcon({
            className: 'gate-marker',
            html: `<span class="gate-pin ${status}"></span>`,
            iconSize: [22, 22],
            iconAnchor: [11, 11],
            popupAnchor: [0, -12]
        });
        const marker = L.marker([gate.latitude, gate.longitude], {
            icon,
            title: `${gate.name} – ${gate.displayStatus}`,
            alt: `${gate.name}, reported ${gate.displayStatus}`
        });
        marker.bindPopup(buildGatePopup(gate), { maxWidth: 300 });
        mapState.markers.set(gate.id, marker);
    });
}

function buildGatePopup(gate) {
    const stale = gate.stale
        ? `<div class="stale-note"><i class="bi bi-exclamation-triangle me-1"></i>
             Stale data → displayed as UNKNOWN.</div>`
        : '';
    return `<div>
        <div class="d-flex justify-content-between align-items-start gap-2">
            ${RailSafeUI.statusBadge(gate.displayStatus)}
            <span class="badge-soft">${RailSafeUI.escapeHtml(gate.gateCode)}</span>
        </div>
        <h4>${RailSafeUI.escapeHtml(gate.name)}</h4>
        <div class="popup-meta"><i class="bi bi-geo-alt me-1"></i>${RailSafeUI.escapeHtml(gate.location)}</div>
        <div class="popup-meta mt-1">
            <i class="bi bi-broadcast me-1"></i>${RailSafeUI.escapeHtml(RailSafeUI.sourceLabel(gate.statusSource))}
            <span class="mx-1">·</span>
            <i class="bi bi-clock me-1"></i>${RailSafeUI.escapeHtml(RailSafeUI.timeAgo(gate.lastUpdatedAt))}
        </div>
        <div class="mt-1">${RailSafeUI.verificationBadge(gate.verificationStatus)}</div>
        ${stale}
        <div class="mt-2">
            <a href="gate.html?id=${gate.id}" class="btn btn-sm btn-soft">Gate details</a>
        </div>
    </div>`;
}

function applyGateFilter() {
    if (!mapState.map) {
        renderGateList();
        return;
    }

    mapState.gates.forEach(gate => {
        const marker = mapState.markers.get(gate.id);
        if (!marker) return;
        const matches = mapState.statusFilter === 'ALL' ||
            gate.displayStatus === mapState.statusFilter;
        if (matches) {
            if (!mapState.markerLayer.hasLayer(marker)) {
                mapState.markerLayer.addLayer(marker);
            }
        } else if (mapState.markerLayer.hasLayer(marker)) {
            mapState.markerLayer.removeLayer(marker);
        }
    });

    renderGateList();
}

function renderGateList() {
    const container = document.getElementById('map-gate-list');
    const gates = mapState.gates.filter(gate =>
        mapState.statusFilter === 'ALL' || gate.displayStatus === mapState.statusFilter);

    document.getElementById('gate-count').textContent =
        `${gates.length} crossing${gates.length === 1 ? '' : 's'}`;

    if (!gates.length) {
        RailSafeUI.emptyState(container, 'bi-filter-circle-xmark', 'No crossings match the current filters',
            'Choose another status filter to see configured crossings.');
        return;
    }

    container.innerHTML = gates.map(gate => `
        <div class="col-md-6">
            <div class="gate-card h-100 ${RailSafeUI.statusClass(gate.displayStatus)}">
                <div class="d-flex justify-content-between align-items-start gap-2">
                    ${RailSafeUI.statusBadge(gate.displayStatus)}
                    <span class="badge-soft">${RailSafeUI.escapeHtml(gate.gateCode)}</span>
                </div>
                <h3>${RailSafeUI.escapeHtml(gate.name)}</h3>
                <div class="gate-meta">
                    <span><i class="bi bi-broadcast"></i>${RailSafeUI.sourceLabel(gate.statusSource)}</span>
                    <span><i class="bi bi-clock"></i>${RailSafeUI.timeAgo(gate.lastUpdatedAt)}</span>
                </div>
                ${gate.stale ? '<div class="stale-note"><i class="bi bi-exclamation-triangle me-1"></i>Stale data → UNKNOWN.</div>' : ''}
                <div class="mt-2 d-flex gap-2">
                    <button class="btn btn-sm btn-soft flex-grow-1 focus-gate" data-id="${gate.id}" type="button">
                        <i class="bi bi-pin-map me-1"></i>Show on map
                    </button>
                    <a href="gate.html?id=${gate.id}" class="btn btn-sm btn-outline-navy">Details</a>
                </div>
            </div>
        </div>
    `).join('');

    container.querySelectorAll('.focus-gate').forEach(button => {
        button.addEventListener('click', () => focusGate(Number(button.dataset.id)));
    });
}

function fitAllGates() {
    if (!mapState.map || !mapState.gates.length) {
        RailSafeUI.toast('No crossings to fit on the map.', 'warning');
        return;
    }
    const bounds = L.latLngBounds(mapState.gates.map(gate => [gate.latitude, gate.longitude]));
    mapState.map.fitBounds(bounds, { padding: [35, 35] });
}

function focusGate(id) {
    if (!mapState.map) return;
    const gate = mapState.gates.find(item => item.id === id);
    if (!gate) return;

    if (mapState.statusFilter !== 'ALL') {
        mapState.statusFilter = 'ALL';
        document.getElementById('status-filter').value = 'ALL';
        applyGateFilter();
    }

    const marker = mapState.markers.get(id);
    if (marker && !mapState.markerLayer.hasLayer(marker)) {
        mapState.markerLayer.addLayer(marker);
    }
    mapState.map.setView([gate.latitude, gate.longitude], 16, { animate: true });
    if (marker) {
        setTimeout(() => marker.openPopup(), 250);
    }
    document.getElementById('map').scrollIntoView({ behavior: 'smooth', block: 'center' });
}

/* ------------------------------------------------------------------ */
/* Place search and map picking                                        */
/* ------------------------------------------------------------------ */

async function searchPlace(kind) {
    const input = document.getElementById(`${kind}-input`);
    const button = document.getElementById(kind === 'start' ? 'search-start' : 'search-dest');
    const query = input.value.trim();
    if (!query) {
        RailSafeUI.toast('Type a place or address first.', 'warning');
        return;
    }

    const original = button.innerHTML;
    button.disabled = true;
    button.innerHTML = '<span class="spinner-border spinner-border-sm"></span>';

    try {
        const url = 'https://nominatim.openstreetmap.org/search?format=jsonv2&limit=5&q=' +
            encodeURIComponent(query);
        const response = await fetch(url, {
            headers: { 'Accept': 'application/json' }
        });
        if (!response.ok) {
            throw new Error(`Place search returned HTTP ${response.status}.`);
        }
        const results = await response.json();
        renderGeocodeResults(kind, results);
    } catch (error) {
        RailSafeUI.toast('Place search failed. You can select a point on the map instead.', 'warning');
        const container = document.getElementById(`${kind}-results`);
        container.classList.remove('hidden');
        container.innerHTML = `<div class="list-group-item small text-muted-rs">
            Place search is unavailable right now. Use “Pick on map” or enter coordinates instead.
        </div>`;
    } finally {
        button.disabled = false;
        button.innerHTML = original;
    }
}

function renderGeocodeResults(kind, results) {
    const container = document.getElementById(`${kind}-results`);
    container.classList.remove('hidden');

    if (!results.length) {
        container.innerHTML = `<div class="list-group-item small text-muted-rs">
            No places found. Try a more specific name or pick a point on the map.
        </div>`;
        return;
    }

    container.innerHTML = results.map((result, index) => `
        <button type="button"
                class="list-group-item list-group-item-action geocode-result"
                data-index="${index}">
            <span class="d-block fw-semibold" style="font-size:.82rem;">${RailSafeUI.escapeHtml(result.display_name)}</span>
            <span class="text-muted-rs" style="font-size:.72rem;">
                ${Number(result.lat).toFixed(5)}, ${Number(result.lon).toFixed(5)}
            </span>
        </button>
    `).join('');

    container.querySelectorAll('.geocode-result').forEach(button => {
        button.addEventListener('click', () => {
            const result = results[Number(button.dataset.index)];
            setWaypoint(kind, {
                lat: Number(result.lat),
                lng: Number(result.lon),
                label: result.display_name
            });
            hideGeocodeResults(kind);
        });
    });
}

function hideGeocodeResults(kind) {
    const container = document.getElementById(`${kind}-results`);
    container.classList.add('hidden');
    container.innerHTML = '';
}

function setWaypoint(kind, point) {
    mapState[kind] = point;
    const input = document.getElementById(`${kind}-input`);
    input.value = point.label;
    updateWaypointLabel(kind);
    renderWaypointMarkers();
    clearRouteResult();
}

function updateWaypointLabel(kind) {
    const element = document.getElementById(`${kind}-selected`);
    const point = mapState[kind];
    if (!point) {
        element.textContent = kind === 'start' ? 'Start not selected.' : 'Destination not selected.';
        return;
    }
    element.innerHTML = `<i class="bi bi-check-circle text-success me-1"></i>
        ${RailSafeUI.escapeHtml(point.label)}
        <span class="text-muted-rs">(${point.lat.toFixed(5)}, ${point.lng.toFixed(5)})</span>`;
}

function renderWaypointMarkers() {
    if (!mapState.map) return;

    if (mapState.startMarker) {
        mapState.map.removeLayer(mapState.startMarker);
        mapState.startMarker = null;
    }
    if (mapState.destMarker) {
        mapState.map.removeLayer(mapState.destMarker);
        mapState.destMarker = null;
    }

    if (mapState.start) {
        mapState.startMarker = L.marker([mapState.start.lat, mapState.start.lng], {
            icon: L.divIcon({
                className: '',
                html: '<div class="route-marker">A</div>',
                iconSize: [30, 30],
                iconAnchor: [15, 15]
            }),
            zIndexOffset: 1000
        }).addTo(mapState.map).bindTooltip('Start: ' + RailSafeUI.escapeHtml(mapState.start.label));
    }

    if (mapState.dest) {
        mapState.destMarker = L.marker([mapState.dest.lat, mapState.dest.lng], {
            icon: L.divIcon({
                className: '',
                html: '<div class="route-marker destination">B</div>',
                iconSize: [30, 30],
                iconAnchor: [15, 15]
            }),
            zIndexOffset: 1000
        }).addTo(mapState.map).bindTooltip('Destination: ' + RailSafeUI.escapeHtml(mapState.dest.label));
    }
}

function useMyLocationAsStart() {
    if (!navigator.geolocation) {
        RailSafeUI.toast('Geolocation is not supported by this browser.', 'warning');
        return;
    }
    const button = document.getElementById('locate-start');
    button.disabled = true;
    button.innerHTML = '<span class="spinner-border spinner-border-sm me-1"></span>Locating…';

    navigator.geolocation.getCurrentPosition(position => {
        setWaypoint('start', {
            lat: position.coords.latitude,
            lng: position.coords.longitude,
            label: `My location (±${Math.round(position.coords.accuracy)} m)`
        });
        if (mapState.map) {
            mapState.map.setView([position.coords.latitude, position.coords.longitude], 15);
        }
        RailSafeUI.toast('Start point set from your location.', 'success');
        button.disabled = false;
        button.innerHTML = '<i class="bi bi-crosshair me-1"></i>My location';
    }, error => {
        const message = error.code === error.PERMISSION_DENIED
            ? 'Location permission was denied. You can search or pick a point on the map instead.'
            : 'Could not get your location. You can search or pick a point on the map instead.';
        RailSafeUI.toast(message, 'warning');
        button.disabled = false;
        button.innerHTML = '<i class="bi bi-crosshair me-1"></i>My location';
    }, { enableHighAccuracy: true, timeout: 10000, maximumAge: 60000 });
}

function togglePickMode(mode) {
    if (!mapState.map) {
        RailSafeUI.toast('The map is not available right now.', 'warning');
        return;
    }
    if (mapState.pickMode === mode) {
        cancelPickMode();
        return;
    }

    mapState.pickMode = mode;
    document.getElementById('pick-start').classList.toggle('btn-soft', mode === 'start');
    document.getElementById('pick-dest').classList.toggle('btn-soft', mode === 'dest');
    mapState.map.getContainer().style.cursor = 'crosshair';
    RailSafeUI.inlineAlert(document.getElementById('map-pick-note'), 'info',
        `<i class="bi bi-pin-map me-1"></i>Click the map to set the <strong>${mode === 'start' ? 'start' : 'destination'}</strong> point.`);
}

function cancelPickMode() {
    mapState.pickMode = null;
    document.getElementById('pick-start').classList.remove('btn-soft');
    document.getElementById('pick-dest').classList.remove('btn-soft');
    if (mapState.map) {
        mapState.map.getContainer().style.cursor = '';
    }
    RailSafeUI.clearAlert(document.getElementById('map-pick-note'));
}

/* ------------------------------------------------------------------ */
/* OSRM routing and 150 m crossing checks                              */
/* ------------------------------------------------------------------ */

function loadDemoRoute() {
    setWaypoint('start', { ...DEMO_ROUTE.start });
    setWaypoint('dest', { ...DEMO_ROUTE.dest });
    mapState.demoRouteActive = true;
    RailSafeUI.toast('Loading the demo primary route and alternatives.', 'info');
    void planRoute();
}

async function planRoute() {
    const isDemoRoute = mapState.demoRouteActive;
    mapState.demoRouteActive = false;
    if (!mapState.start || !mapState.dest) {
        RailSafeUI.toast('Select both a start and a destination first.', 'warning');
        return;
    }
    if (!mapState.map) {
        RailSafeUI.toast('The map is unavailable, so the route cannot be drawn.', 'danger');
        return;
    }

    const button = document.getElementById('plan-route');
    const original = button.innerHTML;
    button.disabled = true;
    button.innerHTML = '<span class="spinner-border spinner-border-sm me-1"></span>Calculating…';

    const url = 'https://router.project-osrm.org/route/v1/driving/' +
        `${mapState.start.lng},${mapState.start.lat};${mapState.dest.lng},${mapState.dest.lat}` +
        '?overview=full&geometries=geojson&alternatives=true';

    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 25000);

    try {
        const response = await fetch(url, { signal: controller.signal });
        if (!response.ok) {
            throw new Error(`OSRM returned HTTP ${response.status}.`);
        }
        const data = await response.json();
        if (data.code !== 'Ok' || !Array.isArray(data.routes) || !data.routes.length) {
            throw new Error(data.message || 'OSRM could not find a driving route between those points.');
        }

        mapState.routes = data.routes.map((raw, index) => {
            const coordinates = raw.geometry.coordinates.map(([lng, lat]) => ({ lat, lng }));
            return {
                index,
                distance: raw.distance,
                duration: raw.duration,
                coordinates,
                demo: isDemoRoute,
                analysis: analyseGeometry(coordinates)
            };
        });
        mapState.selectedRouteIndex = 0;

        renderRouteOptions();
        drawRouteLayers(true);
        updateRouteAlert();
        updateRouteActions();
        maybeNotifyRouteRisk(selectedRoute());
        RailSafeUI.toast('Route options calculated.', 'success');
    } catch (error) {
        // Never draw illustrative geometry: distances and alternates must come from OSRM.
        mapState.routes = [];
        drawRouteLayers(true);
        renderRouteOptions();
        updateRouteActions();
        const message = error.name === 'AbortError'
            ? 'The routing request timed out.'
            : error.message;
        RailSafeUI.inlineAlert(document.getElementById('route-alert'), 'warning',
            `<i class="bi bi-exclamation-triangle me-1"></i><strong>Route data unavailable.</strong>
             No route was drawn. ${RailSafeUI.escapeHtml(message)}
             <div class="mt-1" style="font-size:.78rem;">RailSafe never substitutes estimated geometry for a
             calculated route. You can still inspect the reported crossings on the map.</div>`);
    } finally {
        clearTimeout(timeout);
        button.disabled = false;
        button.innerHTML = original;
    }
}

function analyseGeometry(coordinates) {
    const matches = mapState.gates
        .map(gate => ({
            gate,
            distanceM: distancePointToPolylineMeters(gate.latitude, gate.longitude, coordinates)
        }))
        .filter(match => match.distanceM <= ROUTE_GATE_RADIUS_M)
        .sort((left, right) => left.distanceM - right.distanceM);

    return {
        matches,
        closed: matches.filter(match => match.gate.displayStatus === 'CLOSED'),
        unknown: matches.filter(match => match.gate.displayStatus === 'UNKNOWN'),
        open: matches.filter(match => match.gate.displayStatus === 'OPEN')
    };
}

function reanalyseRoutes() {
    mapState.routes.forEach(route => {
        route.analysis = analyseGeometry(route.coordinates);
    });
    renderRouteOptions();
    updateRouteAlert();
    updateRouteActions();
}

function selectedRoute() {
    return mapState.selectedRouteIndex >= 0
        ? mapState.routes[mapState.selectedRouteIndex]
        : null;
}

function renderRouteOptions() {
    const container = document.getElementById('route-options');
    if (!mapState.routes.length) {
        renderEmptyRouteOptions();
        return;
    }

    let html = mapState.routes.map((route, index) => {
        const analysis = route.analysis;
        const selected = index === mapState.selectedRouteIndex;
        const closedBadge = analysis.closed.length
            ? `<span class="status-badge status-closed"><span class="dot"></span>${analysis.closed.length} reported CLOSED</span>`
            : `<span class="status-badge status-open"><span class="dot"></span>0 reported CLOSED</span>`;
        const unknownBadge = analysis.unknown.length
            ? `<span class="status-badge status-unknown"><span class="dot"></span>${analysis.unknown.length} UNKNOWN</span>`
            : '';
        const nearText = analysis.matches.length
            ? `Passes within 150 m of ${analysis.matches.length} crossing(s). Closest: ` +
              `${RailSafeUI.escapeHtml(analysis.matches[0].gate.name)} (${Math.round(analysis.matches[0].distanceM)} m).`
            : 'No configured crossing is within 150 m of this calculated route. Coverage is limited.';

        return `<button type="button" class="route-option w-100 text-start ${selected ? 'selected' : ''}"
                        data-route-index="${index}">
            <div class="d-flex justify-content-between align-items-start gap-2">
                <div>
                    <span class="small-label">
                        <span class="route-swatch" style="background:${ROUTE_COLORS[index % ROUTE_COLORS.length]}"></span>
                        ${index === 0
                            ? (route.demo ? 'Demo primary route' : 'Primary route')
                            : (route.demo ? `Demo alternative ${index}` : `Alternative ${index}`)}
                    </span>
                    <div class="fw-bold mt-1" style="font-size:.9rem;">
                        ${formatDistance(route.distance)} · ${formatDuration(route.duration)}
                    </div>
                </div>
                <i class="bi ${selected ? 'bi-check-circle-fill text-primary' : 'bi-circle text-muted'}"></i>
            </div>
            <div class="d-flex flex-wrap gap-1 mt-2">
                ${closedBadge}
                ${unknownBadge}
                <span class="badge-soft">${analysis.matches.length} near route</span>
            </div>
            <div class="text-muted-rs mt-2" style="font-size:.74rem;line-height:1.45;">${nearText}</div>
        </button>`;
    }).join('');

    const hasRisk = mapState.routes.some(route =>
        route.analysis.closed.length || route.analysis.unknown.length);
    const safeAlternates = mapState.routes.slice(1).filter(route =>
        !route.analysis.closed.length && !route.analysis.unknown.length);
    if (hasRisk && !safeAlternates.length) {
        html += `<div class="alert-rs alert-rs-danger mt-2" style="font-size:.78rem;">
            <i class="bi bi-x-octagon me-1"></i>
            <strong>No safe alternate route is available</strong> among the calculated options.
            Do not treat a route near a reported CLOSED or UNKNOWN crossing as safe.
        </div>`;
    }
    container.innerHTML = html;

    container.querySelectorAll('[data-route-index]').forEach(button => {
        button.addEventListener('click', () => selectRoute(Number(button.dataset.routeIndex)));
    });
}

function renderEmptyRouteOptions() {
    document.getElementById('route-options').innerHTML = `
        <div class="empty-state py-4">
            <i class="bi bi-signpost-split"></i>
            <p class="fw-bold text-dark mb-1">No route calculated</p>
            <p>Choose two points and press Calculate routes.</p>
        </div>`;
}

function selectRoute(index) {
    if (index < 0 || index >= mapState.routes.length) return;
    mapState.selectedRouteIndex = index;
    renderRouteOptions();
    drawRouteLayers(true);
    updateRouteAlert();
    updateRouteActions();
    maybeNotifyRouteRisk(selectedRoute());
}

function drawRouteLayers(fitBounds) {
    if (!mapState.map) return;
    mapState.routeLayer.clearLayers();

    const order = mapState.routes.map((_, index) => index)
        .sort((left, right) => {
            if (left === mapState.selectedRouteIndex) return 1;
            if (right === mapState.selectedRouteIndex) return -1;
            return left - right;
        });

    order.forEach(index => {
        const route = mapState.routes[index];
        const selected = index === mapState.selectedRouteIndex;
        const color = ROUTE_COLORS[index % ROUTE_COLORS.length];
        const line = L.polyline(route.coordinates.map(point => [point.lat, point.lng]), {
            color,
            weight: selected ? 6 : 4,
            opacity: selected ? 0.92 : 0.68,
            dashArray: selected ? null : '6 7',
            lineJoin: 'round'
        });
        const routeName = index === 0 ? 'Primary' : `Alternative ${index}`;
        line.bindTooltip(
            `${route.demo ? 'Demo ' : ''}${routeName} · ${formatDistance(route.distance)}`,
            { sticky: true }
        );
        line.on('click', () => selectRoute(index));
        mapState.routeLayer.addLayer(line);
    });

    if (fitBounds) {
        const route = selectedRoute();
        if (route && route.coordinates.length) {
            const bounds = L.latLngBounds(route.coordinates.map(point => [point.lat, point.lng]));
            mapState.map.fitBounds(bounds, { padding: [35, 35] });
        }
    }
}

function updateRouteAlert() {
    const container = document.getElementById('route-alert');
    const route = selectedRoute();
    if (!route) {
        RailSafeUI.clearAlert(container);
        return;
    }

    const analysis = route.analysis;
    let type;
    let heading;
    let detail;

    if (analysis.closed.length) {
        type = 'danger';
        heading = 'This route passes near reported CLOSED crossing(s).';
        detail = `<ul class="mb-0 mt-1 ps-3">${analysis.closed.map(match =>
            `<li><a href="gate.html?id=${match.gate.id}" class="fw-bold">${RailSafeUI.escapeHtml(match.gate.name)}</a>
                — reported CLOSED, ${Math.round(match.distanceM)} m from the route.</li>`).join('')}</ul>
            <div class="mt-1">Consider another route or allow extra time. Never drive around a closed barrier.</div>
            ${mapState.routes.slice(1).some(route =>
                !route.analysis.closed.length && !route.analysis.unknown.length)
                ? '<div class="mt-1 fw-bold">A safer alternative is plotted below; select it to inspect the crossings.</div>'
                : '<div class="mt-1 fw-bold">No safe alternate route is available among the calculated options.</div>'}`;
    } else if (analysis.unknown.length) {
        type = 'warning';
        heading = 'No reported CLOSED crossing was detected within 150 m, but some crossings are UNKNOWN.';
        detail = `<div class="mt-1">${analysis.unknown.length} crossing(s) near this route have missing or stale status.
            UNKNOWN is not the same as OPEN. Obey physical barriers and staff.</div>`;
    } else if (!analysis.matches.length) {
        type = 'info';
        heading = 'No configured crossing falls within 150 m of this calculated route.';
        detail = '<div class="mt-1">RailSafe can only show crossings it has on record. This does not mean there are no railway crossings on the road.</div>';
    } else {
        type = 'success';
        heading = 'No reported CLOSED crossing was detected within 150 m of this calculated route.';
        detail = `<div class="mt-1">This is not a safety guarantee. ${analysis.open.length} nearby crossing(s) are
            reported OPEN, but every status may be old or unverified.</div>`;
    }

    RailSafeUI.inlineAlert(container, type, `
        <div class="d-flex gap-2">
            <i class="bi ${type === 'danger' ? 'bi-x-octagon-fill' : type === 'warning' ? 'bi-exclamation-triangle-fill' : type === 'success' ? 'bi-check-circle-fill' : 'bi-info-circle-fill'} fs-5"></i>
            <div>
                <strong>${heading}</strong>
                ${detail}
                <div class="mt-2" style="font-size:.76rem;">
                    Route check radius: 150 m. Reported status only. RailSafe is not connected to live Indian Railways data.
                </div>
            </div>
        </div>`);
}

function updateRouteActions() {
    const route = selectedRoute();
    const saveButton = document.getElementById('save-route');
    const watchButton = document.getElementById('watch-route');

    saveButton.disabled = !route;
    watchButton.disabled = !route || !route.analysis.matches.length;
    updateWatchButton(route);
}

function maybeNotifyRouteRisk(route) {
    if (!route || !route.analysis.closed.length) return;
    if (!('Notification' in window) || Notification.permission !== 'granted') return;

    const key = `${mapState.start.lat},${mapState.start.lng}-${mapState.dest.lat},${mapState.dest.lng}-${route.index}`;
    if (mapState.notifiedRouteKeys.has(key)) return;
    mapState.notifiedRouteKeys.add(key);

    const names = route.analysis.closed.map(match => match.gate.name).join(', ');
    showBrowserAlert(
        'RailSafe route alert',
        `Reported CLOSED near this route: ${names}. This is reported data, not a live railway signal. Never bypass a barrier.`,
        key
    );
}

/* ------------------------------------------------------------------ */
/* Saved routes                                                        */
/* ------------------------------------------------------------------ */

async function loadSavedRoutes() {
    const container = document.getElementById('saved-routes-mini');
    try {
        const routes = await RailSafeAPI.apiFetch('/api/routes');
        if (!routes.length) {
            RailSafeUI.emptyState(container, 'bi-bookmark', 'No saved routes yet',
                'Calculate a route and save it for quick access.');
            return;
        }

        container.innerHTML = routes.map(route => `
            <div class="route-saved-item align-items-center">
                <div class="notification-icon"><i class="bi bi-signpost-2"></i></div>
                <div class="flex-grow-1 min-w-0">
                    <div class="fw-bold" style="font-size:.82rem;">${RailSafeUI.escapeHtml(route.name)}</div>
                    <div class="text-muted-rs text-break-anywhere" style="font-size:.72rem;">
                        ${RailSafeUI.escapeHtml(route.startLabel)} → ${RailSafeUI.escapeHtml(route.destLabel)}
                    </div>
                    <div class="text-muted-rs" style="font-size:.68rem;">
                        ${route.gateIds.length} crossing(s) · ${RailSafeUI.timeAgo(route.createdAt)}
                    </div>
                </div>
                <button class="btn btn-sm btn-soft load-saved-route" data-id="${route.id}" type="button" title="Open route">
                    <i class="bi bi-arrow-up-right"></i>
                </button>
            </div>
        `).join('');

        container.querySelectorAll('.load-saved-route').forEach(button => {
            button.addEventListener('click', () => {
                const route = routes.find(item => item.id === Number(button.dataset.id));
                if (!route) return;
                setWaypoint('start', {
                    lat: route.startLat,
                    lng: route.startLng,
                    label: route.startLabel
                });
                setWaypoint('dest', {
                    lat: route.destLat,
                    lng: route.destLng,
                    label: route.destLabel
                });
                planRoute();
            });
        });
    } catch (error) {
        if (error.status === 401) {
            container.innerHTML = `<div class="text-muted-rs" style="font-size:.8rem;">
                <a href="login.html?next=map.html" class="fw-bold">Log in</a> to see saved routes.</div>`;
            return;
        }
        RailSafeUI.emptyState(container, 'bi-wifi-off', 'Could not load saved routes', error.message);
    }
}

function openSaveRouteModal() {
    if (!mapState.user) {
        RailSafeUI.toast('Log in to save this route.', 'warning');
        setTimeout(() => {
            RailSafeAPI.redirectToLogin('map.html' + window.location.search);
        }, 700);
        return;
    }

    const route = selectedRoute();
    if (!route) {
        RailSafeUI.toast('Calculate a route first.', 'warning');
        return;
    }

    document.getElementById('save-route-summary').innerHTML = `
        <strong>${RailSafeUI.escapeHtml(mapState.start.label)}</strong>
        <i class="bi bi-arrow-right mx-1"></i>
        <strong>${RailSafeUI.escapeHtml(mapState.dest.label)}</strong>
        <div class="mt-1">${formatDistance(route.distance)} · ${formatDuration(route.duration)} ·
            ${route.analysis.matches.length} crossing(s) within 150 m.</div>
        ${route.analysis.closed.length
            ? `<div class="mt-1 fw-bold">${route.analysis.closed.length} reported CLOSED crossing(s) near this route.</div>`
            : ''}`;

    const suggested = `${mapState.start.label} → ${mapState.dest.label}`;
    document.getElementById('route-name').value = suggested.slice(0, 100);
    bootstrap.Modal.getOrCreateInstance(document.getElementById('saveRouteModal')).show();
}

async function saveRoute(event) {
    event.preventDefault();
    if (!mapState.user) {
        RailSafeAPI.redirectToLogin('map.html' + window.location.search);
        return;
    }

    const route = selectedRoute();
    if (!route) return;

    const name = document.getElementById('route-name').value.trim();
    if (!name) {
        RailSafeUI.toast('Give the route a name.', 'warning');
        return;
    }

    const button = document.getElementById('save-route-confirm');
    const original = button.innerHTML;
    button.disabled = true;
    button.innerHTML = '<span class="spinner-border spinner-border-sm me-1"></span>Saving…';

    try {
        await RailSafeAPI.apiFetch('/api/routes', {
            method: 'POST',
            body: JSON.stringify({
                name,
                startLabel: mapState.start.label,
                startLat: mapState.start.lat,
                startLng: mapState.start.lng,
                destLabel: mapState.dest.label,
                destLat: mapState.dest.lat,
                destLng: mapState.dest.lng,
                gateIds: route.analysis.matches.map(match => match.gate.id)
            })
        });
        bootstrap.Modal.getOrCreateInstance(document.getElementById('saveRouteModal')).hide();
        RailSafeUI.toast('Route saved. Open it later from the dashboard or this page.', 'success');
        await loadSavedRoutes();
    } catch (error) {
        if (error.status === 401) {
            RailSafeAPI.redirectToLogin('map.html' + window.location.search);
            return;
        }
        RailSafeUI.toast(error.message, 'danger');
    } finally {
        button.disabled = false;
        button.innerHTML = original;
    }
}

/* ------------------------------------------------------------------ */
/* Optional route watching (browser + in-app alerts)                   */
/* ------------------------------------------------------------------ */

function readWatch() {
    try {
        const raw = localStorage.getItem(ROUTE_WATCH_KEY);
        if (!raw) return null;
        const watch = JSON.parse(raw);
        if (!watch || !Array.isArray(watch.gateIds) || !watch.gateIds.length) return null;
        if (!watch.statuses || typeof watch.statuses !== 'object') watch.statuses = {};
        return watch;
    } catch {
        return null;
    }
}

function writeWatch(watch) {
    try {
        if (watch) {
            localStorage.setItem(ROUTE_WATCH_KEY, JSON.stringify(watch));
        } else {
            localStorage.removeItem(ROUTE_WATCH_KEY);
        }
    } catch (error) {
        console.warn('Could not store watched route:', error);
    }
}

function routeGateIds(route) {
    return route.analysis.matches.map(match => match.gate.id).sort((left, right) => left - right);
}

function isSelectedRouteWatched(route) {
    const watch = readWatch();
    if (!watch || !route) return false;
    return JSON.stringify(routeGateIds(route)) ===
        JSON.stringify(watch.gateIds.slice().sort((left, right) => left - right));
}

function updateWatchButton(route) {
    const button = document.getElementById('watch-route');
    if (!route) {
        button.innerHTML = '<i class="bi bi-bell me-1"></i>Watch this route';
        return;
    }
    if (isSelectedRouteWatched(route)) {
        button.innerHTML = '<i class="bi bi-bell-slash me-1"></i>Stop watching this route';
    } else {
        button.innerHTML = '<i class="bi bi-bell me-1"></i>Watch this route';
    }
}

async function toggleRouteWatch() {
    const route = selectedRoute();
    if (!route) return;

    if (isSelectedRouteWatched(route)) {
        writeWatch(null);
        stopWatchPolling();
        updateRouteActions();
        RailSafeUI.toast('Stopped watching this route.', 'info');
        return;
    }

    if (!route.analysis.matches.length) {
        RailSafeUI.toast('No crossings are close enough to this route to watch.', 'warning');
        return;
    }

    if ('Notification' in window && Notification.permission === 'default') {
        try {
            await Notification.requestPermission();
        } catch {
            /* Permission prompts can fail in some embedded browsers. In-app alerts still work. */
        }
    }

    const statuses = {};
    route.analysis.matches.forEach(match => {
        statuses[match.gate.id] = match.gate.displayStatus;
    });

    const watch = {
        name: `${mapState.start.label} → ${mapState.dest.label}`,
        gateIds: routeGateIds(route),
        statuses,
        start: { ...mapState.start },
        dest: { ...mapState.dest },
        createdAt: new Date().toISOString()
    };
    writeWatch(watch);
    startWatchPolling();
    updateRouteActions();

    if ('Notification' in window && Notification.permission === 'granted') {
        showBrowserAlert('RailSafe route watch enabled',
            'RailSafe will check the crossings near this route while this page stays open.', 'railsafe-watch-enabled');
    }
    RailSafeUI.toast('Watching this route. Keep this page open to receive alerts.', 'success');
}

function restoreRouteWatch() {
    const watch = readWatch();
    if (!watch) return;
    startWatchPolling();
    updateRouteActions();
}

function startWatchPolling() {
    stopWatchPolling();
    mapState.watchTimer = setInterval(checkWatchedRoute, 20000);
}

function stopWatchPolling() {
    if (mapState.watchTimer) {
        clearInterval(mapState.watchTimer);
        mapState.watchTimer = null;
    }
}

async function checkWatchedRoute() {
    const watch = readWatch();
    if (!watch) {
        stopWatchPolling();
        return;
    }

    try {
        const gates = await RailSafeAPI.apiFetch('/api/gates');
        const byId = new Map(gates.map(gate => [gate.id, gate]));
        const newlyClosed = [];
        let changed = false;

        watch.gateIds.forEach(id => {
            const gate = byId.get(Number(id));
            if (!gate) return;
            const previous = watch.statuses[id];
            if (gate.displayStatus === 'CLOSED' && previous && previous !== 'CLOSED') {
                newlyClosed.push(gate);
            }
            if (previous !== gate.displayStatus) {
                changed = true;
            }
            watch.statuses[id] = gate.displayStatus;
        });

        if (changed) {
            writeWatch(watch);
        }

        if (newlyClosed.length) {
            const names = newlyClosed.map(gate => gate.name).join(', ');
            RailSafeUI.toast(`Route alert: ${names} is now reported CLOSED.`, 'danger');
            showBrowserAlert('RailSafe route alert: crossing reported CLOSED',
                `${names}. This is reported data, not a live railway signal. Never bypass a barrier.`,
                `railsafe-route-closed-${newlyClosed.map(gate => gate.id).join('-')}`);
        }

        if (changed) {
            mapState.gates = gates;
            renderGateMarkers();
            applyGateFilter();
            if (mapState.routes.length) reanalyseRoutes();
        }
    } catch (error) {
        if (error.status === 401) {
            stopWatchPolling();
            return;
        }
        console.warn('Watched-route check failed:', error);
    }
}

function showBrowserAlert(title, body, tag) {
    if (!('Notification' in window) || Notification.permission !== 'granted') return;
    try {
        new Notification(title, {
            body,
            tag,
            icon: 'favicon.ico'
        });
    } catch (error) {
        console.warn('Could not show browser notification:', error);
    }
}

/* ------------------------------------------------------------------ */
/* URL state and helpers                                               */
/* ------------------------------------------------------------------ */

function applyUrlState() {
    const params = new URLSearchParams(window.location.search);
    const focusId = Number(params.get('focusGate'));
    if (focusId) {
        focusGate(focusId);
    }

    const startLat = Number(params.get('startLat'));
    const startLng = Number(params.get('startLng'));
    const destLat = Number(params.get('destLat'));
    const destLng = Number(params.get('destLng'));

    const hasStart = params.get('startLat') !== null && params.get('startLng') !== null &&
        Number.isFinite(startLat) && Number.isFinite(startLng);
    const hasDest = params.get('destLat') !== null && params.get('destLng') !== null &&
        Number.isFinite(destLat) && Number.isFinite(destLng);

    if (hasStart) {
        setWaypoint('start', {
            lat: startLat,
            lng: startLng,
            label: params.get('startLabel') || `Start (${startLat.toFixed(5)}, ${startLng.toFixed(5)})`
        });
    }
    if (hasDest) {
        setWaypoint('dest', {
            lat: destLat,
            lng: destLng,
            label: params.get('destLabel') || `Destination (${destLat.toFixed(5)}, ${destLng.toFixed(5)})`
        });
        if (mapState.map) {
            mapState.map.setView([destLat, destLng], 15);
        }
    }

    if (hasStart && hasDest) {
        planRoute();
    } else if (hasDest) {
        RailSafeUI.toast('Destination set. Choose a start point to calculate a route.', 'info');
    } else if (hasStart) {
        RailSafeUI.toast('Start set. Choose a destination to calculate a route.', 'info');
    }
}

function clearRoute() {
    mapState.start = null;
    mapState.dest = null;
    document.getElementById('start-input').value = '';
    document.getElementById('dest-input').value = '';
    hideGeocodeResults('start');
    hideGeocodeResults('dest');
    mapState.pickMode = null;
    cancelPickMode();
    updateWaypointLabel('start');
    updateWaypointLabel('dest');
    renderWaypointMarkers();
    clearRouteResult();
    if (mapState.map) {
        mapState.map.setView([RAILSAFE_MAP_CENTER.lat, RAILSAFE_MAP_CENTER.lng], 13);
    }
}

function clearRouteResult() {
    mapState.routes = [];
    mapState.selectedRouteIndex = -1;
    if (mapState.routeLayer) {
        mapState.routeLayer.clearLayers();
    }
    renderEmptyRouteOptions();
    RailSafeUI.clearAlert(document.getElementById('route-alert'));
    updateRouteActions();
}

function formatDistance(meters) {
    if (!Number.isFinite(meters)) return 'unknown distance';
    return meters >= 1000
        ? `${(meters / 1000).toFixed(1)} km`
        : `${Math.round(meters)} m`;
}

function formatDuration(seconds) {
    const minutes = Math.max(1, Math.round(seconds / 60));
    if (minutes < 60) return `${minutes} min`;
    const hours = Math.floor(minutes / 60);
    const rest = minutes % 60;
    return rest ? `${hours} h ${rest} min` : `${hours} h`;
}

/**
 * Shortest distance from a point to a polyline, using a local equirectangular
 * projection. This is accurate enough for the 150 m route check.
 */
function distancePointToPolylineMeters(lat, lng, coordinates) {
    if (!coordinates.length) return Infinity;
    if (coordinates.length === 1) {
        return haversineMeters(lat, lng, coordinates[0].lat, coordinates[0].lng);
    }

    const metresPerDegreeLat = 111320;
    const metresPerDegreeLng = 111320 * Math.cos(lat * Math.PI / 180);
    let minimum = Infinity;

    for (let index = 0; index < coordinates.length - 1; index += 1) {
        const a = coordinates[index];
        const b = coordinates[index + 1];
        const ax = (a.lng - lng) * metresPerDegreeLng;
        const ay = (a.lat - lat) * metresPerDegreeLat;
        const bx = (b.lng - lng) * metresPerDegreeLng;
        const by = (b.lat - lat) * metresPerDegreeLat;
        const dx = bx - ax;
        const dy = by - ay;
        const lengthSquared = dx * dx + dy * dy;
        let t = 0;
        if (lengthSquared > 0) {
            t = Math.max(0, Math.min(1, -(ax * dx + ay * dy) / lengthSquared));
        }
        const closestX = ax + dx * t;
        const closestY = ay + dy * t;
        minimum = Math.min(minimum, Math.hypot(closestX, closestY));
    }

    return minimum;
}

function haversineMeters(lat1, lng1, lat2, lng2) {
    const radius = 6371000;
    const toRad = value => value * Math.PI / 180;
    const dLat = toRad(lat2 - lat1);
    const dLng = toRad(lng2 - lng1);
    const a = Math.sin(dLat / 2) ** 2 +
        Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) * Math.sin(dLng / 2) ** 2;
    return 2 * radius * Math.asin(Math.min(1, Math.sqrt(a)));
}
