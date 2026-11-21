import React from 'react';
import { formatClock, formatDateTime, timeAgo } from '../format.js';
import StatusBadge from './StatusBadge.jsx';

function closingWarning(prediction) {
  const closing = prediction?.closing;
  if (!closing?.available) return null;
  const train = closing.train;
  return (
    <p>
      <strong>Train approaching.</strong>{' '}
      {train ? `${train.trainName} (#${train.trainNumber}) is heading to this gate. ` : ''}
      {closing.estimatedCloseAt
        ? `The barrier may close around ${formatClock(closing.estimatedCloseAt)}.`
        : 'The barrier may close soon.'}
      {' '}This is an estimate, not a sensor reading.
    </p>
  );
}

export default function GatePanel({ gate, prediction, status }) {
  const next = prediction?.nextTrain;
  const knownModes = ['VERIFIED', 'LIVE', 'THIRD_PARTY', 'SCHEDULE', 'UNAVAILABLE'];
  const unverifiedSource = Boolean(prediction?.dataMode) && !knownModes.includes(prediction.dataMode);
  const warning = closingWarning(prediction);

  return (
    <div className="detail-head-block">
      <div className="detail-head">
        <div className="detail-identity">
          <span className="eyebrow">Selected gate</span>
          <h2 id="gate-heading">{gate.name}</h2>
          <p className="detail-sub">
            <span className="mono">{gate.gateCode}</span>
            {gate.location ? ` · ${gate.location}` : ''}
          </p>
        </div>

        <div className="detail-status">
          <span className="eyebrow">Gate status</span>
          <StatusBadge status={status} />
          {prediction ? <small>{prediction.statusConfidence}</small> : null}
        </div>
      </div>

      {prediction ? (
        <dl className="detail-facts">
          <div>
            <dt>Next train</dt>
            <dd>{next ? `${next.trainName} (#${next.trainNumber})` : 'No train due'}</dd>
          </div>
          <div>
            <dt>ETA at gate</dt>
            <dd>{next?.etaAtGate ? formatClock(next.etaAtGate) : '—'}</dd>
          </div>
          <div>
            <dt>{status === 'CLOSED' || status === 'OPENING' ? 'Expected opening' : 'Expected closure'}</dt>
            <dd>
              {status === 'CLOSED' || status === 'OPENING'
                ? (prediction.opening?.available && prediction.opening.estimatedOpenAt
                  ? formatClock(prediction.opening.estimatedOpenAt)
                  : '—')
                : (prediction.closing?.available && prediction.closing.estimatedCloseAt
                  ? formatClock(prediction.closing.estimatedCloseAt)
                  : '—')}
              <small>Estimate</small>
            </dd>
          </div>
          <div>
            <dt>Stored status updated</dt>
            <dd>
              {formatDateTime(prediction.lastUpdatedAt)}
              <small>{timeAgo(prediction.lastUpdatedAt)}</small>
            </dd>
          </div>
        </dl>
      ) : null}

      {prediction && status === 'CLOSING' ? (
        <div className="inline-alert inline-alert-orange" role="status">
          {warning}
        </div>
      ) : null}

      {prediction && (status === 'CLOSED' || status === 'OPENING') ? (
        <div className="inline-alert inline-alert-red" role="status">
          <p>
            <strong>Gate is closed.</strong>{' '}
            {prediction.opening?.available && prediction.opening.estimatedOpenAt
              ? `Estimated reopen around ${formatClock(prediction.opening.estimatedOpenAt)}.`
              : 'No reliable reopening time is available.'}
            {' '}Do not cross or bypass the barrier. Follow physical signals and railway staff.
          </p>
        </div>
      ) : null}

      {prediction && (status === 'UNKNOWN' || status === 'AWAITING_VERIFICATION') ? (
        <div className="inline-alert inline-alert-grey" role="status">
          <p>
            <strong>Awaiting Verification.</strong> {prediction.statusMessage || 'No verified status is currently available for this gate.'}
          </p>
        </div>
      ) : null}

      {prediction && prediction.consistencyWarning ? (
        <div className="inline-alert inline-alert-orange" role="status">
          <p><strong>Data note.</strong> {prediction.consistencyWarning}</p>
        </div>
      ) : null}

      {prediction && unverifiedSource ? (
        <div className="inline-alert inline-alert-grey" role="status">
          <p>
            <strong>Source information is unverified.</strong> Verify the crossing locally and obey
            physical barriers.
          </p>
        </div>
      ) : null}
    </div>
  );
}
