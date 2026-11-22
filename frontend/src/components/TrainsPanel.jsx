import React from 'react';
import { formatClock, formatKm, timeAgo, titleCase } from '../format.js';
import StatusBadge from './StatusBadge.jsx';

/** Only an authorised live feed may populate live position / ETA columns. */
function isGenuineLiveFeed(tracking) {
  if (!tracking) return false;
  if (tracking.liveDataAvailable === true) return true;
  return tracking.mode === 'LIVE'
    && tracking.available === true
    && tracking.stale !== true
    && tracking.simulated !== true;
}

function providerNote(tracking, genuineLive) {
  if (genuineLive) {
    return 'Positions and ETAs come from an authorised live feed. They remain estimates, not guarantees.';
  }
  if (tracking?.mode === 'THIRD_PARTY') {
    return 'Third-party data from RailRadar is not an authorised Indian Railways live feed. Live positions and ETAs are withheld.';
  }
  if (tracking?.mode === 'SCHEDULE') {
    return 'Scheduled times only. These are not live train positions, so live fields are withheld.';
  }
  return 'Live train data is unavailable. Verify the crossing locally and obey physical barriers.';
}

export default function TrainsPanel({ tracking, prediction }) {
  const trains = tracking?.trains || [];
  const nextNumber = prediction?.nextTrain?.trainNumber;
  const genuineLive = isGenuineLiveFeed(tracking);

  const liveCell = (content) => (
    genuineLive ? content : <span className="live-unavailable">Live data unavailable</span>
  );

  return (
    <div className="detail-trains">
      <div className="trains-head">
        <div>
          <span className="eyebrow">Train monitoring</span>
          <h3>Trains at this gate</h3>
        </div>
        <span className={`tag ${genuineLive ? 'tag-green' : 'tag-grey'}`}>
          {genuineLive ? 'Authorised live feed' : 'Live data unavailable'}
        </span>
      </div>
      <p className="inline-note">{providerNote(tracking, genuineLive)}</p>

      {!genuineLive ? (
        <div className="notice notice-grey" role="status">
          <strong>Live data unavailable.</strong> Live positions, speeds and ETAs are withheld unless an
          authorised live feed is present. Any train details below are reference information only.
        </div>
      ) : null}

      {!tracking?.available && !genuineLive ? (
        <div className="empty-state" role="status">
          <span className="empty-title">Live data unavailable</span>
          <p>{tracking?.message || 'No authorised data provider is configured.'}</p>
        </div>
      ) : (
        <>
          {tracking?.stale ? (
            <div className="inline-alert inline-alert-orange" role="status">
              <p>
                <strong>Stale data.</strong> The provider timestamp is outside the freshness window, so
                predictions are withheld.
              </p>
            </div>
          ) : null}

          {trains.length === 0 ? (
            <div className="empty-state" role="status">
              <span className="empty-title">No trains reported near this gate</span>
              <p>{tracking?.message}</p>
            </div>
          ) : (
            <div className="table-wrap">
              <table className="data-table trains-table responsive-table">
                <caption className="sr-only">Trains reported for the selected railway gate</caption>
                <thead>
                  <tr>
                    <th scope="col">Train number</th>
                    <th scope="col">Train name</th>
                    <th scope="col">Direction</th>
                    <th scope="col">Current location</th>
                    <th scope="col">Distance</th>
                    <th scope="col">Speed</th>
                    <th scope="col">ETA</th>
                    <th scope="col">Status</th>
                  </tr>
                </thead>
                <tbody>
                  {trains.map((train, index) => (
                    <tr key={`${train.trainNumber}-${index}`}>
                      <td data-label="Train number">
                        <span className="mono">#{train.trainNumber}</span>
                        {train.trainNumber === nextNumber ? <span className="tag tag-blue">NEXT</span> : null}
                      </td>
                      <td data-label="Train name"><strong>{train.trainName}</strong></td>
                      <td data-label="Direction">{train.directionLabel || titleCase(train.direction)}</td>
                      <td data-label="Current location">{liveCell(train.currentLocation)}</td>
                      <td data-label="Distance">
                        {liveCell(
                          train.distanceToGateKm !== null && train.distanceToGateKm !== undefined
                            ? formatKm(train.distanceToGateKm)
                            : <span className="dash">—</span>
                        )}
                      </td>
                      <td data-label="Speed">
                        {liveCell(
                          train.speedKmph !== null && train.speedKmph !== undefined
                            ? `${Math.round(train.speedKmph)} km/h`
                            : <span className="dash">—</span>
                        )}
                      </td>
                      <td data-label="ETA">
                        {liveCell(
                          train.etaAtGate ? (
                            <>
                              <strong>{formatClock(train.etaAtGate)}</strong>
                              <small>{timeAgo(train.etaAtGate)}</small>
                            </>
                          ) : (
                            <span className="dash">—</span>
                          )
                        )}
                      </td>
                      <td data-label="Status">
                        <StatusBadge status={genuineLive ? train.status : 'UNKNOWN'} />
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          <p className="table-note">
            ETAs are derived from the available feed and may change. Gate closure and reopening times are
            estimates, not sensor readings.
          </p>
        </>
      )}
    </div>
  );
}
