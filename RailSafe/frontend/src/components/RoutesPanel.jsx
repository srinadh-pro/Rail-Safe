import React, { useMemo, useState } from 'react';
import { apiFetch } from '../api.js';
import { formatKm, formatMeters } from '../format.js';
import { ROUTE_COLORS, gateStatusLabel } from '../status.js';
import MapView from './MapView.jsx';

function parseCoordinate(value, min, max) {
  const numeric = Number(value);
  if (!Number.isFinite(numeric) || numeric < min || numeric > max) return null;
  return numeric;
}

function formatGateList(value) {
  if (value === null || value === undefined || value === '') return 'Not Available';
  if (Array.isArray(value)) {
    if (value.length === 0) return 'None';
    return value
      .map((item) => (typeof item === 'string'
        ? item
        : (item.gateName || item.name || item.gateId || 'Gate')))
      .join(', ');
  }
  if (typeof value === 'object') return value.gateName || value.name || value.gateId || 'Not Available';
  if (typeof value === 'number') return `${value} gate${value === 1 ? '' : 's'}`;
  return String(value);
}

function formatExtraKm(value) {
  if (value === null || value === undefined || Number.isNaN(Number(value))) return 'Not Available';
  return `+${Number(value).toFixed(1)} km`;
}

function formatExtraDuration(route) {
  const value = route.extraDurationMinutes ?? route.extraMinutes;
  if (value === null || value === undefined || Number.isNaN(Number(value))) return 'Not Available';
  const number = Number(value);
  return `${number > 0 ? '+' : ''}${Math.round(number)} min`;
}

function impactSummary(route) {
  return [
    `${formatKm(route.distanceKm)} in ${Math.round(route.durationMinutes)} min`,
    `${formatExtraKm(route.extraDistanceKm)} / ${formatExtraDuration(route)} versus direct`,
    `closest approach ${formatMeters(route.distanceFromGateMeters)}`,
    `closed gates: ${formatGateList(route.closedGates)}`,
    `avoided gates: ${formatGateList(route.avoidedGates)}`
  ].join(' · ');
}

