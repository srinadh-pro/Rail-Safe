import React from 'react';
import { formatClock, timeAgo } from '../format.js';

export default function ProviderBanner({ provider, tracking, lastSuccess }) {
  if (!tracking && !provider) return null;

  const mode = tracking?.mode || provider?.mode;
  const knownModes = ['LIVE', 'THIRD_PARTY', 'SCHEDULE', 'UNAVAILABLE'];
  const unverified = (Boolean(mode) && !knownModes.includes(mode)) || tracking?.simulated === true;
  const unavailable = tracking?.liveDataAvailable === false
    || !tracking?.available
    || mode === 'UNAVAILABLE'
    || provider?.available === false
    || unverified;
  const stale = tracking?.stale || provider?.stale;

  if (unavailable) {
    return (
      <div className="data-strip data-strip-grey" role="status">
        <span className="data-strip-dot" aria-hidden="true" />
        <strong>Live train data unavailable</strong>
        <span className="data-strip-text">
          {tracking?.message || provider?.message || 'No authorised train data provider is configured.'}
          {' '}Verify the crossing locally and obey physical barriers. No train, ETA or countdown is invented
          in this state.
        </span>
      </div>
    );
  }

  const thirdParty = mode === 'THIRD_PARTY';
  const scheduleOnly = mode === 'SCHEDULE';
  const tone = thirdParty || stale ? 'orange' : scheduleOnly ? 'blue' : 'green';
  const title = stale
    ? 'Live data is stale'
    : thirdParty
      ? 'Third-party live data'
      : scheduleOnly
        ? 'Scheduled data only'
        : 'Authorised live data';

  return (
    <div className={`data-strip data-strip-${tone}`} role="status">
      <span className="data-strip-dot" aria-hidden="true" />
      <strong>{title}</strong>
      <span className="data-strip-text">
        {thirdParty
          ? 'Provided by RailRadar, an independent service. Not official Indian Railways, IRCTC or NTES data.'
          : scheduleOnly
            ? 'These are scheduled times, not live train positions.'
            : `Provider: ${tracking?.provider || provider?.provider || 'authorised feed'}.`}
        {stale ? ' The feed is older than the freshness window, so predictions are withheld.' : ''}
      </span>
      {tracking?.updatedAt ? (
        <span className="data-strip-time" title={`${timeAgo(tracking.updatedAt)}${lastSuccess ? ` · page updated ${lastSuccess.toLocaleTimeString()}` : ''}`}>
          Feed {formatClock(tracking.updatedAt)}
        </span>
      ) : null}
    </div>
  );
}
