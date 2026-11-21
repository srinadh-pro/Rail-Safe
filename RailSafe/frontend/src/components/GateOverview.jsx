import React, { useMemo, useState } from 'react';
import { formatClock } from '../format.js';
import { normalizeGateStatus } from '../status.js';
import StatusBadge from './StatusBadge.jsx';

/**
 * Expected operational state for a gate row.
 *
 * OPEN/CLOSED only when the backend has verified the stored status. CLOSING and
 * OPENING are derived from the server's countdown targets, never invented here.
 * Anything unverifiable is shown as AWAITING_VERIFICATION (never raw UNKNOWN).
 */
export function gateDisplayStatus(gate) {
  const base = normalizeGateStatus(gate.verifiedStatus || gate.status);
  if (base === 'CLOSED') return gate.expectedOpenAt ? 'OPENING' : 'CLOSED';
  if (base === 'OPEN') return gate.expectedCloseAt ? 'CLOSING' : 'OPEN';
  return base;
}

const FILTERS = [
  { id: 'ALL', label: 'All' },
  { id: 'OPEN', label: 'Open' },
  { id: 'CLOSED', label: 'Closed' },
  { id: 'CLOSING', label: 'Closing' },
  { id: 'OPENING', label: 'Opening' },
  { id: 'MAINTENANCE', label: 'Maintenance' },
  { id: 'AWAITING_VERIFICATION', label: 'Awaiting Verification' }
];

function matchesFilter(gate, filter) {
  if (filter === 'ALL') return true;
  return gateDisplayStatus(gate) === filter;
}

export default function GateOverview({ gates, selectedGateId, onSelect, loading }) {
  const [query, setQuery] = useState('');
  const [filter, setFilter] = useState('ALL');

  const rows = useMemo(() => {
    const term = query.trim().toLowerCase();
    return gates.filter((gate) => {
      if (!matchesFilter(gate, filter)) return false;
      if (!term) return true;
      return [gate.name, gate.gateCode, gate.location]
        .filter(Boolean)
        .some((value) => String(value).toLowerCase().includes(term));
    });
  }, [gates, query, filter]);

  return (
    <section className="card gate-overview" aria-labelledby="gates-heading">
      <div className="section-head section-head-tight">
        <div>
          <span className="eyebrow">Monitoring</span>
          <h2 id="gates-heading">Railway gates</h2>
        </div>
        <span className="muted small">{rows.length} of {gates.length} gates</span>
      </div>

      <div className="gate-toolbar">
        <label className="search-field">
          <span className="sr-only">Search gates</span>
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8"
               strokeLinecap="round" aria-hidden="true">
            <circle cx="11" cy="11" r="7" />
            <path d="M16.5 16.5L21 21" />
          </svg>
          <input
            type="search"
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            placeholder="Search gate, code or location"
          />
        </label>
        <div className="filter-group" role="group" aria-label="Filter gates by status">
          {FILTERS.map((item) => (
            <button
              key={item.id}
              type="button"
              className={`filter-chip ${filter === item.id ? 'filter-chip-active' : ''}`}
              onClick={() => setFilter(item.id)}
              aria-pressed={filter === item.id}
            >
              {item.label}
            </button>
          ))}
        </div>
      </div>

      {loading && gates.length === 0 ? (
        <div className="empty-state" role="status">
          <span className="empty-title">Loading railway gates…</span>
        </div>
      ) : rows.length === 0 ? (
        <div className="empty-state" role="status">
          <span className="empty-title">No gates match this filter</span>
          <p>Clear the search or choose a different status.</p>
        </div>
      ) : (
        <div className="table-wrap">
          <table className="data-table gate-table responsive-table">
            <caption className="sr-only">Railway gates with status, next train, ETA and expected opening time</caption>
            <thead>
              <tr>
                <th scope="col">Gate</th>
                <th scope="col">Location</th>
                <th scope="col">Status</th>
                <th scope="col">Approaching train</th>
                <th scope="col">ETA</th>
                <th scope="col">Expected opening</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((gate) => {
                const status = gateDisplayStatus(gate);
                const selected = gate.id === selectedGateId;
                return (
                  <tr
                    key={gate.id}
                    className={selected ? 'gate-row gate-row-selected' : 'gate-row'}
                    onClick={() => onSelect(gate.id)}
                  >
                    <td data-label="Gate">
                      <button
                        type="button"
                        className="gate-link"
                        onClick={(event) => {
                          event.stopPropagation();
                          onSelect(gate.id);
                        }}
                        aria-current={selected ? 'true' : undefined}
                      >
                        {gate.name}
                      </button>
                      <small>{gate.gateCode}</small>
                    </td>
                    <td data-label="Location">{gate.location || 'Not Available'}</td>
                    <td data-label="Status"><StatusBadge status={status} /></td>
                    <td data-label="Approaching train">
                      {gate.nextTrainLabel || <span className="dash">No train due</span>}
                    </td>
                    <td data-label="ETA">
                      {gate.nextTrainEta ? formatClock(gate.nextTrainEta) : <span className="dash">—</span>}
                    </td>
                    <td data-label="Expected opening">
                      {gate.expectedOpenAt ? formatClock(gate.expectedOpenAt) : <span className="dash">—</span>}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}

      <p className="table-note">
        CLOSING and OPENING are estimate-based labels built from server countdowns. Gate status is shown as
        Awaiting Verification whenever it cannot be verified; no gate is presented as OPEN or CLOSED without
        a verified report.
      </p>
    </section>
  );
}