export default function RoutesPanel({ gate, prediction }) {
  const [startLat, setStartLat] = useState('');
  const [startLng, setStartLng] = useState('');
  const [destLat, setDestLat] = useState('');
  const [destLng, setDestLng] = useState('');
  const [result, setResult] = useState(null);
  const [selectedRouteId, setSelectedRouteId] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [locationMessage, setLocationMessage] = useState(null);

  const gateStatus = prediction?.verifiedStatus || gate?.status || gate?.verifiedStatus || 'AWAITING_VERIFICATION';

  const start = useMemo(() => {
    const lat = parseCoordinate(startLat, -90, 90);
    const lng = parseCoordinate(startLng, -180, 180);
    return lat !== null && lng !== null ? { lat, lng } : null;
  }, [startLat, startLng]);

  const destination = useMemo(() => {
    const lat = parseCoordinate(destLat, -90, 90);
    const lng = parseCoordinate(destLng, -180, 180);
    return lat !== null && lng !== null ? { lat, lng } : null;
  }, [destLat, destLng]);

  // Only genuine, verified alternatives are shown. Simulated routes are never
  // offered as safe diversions.
  const genuineAlternatives = useMemo(
    () => (result?.alternatives || []).filter(
      (route) => route.simulated !== true && route.dataClassification !== 'SIMULATED'
    ),
    [result]
  );

  const mapRoutes = useMemo(() => {
    if (!result) return [];
    const list = [];
    if (result.original && result.original.geometry?.length) {
      list.push({
        ...result.original,
        id: 'original',
        label: result.original.avoidsGate ? 'Direct route (avoids crossing)' : 'Direct route (crosses the closed gate)'
      });
    }
    genuineAlternatives.forEach((route) => list.push(route));
    return list.map((route, index) => ({ ...route, color: ROUTE_COLORS[index % ROUTE_COLORS.length] }));
  }, [result, genuineAlternatives]);

  const useMyLocation = () => {
    if (!navigator.geolocation) {
      setLocationMessage('Geolocation is not supported by this browser.');
      return;
    }
    setLocationMessage('Requesting your location…');
    navigator.geolocation.getCurrentPosition(
      (position) => {
        setStartLat(position.coords.latitude.toFixed(6));
        setStartLng(position.coords.longitude.toFixed(6));
        setLocationMessage(`Start set to your approximate position (±${Math.round(position.coords.accuracy)} m).`);
      },
      (geoError) => {
        setLocationMessage(
          geoError.code === geoError.PERMISSION_DENIED
            ? 'Location permission was denied. Enter coordinates manually or use your browser settings.'
            : 'Could not read your location. Enter coordinates manually.'
        );
      },
      { enableHighAccuracy: true, timeout: 10000, maximumAge: 60000 }
    );
  };

  const calculate = async (event) => {
    event.preventDefault();
    setError(null);
    setLocationMessage(null);

    const values = {
      gateId: gate.id,
      startLat: parseCoordinate(startLat, -90, 90),
      startLng: parseCoordinate(startLng, -180, 180),
      destLat: parseCoordinate(destLat, -90, 90),
      destLng: parseCoordinate(destLng, -180, 180)
    };
    if (Object.values(values).some((value) => value === null)) {
      setError('Enter valid start and destination coordinates. Latitude must be −90 to 90 and longitude −180 to 180.');
      return;
    }

    setLoading(true);
    try {
      const response = await apiFetch('/api/routes/alternatives', {
        method: 'POST',
        body: JSON.stringify(values)
      });
      setResult(response);
      setSelectedRouteId(
        response.recommendedRouteId ||
        response.alternatives?.[0]?.id ||
        (response.original?.avoidsGate ? 'original' : null)
      );
    } catch (err) {
      setError(err.message);
      setResult(null);
    } finally {
      setLoading(false);
    }
  };

  const selectedRoute = genuineAlternatives.find((route) => route.id === selectedRouteId);
  const noSafeAlternate = Boolean(result)
    && (result.available === false || (genuineAlternatives.length === 0 && !result.original?.avoidsGate));

  return (
    <section className="card routes-card" aria-labelledby="routes-heading">
      <div className="card-head">
        <div>
          <span className="eyebrow">OpenStreetMap + OSRM</span>
          <h2 id="routes-heading">Alternate route finder</h2>
          <p className="muted">
            Routes are calculated by OSRM and verified by RailSafe to stay outside an avoidance
            radius around this crossing. Every distance and duration comes from the routing engine.
          </p>
        </div>
        <span className={`tag ${gateStatus === 'CLOSED' ? 'tag-red' : 'tag-grey'}`}>
          Gate {gateStatus === 'CLOSED' ? 'verified CLOSED' : gateStatusLabel(gateStatus)}
        </span>
      </div>

      {gateStatus === 'CLOSED' ? (
        <div className="notice notice-red">
          <strong>The gate is verified CLOSED.</strong> Use only genuine verified alternatives below and never
          enter a closed crossing or bypass a barrier.
        </div>
      ) : (
        <div className="notice notice-orange">
          <strong>Route assistance is advisory for this gate.</strong> This gate is {gateStatusLabel(gateStatus)}.
          You may still calculate and verify a route, but RailSafe will not claim that a diversion is required.
        </div>
      )}

      <form className="route-form" onSubmit={calculate}>
        <fieldset>
          <legend>Start location</legend>
          <label>
            Latitude
            <input inputMode="decimal" value={startLat} onChange={(event) => setStartLat(event.target.value)} placeholder="17.385000" />
          </label>
          <label>
            Longitude
            <input inputMode="decimal" value={startLng} onChange={(event) => setStartLng(event.target.value)} placeholder="78.486700" />
          </label>
          <button type="button" className="btn btn-ghost" onClick={useMyLocation}>
            Use my location
          </button>
        </fieldset>

        <fieldset>
          <legend>Destination</legend>
          <label>
            Latitude
            <input inputMode="decimal" value={destLat} onChange={(event) => setDestLat(event.target.value)} placeholder="17.345600" />
          </label>
          <label>
            Longitude
            <input inputMode="decimal" value={destLng} onChange={(event) => setDestLng(event.target.value)} placeholder="78.552500" />
          </label>
          <button type="submit" className="btn btn-primary" disabled={loading}>
            {loading ? 'Calculating…' : 'Calculate verified alternatives'}
          </button>
        </fieldset>
      </form>

      {locationMessage ? <p className="form-hint">{locationMessage}</p> : null}
      {error ? <div className="notice notice-red" role="alert">{error}</div> : null}

      {result ? (
        <div className="route-results">
          <div className="route-map-wrap">
            <MapView
              gate={gate}
              start={start}
              destination={destination}
              routes={mapRoutes}
              selectedRouteId={selectedRouteId}
              gateStatus={gateStatus}
            />
            <div className="map-legend">
              <span><i className="legend-dot legend-gate" /> Selected crossing</span>
              {mapRoutes.map((route) => (
                <span key={route.id}>
                  <i className="legend-line" style={{ background: route.color }} /> {route.label}
                </span>
              ))}
            </div>
          </div>

          {noSafeAlternate ? (
            <div className="notice notice-red route-no-safe" role="status">
              <strong>No safe alternate route exists.</strong>{' '}
              {result.message || 'No verified route stays clear of the closed crossing. Do not cross or bypass the barrier; wait for an official diversion.'}
            </div>
          ) : null}

          {result.original ? (
            <div className={`original-route ${result.original.avoidsGate ? 'original-safe' : 'original-blocked'}`}>
              <div>
                <span className="eyebrow">Impact · direct OSRM route</span>
                <strong>{formatKm(result.original.distanceKm)} · {Math.round(result.original.durationMinutes)} min</strong>
              </div>
              <p>
                {result.original.avoidsGate
                  ? `This direct route does not pass within the ${Math.round(result.avoidRadiusMeters)} m avoidance radius of the crossing, so no detour is needed.`
                  : `The direct route passes within ${formatMeters(result.original.distanceFromGateMeters)} of this crossing, so it is not offered while the gate is closed.`}
              </p>
            </div>
          ) : null}

          {genuineAlternatives.length ? (
            <div className="route-list" role="list">
              {genuineAlternatives.map((route) => (
                <article
                  key={route.id}
                  role="listitem"
                  className={`route-option ${route.id === selectedRouteId ? 'route-option-selected' : ''}`}
                >
                  <header>
                    <div>
                      <span className="eyebrow">{route.recommended ? 'Fastest verified option' : 'Verified alternative'}</span>
                      <h3>{route.label}</h3>
                    </div>
                    {route.recommended ? <span className="tag tag-blue">FASTEST</span> : null}
                  </header>
                  <div className="route-metrics">
                    <div><span>Distance</span><strong>{formatKm(route.distanceKm)}</strong></div>
                    <div><span>Duration</span><strong>{Math.round(route.durationMinutes)} min</strong></div>
                    <div><span>Extra distance</span><strong>{formatExtraKm(route.extraDistanceKm)}</strong></div>
                    <div><span>Extra duration</span><strong>{formatExtraDuration(route)}</strong></div>
                    <div><span>Closest approach</span><strong>{formatMeters(route.distanceFromGateMeters)}</strong></div>
                    <div><span>Closed gates</span><strong>{formatGateList(route.closedGates)}</strong></div>
                    <div><span>Avoided gates</span><strong>{formatGateList(route.avoidedGates)}</strong></div>
                  </div>
                  <div className="route-impact">
                    <span className="eyebrow">Impact</span>
                    <p>{impactSummary(route)}</p>
                  </div>
                  <div className="route-actions">
                    <button
                      type="button"
                      className={`btn ${route.id === selectedRouteId ? 'btn-primary' : 'btn-ghost'}`}
                      onClick={() => setSelectedRouteId(route.id)}
                    >
                      {route.id === selectedRouteId ? 'Selected' : 'Select this route'}
                    </button>
                    {route.navigationUrl ? (
                      <a className="btn btn-ghost" href={route.navigationUrl} target="_blank" rel="noreferrer">
                        Navigate (Google Maps)
                      </a>
                    ) : null}
                    {route.mapUrl ? (
                      <a className="btn btn-ghost" href={route.mapUrl} target="_blank" rel="noreferrer">
                        Open in OpenStreetMap
                      </a>
                    ) : null}
                  </div>
                </article>
              ))}
            </div>
          ) : (
            <div className="notice notice-grey">
              No genuine alternative remains after verification. Do not drive through the closed crossing;
              wait or follow an official diversion.
            </div>
          )}

          {selectedRoute ? (
            <p className="route-selected-note">
              Selected: <strong>{selectedRoute.label}</strong> · Impact: {impactSummary(selectedRoute)} ·
              {' '}verified closest approach {formatMeters(selectedRoute.distanceFromGateMeters)} (avoid radius{' '}
              {Math.round(result.avoidRadiusMeters)} m).
            </p>
          ) : null}
        </div>
      ) : (
        <div className="empty-state">
          <span className="empty-title">No route calculated yet</span>
          <p>
            Enter a start and destination to request OSRM alternatives. RailSafe rejects any route
            that comes within the configured distance of the selected crossing.
          </p>
        </div>
      )}
    </section>
  );
}
