import React from 'react';
import { formatDateTime } from '../format.js';
import { notAvailable } from '../status.js';
import { useTrainReference } from '../hooks.js';

const MAX_TRAINS = 30;

function formatSchedule(value) {
  const safe = notAvailable(value);
  if (safe === 'Not Available') return safe;
  const text = String(safe).trim();
  // The catalogue may store a plain HH:mm schedule rather than a full timestamp.
  if (/^\d{1,2}:\d{2}(:\d{2})?$/.test(text)) return text;
  return formatDateTime(safe);
}

function dashOr(value) {
  const safe = notAvailable(value);
  return safe === 'Not Available' ? <span className="dash">Not Available</span> : String(safe);
}

export default function TrainReferencePanel() {
  const { trains, loading, error, refresh } = useTrainReference();
  const visible = trains.slice(0, MAX_TRAINS);

  return (
    <section className="card train-reference" aria-labelledby="train-reference-heading">
      <div className="card-head">
        <div>
          <span className="eyebrow">Verified reference data</span>
          <h2 id="train-reference-heading">Train reference catalogue</h2>
          <p className="muted">
            A verified timetable reference — source, destination, schedule and reference source are supplied
            by the catalogue. This is <strong>not</strong> a live tracking feed. Live positions and live ETAs
            show &ldquo;Live data unavailable&rdquo; unless a genuine authorised feed is present.
          </p>
        </div>
        <div className="train-reference-actions">
          <span className="tag tag-green">VERIFIED REFERENCE</span>
          <button type="button" className="btn btn-ghost btn-small" onClick={refresh} disabled={loading}>
            {loading ? 'Refreshing…' : 'Refresh'}
          </button>
        </div>
      </div>

      {loading && trains.length === 0 ? (
        <div className="detail-loading" role="status">
          <span className="spinner spinner-small" aria-hidden="true" />
          Loading train reference catalogue…
        </div>
      ) : null}

      {error ? <div className="notice notice-grey" role="status">{error}</div> : null}

      {!loading && visible.length === 0 && !error ? (
        <div className="empty-state" role="status">
          <span className="empty-title">No verified trains available</span>
          <p>The reference catalogue is empty or unavailable.</p>
        </div>
      ) : null}

      {visible.length > 0 ? (
        <>
          <p className="inline-note">
            Showing {visible.length} verified train{visible.length === 1 ? '' : 's'}
            {trains.length > MAX_TRAINS ? ` of ${trains.length}` : ''}. Reference classification:
            {' '}<strong>{dashOr(visible[0]?.dataClassification)}</strong>.
          </p>
          <div className="table-wrap">
            <table className="data-table trains-table reference-table responsive-table">
              <caption className="sr-only">Verified reference train catalogue with schedules and reference sources</caption>
              <thead>
                <tr>
                  <th scope="col">Train</th>
                  <th scope="col">Source → destination</th>
                  <th scope="col">Current / next station</th>
                  <th scope="col">Scheduled departure</th>
                  <th scope="col">Scheduled arrival</th>
                  <th scope="col">Estimated arrival</th>
                  <th scope="col">Running status</th>
                  <th scope="col">Speed</th>
                  <th scope="col">Reference source</th>
                  <th scope="col">Live position / ETA</th>
                  <th scope="col">Last verified</th>
                  <th scope="col">Note</th>
                </tr>
              </thead>
              <tbody>
                {visible.map((train, index) => (
                  <tr key={`${train.trainNumber || 'train'}-${index}`}>
                    <td data-label="Train">
                      <strong>{dashOr(train.trainName)}</strong>
                      <small className="mono">#{dashOr(train.trainNumber)}</small>
                    </td>
                    <td data-label="Source → destination">
                      {dashOr(train.sourceStation)} → {dashOr(train.destinationStation)}
                    </td>
                    <td data-label="Current / next station">
                      {dashOr(train.currentStation)} / {dashOr(train.nextStation)}
                    </td>
                    <td data-label="Scheduled departure">{formatSchedule(train.scheduledDeparture)}</td>
                    <td data-label="Scheduled arrival">{formatSchedule(train.scheduledArrival)}</td>
                    <td data-label="Estimated arrival">{formatSchedule(train.estimatedArrival)}</td>
                    <td data-label="Running status">{dashOr(train.runningStatus)}</td>
                    <td data-label="Speed">
                      {train.speedKmph === null || train.speedKmph === undefined
                        ? <span className="dash">Not Available</span>
                        : `${Math.round(Number(train.speedKmph))} km/h`}
                    </td>
                    <td data-label="Reference source">
                      {train.referenceUrl ? (
                        <a href={train.referenceUrl} target="_blank" rel="noreferrer">
                          {dashOr(train.referenceSource)}
                        </a>
                      ) : (
                        dashOr(train.referenceSource)
                      )}
                    </td>
                    <td data-label="Live position / ETA">
                      <span className="live-unavailable">Live data unavailable</span>
                    </td>
                    <td data-label="Last verified">{formatDateTime(train.lastVerifiedAt)}</td>
                    <td data-label="Note">
                      <small>{dashOr(train.note)}</small>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <p className="table-note">
            Verified reference data only. Simulated or scheduled positions are never presented as live.
            Verify locally and obey physical barriers and authorised staff.
          </p>
        </>
      ) : null}
    </section>
  );
}
