import React, { useEffect, useState } from 'react';
import { formatDateTime, formatDuration, formatKm } from '../format.js';
import { notAvailable } from '../status.js';
import { useGateDetail } from '../hooks.js';
import StatusBadge from './StatusBadge.jsx';

/** Every field promised by GET /api/gates/{id}/detail, with a human label. */
const FIELDS = [
  ['id', 'Record ID'],
  ['gateId', 'Gate code'],
  ['gateName', 'Gate name'],
  ['railwayStation', 'Railway station'],
  ['district', 'District'],
  ['state', 'State'],
  ['railwayZone', 'Railway zone'],
  ['division', 'Division'],
  ['latitude', 'Latitude'],
  ['longitude', 'Longitude'],
  ['gateType', 'Gate type'],
  ['status', 'Status'],
  ['verificationStatus', 'Verification status'],
  ['dataSource', 'Data source'],
  ['dataClassification', 'Data classification'],
  ['lastUpdatedAt', 'Last updated'],
  ['statusSince', 'Status since'],
  ['currentDurationSeconds', 'Current status duration'],
  ['expectedClosingAt', 'Expected closing'],
  ['expectedOpeningAt', 'Expected opening'],
  ['actualClosingAt', 'Actual closing'],
  ['actualOpeningAt', 'Actual opening'],
  ['expectedClosingSource', 'Expected closing source'],
  ['expectedOpeningSource', 'Expected opening source'],
  ['nextTrainNumber', 'Next train number'],
  ['nextTrainName', 'Next train name'],
  ['nextTrainDirection', 'Next train direction'],
  ['nextTrainEta', 'Next train ETA'],
  ['nextTrainDistanceKm', 'Next train distance'],
  ['nextTrainStatus', 'Next train status'],
  ['responsibleStaff', 'Responsible staff'],
  ['associatedRoutes', 'Associated routes'],
  ['note', 'Note']
];

const TIME_FIELDS = new Set([
  'lastUpdatedAt', 'statusSince', 'expectedClosingAt', 'expectedOpeningAt',
  'actualClosingAt', 'actualOpeningAt', 'nextTrainEta'
]);
const AWAITING_FIELDS = new Set(['status', 'verificationStatus']);

/**
 * The duration ticks in the browser from statusSince (1s interval). We do not
 * poll the server for it. If statusSince is missing, the server-provided
 * currentDurationSeconds is used as the baseline and incremented locally.
 */
function useStatusDuration(detail) {
  const statusSince = detail?.statusSince;
  const base = Number(detail?.currentDurationSeconds);
  const baseSeconds = Number.isFinite(base) ? base : 0;
  const [elapsed, setElapsed] = useState(0);

  useEffect(() => {
    const since = statusSince ? new Date(statusSince).getTime() : NaN;
    const hasSince = Number.isFinite(since);
    const tick = () => {
      if (hasSince) {
        setElapsed(Math.max(0, Math.floor((Date.now() - since) / 1000)));
      } else {
        setElapsed((value) => value + 1);
      }
    };
    tick();
    const timer = window.setInterval(tick, 1000);
    return () => window.clearInterval(timer);
  }, [statusSince]);

  const hasSince = Boolean(statusSince) && Number.isFinite(new Date(statusSince).getTime());
  return hasSince ? elapsed : baseSeconds + elapsed;
}

function renderValue(field, value, duration) {
  if (field === 'currentDurationSeconds') {
    return (
      <span className="live-duration" aria-live="off">
        {formatDuration(duration)} <small>ticking locally from status since</small>
      </span>
    );
  }

  const fallback = AWAITING_FIELDS.has(field) ? 'Awaiting Verification' : 'Not Available';
  const safe = notAvailable(value, fallback);
  if (safe === fallback) return <span className="dash">{fallback}</span>;

  if (field === 'status' || field === 'nextTrainStatus') {
    return <StatusBadge status={value} />;
  }
  if (field === 'verificationStatus') {
    const text = String(value).trim();
    if (/^(UNKNOWN|UNVERIFIED|AWAITING[_\s]VERIFICATION)$/i.test(text)) return 'Awaiting Verification';
    return text;
  }
  if (TIME_FIELDS.has(field)) return formatDateTime(value);
  if (field === 'nextTrainDistanceKm') return formatKm(value);
  if (field === 'latitude' || field === 'longitude') {
    const numeric = Number(value);
    return Number.isFinite(numeric) ? numeric.toFixed(6) : String(value);
  }
  if (Array.isArray(value)) {
    return value
      .map((item) => (typeof item === 'string'
        ? item
        : (item.gateName || item.name || item.gateId || item.trainNumber || JSON.stringify(item))))
      .join(', ') || 'Not Available';
  }
  if (typeof value === 'object') {
    return value.gateName || value.name || value.label || JSON.stringify(value);
  }
  return String(value);
}

export default function GateDetailPanel({ gateId }) {
  const { detail, loading, error } = useGateDetail(gateId);
  const duration = useStatusDuration(detail);

  if (!gateId) return null;

  const heading = detail?.gateName || detail?.gateId || 'Crossing detail';

  return (
    <section className="card gate-detail-panel" aria-labelledby="gate-detail-heading">
      <div className="section-head section-head-tight">
        <div>
          <span className="eyebrow">Gate detail</span>
          <h2 id="gate-detail-heading">{heading}</h2>
          <p className="muted small">Full record from the authoritative gate detail service.</p>
        </div>
        {detail ? <StatusBadge status={detail.status} /> : null}
      </div>

      {loading && !detail ? (
        <div className="detail-loading" role="status">
          <span className="spinner spinner-small" aria-hidden="true" />
          Loading gate detail…
        </div>
      ) : null}

      {error ? (
        <div className="notice notice-grey" role="status">
          Gate detail is unavailable right now. {error}
        </div>
      ) : null}

      {detail ? (
        <dl className="detail-field-grid">
          {FIELDS.map(([field, label]) => (
            <div key={field} className="detail-field">
              <dt>{label}</dt>
              <dd>{renderValue(field, detail[field], duration)}</dd>
            </div>
          ))}
        </dl>
      ) : null}
    </section>
  );
}
