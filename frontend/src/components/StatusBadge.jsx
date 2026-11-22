import React from 'react';
import { gateStatusMeta, isGateStatus, trainStatusMeta } from '../status.js';

export default function StatusBadge({ status, label, dot = true }) {
  const raw = status || 'UNKNOWN';

  // Train movement statuses and gate barrier statuses share the badge design
  // but have separate vocabularies.
  const train = trainStatusMeta(raw);
  const gate = isGateStatus(raw) || train === null ? gateStatusMeta(raw) : null;
  const tone = train ? train.tone : gate.tone;
  const text = label || (train ? train.label : gate.badgeLabel);

  return (
    <span className={`badge badge-${tone}`} data-status={raw}>
      {dot ? <span className="badge-dot" aria-hidden="true" /> : null}
      {text}
    </span>
  );
}
