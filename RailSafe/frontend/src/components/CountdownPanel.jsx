import React from 'react';
import { formatClock, formatCountdown } from '../format.js';
import { useCountdown } from '../hooks.js';

function CountdownCard({ tone, label, available, target, generatedAt, message }) {
  const seconds = useCountdown(available ? target : null, generatedAt);

  return (
    <article className={`countdown-card countdown-${tone}`}>
      <div className="countdown-head">
        <span className="countdown-label">{label}</span>
        <span className="tag tag-estimate">ESTIMATE</span>
      </div>
      <div className="countdown-value" aria-live="polite">{formatCountdown(seconds)}</div>
      <span className="countdown-target">
        {target ? `Expected at ${formatClock(target)}` : 'Expected time unavailable'}
      </span>
      <p className="countdown-note">{message}</p>
    </article>
  );
}

export default function CountdownPanel({ prediction, generatedAt, nextTrain }) {
  const closing = prediction.closing;
  const opening = prediction.opening;
  const cards = [];

  // Only a backend-provided ETA can drive this countdown. It is always labelled
  // as an estimate and never connected to physical barrier controls.
  if (nextTrain?.etaAtGate) {
    const trainLabel = [nextTrain.trainName, nextTrain.trainNumber]
      .filter(Boolean)
      .join(' · ');
    cards.push(
      <CountdownCard
        key="train-arrival"
        tone="blue"
        label={`${trainLabel || 'Train'} arrives in`}
        available
        target={nextTrain.etaAtGate}
        generatedAt={generatedAt}
        message="Estimated from the available train timing. Not a guarantee or a barrier-control signal."
      />
    );
  }

  if (closing.available) {
    cards.push(
      <CountdownCard
        key="closing"
        tone="orange"
        label="Gate closes in"
        available
        target={closing.estimatedCloseAt}
        generatedAt={generatedAt}
        message={closing.message}
      />
    );
  }

  if (opening.available) {
    cards.push(
      <CountdownCard
        key="opening"
        tone="red"
        label="Gate reopens in"
        available
        target={opening.estimatedOpenAt}
        generatedAt={generatedAt}
        message={opening.message}
      />
    );
  }

  const reason = prediction.verifiedStatus === 'CLOSED'
    ? opening.message
    : closing.message;

  return (
    <div className="detail-countdowns">
      <div className="section-head section-head-tight">
        <div>
          <span className="eyebrow">Timing</span>
          <h3>Expected gate timing</h3>
        </div>
        <span className="muted small">Estimates only, never guaranteed</span>
      </div>

      {cards.length === 0 ? (
        <div className="countdown-empty" role="status">
          <strong>No reliable timing estimate right now.</strong>
          <p>{reason}</p>
        </div>
      ) : (
        <div className={`countdown-grid ${cards.length === 1 ? 'countdown-grid-single' : ''}`}>
          {cards}
        </div>
      )}
    </div>
  );
}
