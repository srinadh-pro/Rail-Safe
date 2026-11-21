import React, { useEffect, useState } from 'react';
import { apiFetch } from '../api.js';
import { formatClock } from '../format.js';
import StatusBadge from './StatusBadge.jsx';

/**
 * Clearly labelled demo simulation. It is deliberately separate from the
 * operational feed: nothing here sets LIVE or claims a genuine train position.
 */
export default function SimulationPanel() {
  const [snapshot, setSnapshot] = useState(null);
  const [error, setError] = useState(null);
  const [now, setNow] = useState(() => Date.now());

  useEffect(() => {
    let cancelled = false;
    const load = () => {
      apiFetch('/api/simulation/snapshot')
        .then((result) => {
          if (!cancelled) {
            setSnapshot(result);
            setError(null);
          }
        })
        .catch((err) => {
          if (!cancelled && err.status !== 401) setError(err.message);
        });
    };
    load();
    const poll = setInterval(load, 10000);
    const tick = setInterval(() => setNow(Date.now()), 1000);
    return () => {
      cancelled = true;
      clearInterval(poll);
      clearInterval(tick);
    };
  }, []);

  const countdown = (target) => {
    if (!target) return 'Not Available';
    const seconds = Math.max(0, Math.floor((new Date(target).getTime() - now) / 1000));
    if (!Number.isFinite(seconds)) return 'Not Available';
    if (seconds === 0) return 'now';
    const hours = Math.floor(seconds / 3600);
    const minutes = Math.floor((seconds % 3600) / 60);
    const rest = seconds % 60;
    return `${hours ? `${hours}h ` : ''}${minutes}m ${rest}s`;
  };

  if (error && !snapshot) {
    return <section className="card simulation-panel"><p className="muted">Demo simulation unavailable: {error}</p></section>;
  }
  if (!snapshot) {
    return <section className="card simulation-panel"><p className="muted">Loading demo simulation…</p></section>;
  }

  const counts = snapshot.counts || {};
  const cards = [
    ['Demo gates', counts.totalGates],
    ['Open', counts.openGates],
    ['Closed', counts.closedGates],
    ['Closing', counts.closingGates],
    ['Approaching trains', counts.approachingTrains],
    ['Active crossings', counts.activeCrossings]
  ];

  return (
    <section className="card simulation-panel" aria-labelledby="simulation-heading">
      <div className="section-head">
        <div>
          <span className="eyebrow">Simulation · fictional data</span>
          <h2 id="simulation-heading">Demo simulation board</h2>
          <p className="muted small">{snapshot.warning}</p>
        </div>
        <span className="tag tag-estimate">NOT LIVE</span>
      </div>

      <div className="simulation-grid">
        {cards.map(([label, value]) => (
          <div className="simulation-card" key={label}>
            <span className="summary-label">{label}</span>
            <strong className="summary-value">{value}</strong>
          </div>
        ))}
      </div>

      <div className="simulation-tables">
        <div>
          <h3 className="sim-board-title">Gate board</h3>
          <p className="sim-board-hint">Fictional crossing states only.</p>
          <div className="table-wrap">
            <table className="data-table sim-table sim-gate-table responsive-table">
              <caption className="sr-only">Fictional demo gate statuses</caption>
              <thead><tr>
                <th scope="col">Code</th><th scope="col">Crossing</th><th scope="col">Status</th>
              </tr></thead>
              <tbody>
                {(snapshot.gates || []).slice(0, 16).map((gate) => (
                  <tr key={gate.gateCode}>
                    <td data-label="Code"><span className="sim-code">{gate.gateCode}</span></td>
                    <td data-label="Crossing"><span className="sim-crossing">{gate.name}</span></td>
                    <td data-label="Status"><StatusBadge status={gate.status} /></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>

        <div>
          <h3 className="sim-board-title">Train board</h3>
          <p className="sim-board-hint">Estimated times, not sensor readings.</p>
          <div className="table-wrap">
            <table className="data-table sim-table sim-train-table responsive-table">
              <caption className="sr-only">Fictional demo train movements and expected times</caption>
              <thead><tr>
                <th scope="col">Train</th><th scope="col">Demo crossing</th>
                <th scope="col">Expected arrival</th><th scope="col">Expected leaving</th>
              </tr></thead>
              <tbody>
                {(snapshot.trains || []).slice(0, 12).map((train) => (
                  <tr key={`${train.trainNumber}-${train.gateCode}`}>
                    <td data-label="Train">
                      <strong>{train.trainName}</strong>
                      <small>#{train.trainNumber}</small>
                    </td>
                    <td data-label="Demo crossing">
                      <span className="sim-crossing">{train.gateName}</span>
                      <small><span className="sim-code sim-code-subtle">{train.gateCode}</span></small>
                    </td>
                    <td data-label="Arrival">
                      <span className="sim-countdown" title={formatClock(train.expectedArrivalAt)}>
                        {countdown(train.expectedArrivalAt)}
                      </span>
                    </td>
                    <td data-label="Leaving">
                      <span className="sim-countdown" title={formatClock(train.expectedDepartureAt)}>
                        {countdown(train.expectedDepartureAt)}
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      </div>

      <p className="sim-footer-note">
        <span className="tag tag-estimate">NOT LIVE</span>
        Countdowns tick every second and the snapshot refreshes every 10 s. Mode: {snapshot.dataMode}.
        This data is fictional and must never be used for a crossing decision.
      </p>
    </section>
  );
}
