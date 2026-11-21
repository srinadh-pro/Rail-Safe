import React, { useEffect, useState } from 'react';
import { apiFetch, getCurrentUser } from './api.js';
import { formatClock, timeAgo } from './format.js';
import { useLiveDashboard, useLiveCounts, useNotifications } from './hooks.js';
import { normalizeGateStatus } from './status.js';
import ProviderBanner from './components/ProviderBanner.jsx';
import GateOverview from './components/GateOverview.jsx';
import GatePanel from './components/GatePanel.jsx';
import GateDetailPanel from './components/GateDetailPanel.jsx';
import CountdownPanel from './components/CountdownPanel.jsx';
import TrainsPanel from './components/TrainsPanel.jsx';
import TrainReferencePanel from './components/TrainReferencePanel.jsx';
import RoutesPanel from './components/RoutesPanel.jsx';
import NotificationsPanel from './components/NotificationsPanel.jsx';
import SimulationPanel from './components/SimulationPanel.jsx';

const ICONS = {
  gates: <path d="M4 5h16M4 12h16M4 19h16M8 5v14M16 5v14" />,
  open: <path d="M9 12.5l2 2 4.5-4.5M12 3.5l7 3v5.2c0 4-3 7.4-7 8.8-4-1.4-7-4.8-7-8.8V6.5l7-3z" />,
  closed: <path d="M12 3.5l7 3v5.2c0 4-3 7.4-7 8.8-4-1.4-7-4.8-7-8.8V6.5l7-3zM9.5 9.5l5 5M14.5 9.5l-5 5" />,
  train: <path d="M6 4h12a2 2 0 0 1 2 2v8a3 3 0 0 1-3 3H7a3 3 0 0 1-3-3V6a2 2 0 0 1 2-2zM4 9h16M9 21l1.5-4M15 21l-1.5-4M8.5 14h.01M15.5 14h.01" />,
  closing: <path d="M12 3v6M12 21v-6M4.2 7.5l4 2.3M19.8 7.5l-4 2.3M4.2 16.5l4-2.3M19.8 16.5l-4-2.3" />,
  opening: <path d="M8 12h8M13 9l3 3-3 3M12 3.5l7 3v5.2c0 4-3 7.4-7 8.8-4-1.4-7-4.8-7-8.8V6.5l7-3z" />,
  awaiting: <path d="M12 8v5M12 17h.01M12 3.5l7 3v5.2c0 4-3 7.4-7 8.8-4-1.4-7-4.8-7-8.8V6.5l7-3z" />,
  crossing: <path d="M4 18h16M6 18V8l6-4 6 4v10M10 18v-5h4v5" />
};

function SummaryIcon({ name }) {
  return (
    <span className={`summary-icon summary-icon-${name}`} aria-hidden="true">
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8"
           strokeLinecap="round" strokeLinejoin="round">
        {ICONS[name]}
      </svg>
    </span>
  );
}

function SummaryCard({ tone, icon, label, value, hint }) {
  return (
    <article className={`summary-card summary-card-${tone}`}>
      <div className="summary-card-top">
        <span className="summary-label">{label}</span>
        <SummaryIcon name={icon} />
      </div>
      <strong className="summary-value">{value}</strong>
      <span className="summary-hint">{hint}</span>
    </article>
  );
}

const SUMMARY_CARDS = [
  { key: 'totalGates', label: 'Total railway gates', icon: 'gates', tone: 'blue', hint: 'Registered crossings' },
  { key: 'openGates', label: 'Open gates', icon: 'open', tone: 'green', hint: 'Verified open' },
  { key: 'closedGates', label: 'Closed gates', icon: 'closed', tone: 'red', hint: 'Verified closed' },
  { key: 'closingGates', label: 'Closing gates', icon: 'closing', tone: 'orange', hint: 'Barrier lowering' },
  { key: 'openingGates', label: 'Opening gates', icon: 'opening', tone: 'blue', hint: 'Barrier lifting' },
  { key: 'awaitingVerificationGates', label: 'Awaiting verification', icon: 'awaiting', tone: 'yellow', hint: 'Status not verified' },
  { key: 'approachingTrains', label: 'Approaching trains', icon: 'train', tone: 'orange', hint: 'Matched to a gate' },
  { key: 'activeCrossings', label: 'Active crossings', icon: 'crossing', tone: 'green', hint: 'Train crossing now' }
];

const CHART_SEGMENTS = [
  { key: 'openGates', label: 'Open', segment: 'chart-segment-open', swatch: 'legend-open' },
  { key: 'closedGates', label: 'Closed', segment: 'chart-segment-closed', swatch: 'legend-closed' },
  { key: 'closingGates', label: 'Closing', segment: 'chart-segment-closing', swatch: 'legend-closing' },
  { key: 'openingGates', label: 'Opening', segment: 'chart-segment-opening', swatch: 'legend-opening' },
  { key: 'awaitingVerificationGates', label: 'Awaiting Verification', segment: 'chart-segment-awaiting', swatch: 'legend-awaiting' },
  { key: 'maintenanceGates', label: 'Maintenance', segment: 'chart-segment-maintenance', swatch: 'legend-maintenance' }
];

function SummarySection({ counts, connected }) {
  const value = (key) => (counts && counts[key] !== null && counts[key] !== undefined ? counts[key] : '—');
  const scope = counts?.scope || 'network';

  return (
    <section className="summary-section" aria-labelledby="summary-heading">
      <div className="section-head section-head-tight">
        <div>
          <span className="eyebrow">Overview · {String(scope).toLowerCase()}</span>
          <h2 id="summary-heading">Network at a glance</h2>
        </div>
        <span className="muted small">
          {connected ? 'Live stream connected' : 'Live stream disconnected'}
          {counts?.generatedAt ? ` · snapshot ${formatClock(counts.generatedAt)}` : ''}
        </span>
      </div>
      <div className="summary-grid">
        {SUMMARY_CARDS.map((card) => (
          <SummaryCard
            key={card.key}
            tone={card.tone}
            icon={card.icon}
            label={card.label}
            value={value(card.key)}
            hint={card.hint}
          />
        ))}
      </div>

      <div className="status-chart card" aria-label="Verified gate status distribution">
        <div className="section-head section-head-tight">
          <div>
            <span className="eyebrow">Status distribution</span>
            <h3>Gate status</h3>
          </div>
          <span className="muted small">
            Current snapshot · {value('totalGates')} crossing{counts?.totalGates === 1 ? '' : 's'}
          </span>
        </div>
        <div
          className="chart-bar"
          role="img"
          aria-label={CHART_SEGMENTS.map((segment) => `${value(segment.key)} ${segment.label.toLowerCase()}`).join(', ')}
        >
          {CHART_SEGMENTS.map((segment) => (
            <span
              key={segment.key}
              className={`chart-segment ${segment.segment}`}
              style={{ flexGrow: Math.max(Number(counts?.[segment.key]) || 0, 0.25) }}
            />
          ))}
        </div>
        <div className="chart-legend">
          {CHART_SEGMENTS.map((segment) => (
            <span key={segment.key}>
              <i className={`legend-swatch ${segment.swatch}`} />
              {segment.label} <strong>{value(segment.key)}</strong>
            </span>
          ))}
        </div>
      </div>
    </section>
  );
}

/** LIVE is shown only when the counts payload says liveDataAvailable === true. */
function LiveIndicator({ counts }) {
  const live = counts?.liveDataAvailable === true;
  return (
    <div
      className={`live-indicator ${live ? 'live-indicator-on' : 'live-indicator-off'}`}
      role="status"
      title={counts?.message || (live ? 'Authorised live data is available.' : 'No authorised live data is available.')}
    >
      <span className="live-dot" aria-hidden="true" />
      <strong>{live ? 'LIVE' : 'Live data unavailable'}</strong>
    </div>
  );
}

/** Role-aware navigation. The server still enforces every protected route. */
function navLinksFor(role) {
  const links = [
    { label: 'Home', href: '/index.html' },
    { label: 'Gate map', href: '/map.html' }
  ];

  if (role === 'STAFF') {
    links.push({ label: 'Assigned gates', href: '/staff.html' });
  } else if (role === 'ADMIN') {
    links.push({ label: 'Operations', href: '/admin.html' });
  } else if (role === 'SUPER_ADMIN') {
    links.push({ label: 'Operations', href: '/admin.html' });
    links.push({ label: 'System & source review', href: '/super-admin.html' });
  } else {
    // CUSTOMER, legacy USER and not-yet-authenticated visitors use the customer view.
    links.push({ label: 'Dashboard', href: '/dashboard.html' });
  }

  links.push({ label: 'Live monitoring', href: '/live/' });
  links.push({ label: 'Alerts', href: '/notifications.html' });
  return links;
}

function LoadingState() {
  return (
    <div className="loading-state" role="status" aria-live="polite">
      <span className="spinner" aria-hidden="true" />
      <div>
        <strong>Loading the dashboard…</strong>
        <p>Reading railway gates, train positions and status predictions from the RailSafe server.</p>
      </div>
    </div>
  );
}

function gateStatusFor(prediction, gate) {
  if (!prediction) return normalizeGateStatus(gate?.verifiedStatus || gate?.status);
  if (prediction.verifiedStatus === 'CLOSED') {
    return prediction.opening?.available ? 'OPENING' : 'CLOSED';
  }
  if (prediction.verifiedStatus === 'OPEN') {
    return prediction.closing?.available ? 'CLOSING' : 'OPEN';
  }
  return normalizeGateStatus(prediction.verifiedStatus || gate?.status);
}

export default function App() {
  const [user, setUser] = useState(null);
  const [authLoading, setAuthLoading] = useState(true);
  const [sidebarOpen, setSidebarOpen] = useState(false);

  const dashboard = useLiveDashboard();
  const live = useLiveCounts();
  const notifications = useNotifications(user);

  useEffect(() => {
    let cancelled = false;
    getCurrentUser()
      .then((result) => {
        if (!cancelled) setUser(result);
      })
      .catch(() => {
        if (!cancelled) setUser(null);
      })
      .finally(() => {
        if (!cancelled) setAuthLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    if (!authLoading && !user) {
      // The server also protects this page, so this only handles an expired session.
      window.location.href = '/login.html?next=' + encodeURIComponent('live/');
    }
  }, [authLoading, user]);

  const handleLogout = async () => {
    try {
      await apiFetch('/api/auth/logout', { method: 'POST' });
    } catch {
      // The redirect still clears local access when the request fails.
    }
    window.location.href = '/login.html';
  };

  const { gates, selectedGateId, setSelectedGateId, data, loading, refreshing, error, lastSuccess, refresh } = dashboard;
  const { counts, connected, error: countsError, manualRefresh: refreshCounts } = live;

  const handleRefresh = () => {
    refresh();
    refreshCounts();
  };

  const selectedSummary = gates.find((gate) => gate.id === selectedGateId) || null;
  const detailReady = Boolean(data && data.gate && data.gate.id === selectedGateId);
  const selectedGate = detailReady ? data.gate : selectedSummary;
  const prediction = detailReady ? data.prediction : null;
  const detailStatus = gateStatusFor(prediction, selectedGate);
  const mode = data?.tracking?.mode || selectedSummary?.dataMode;
  const sourceLabel = mode === 'THIRD_PARTY'
    ? 'third-party'
    : mode === 'SCHEDULE'
      ? 'schedule'
      : mode === 'LIVE'
        ? 'live feed'
        : 'unverified';

  const navLinks = navLinksFor(user?.role);

  return (
    <div className={`app-shell${sidebarOpen ? ' sidebar-open' : ''}`}>
      <a className="skip-link" href="#main-content">Skip to main content</a>

      <aside className={`app-sidebar${sidebarOpen ? ' app-sidebar-open' : ''}`} aria-label="Sidebar navigation">
        <a className="sidebar-brand" href="/index.html" aria-label="RailSafe home">
          <span className="brand-mark" aria-hidden="true">RS</span>
          <span>
            <strong>RailSafe</strong>
            <small>Railway safety platform</small>
          </span>
        </a>

        <nav className="sidebar-nav" aria-label="Primary">
          {navLinks.map((link) => {
            const active = link.href === '/live/';
            return (
              <a
                key={`side-${link.href}-${link.label}`}
                className={active ? 'app-nav-active' : undefined}
                href={link.href}
                aria-current={active ? 'page' : undefined}
              >
                {link.label}
              </a>
            );
          })}
        </nav>

        <div className="sidebar-foot">
          <span className="sidebar-foot-label">Operational context</span>
          <p>Verified status only. Physical barriers and authorised staff always take priority.</p>
          {user ? (
            <div className="sidebar-user">
              <span className="sidebar-avatar" aria-hidden="true">
                {user.fullName?.split(/\s+/).slice(0, 2).map((part) => part.charAt(0).toUpperCase()).join('')}
              </span>
              <span>
                <strong>{user.fullName}</strong>
                <small>{user.role}</small>
              </span>
            </div>
          ) : null}
        </div>
      </aside>

      <button type="button" className="app-sidebar-backdrop"
              aria-label="Close navigation" onClick={() => setSidebarOpen(false)} />

      <header className="app-header">
        <div className="app-header-inner">
          <button type="button" className="sidebar-toggle"
                  aria-label="Open navigation" aria-expanded={sidebarOpen}
                  onClick={() => setSidebarOpen((open) => !open)}>
            <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" strokeWidth="1.8">
              <path d="M4 7h16M4 12h16M4 17h16" strokeLinecap="round" />
            </svg>
          </button>

          <a className="brand" href="/index.html" aria-label="RailSafe home">
            <span className="brand-mark" aria-hidden="true">RS</span>
            <span className="brand-text">
              <strong>RailSafe</strong>
              <small>Railway gate monitoring</small>
            </span>
          </a>

          <nav className="app-nav" aria-label="Primary">
            {navLinks.map((link) => {
              const active = link.href === '/live/';
              return (
                <a
                  key={`head-${link.href}-${link.label}`}
                  className={active ? 'app-nav-active' : undefined}
                  href={link.href}
                  aria-current={active ? 'page' : undefined}
                >
                  {link.label}
                </a>
              );
            })}
          </nav>

          <div className="app-user">
            {authLoading ? (
              <span className="muted small">Checking session…</span>
            ) : user ? (
              <>
                <span className="user-chip" title={`${user.email || user.id} · ${user.role}`}>{user.fullName}</span>
                <span className="muted small">{user.role === 'SUPER_ADMIN' ? 'Super Admin' : user.role}</span>
                <a className="btn btn-ghost btn-small" href="/dashboard.html">My account</a>
                <button type="button" className="btn btn-ghost btn-small" onClick={handleLogout}>Log out</button>
              </>
            ) : (
              <>
                <a className="btn btn-ghost btn-small" href="/login.html">Log in</a>
                <a className="btn btn-primary btn-small" href="/register.html">Register</a>
              </>
            )}
          </div>
        </div>
      </header>

      <main id="main-content" className="app-main">
        <div className="page-heading">
          <div>
            <span className="eyebrow">Live assistance · login required</span>
            <h1>Railway gate monitoring</h1>
            <p>Gate status, approaching trains, expected timings and verified alternate routes.</p>
          </div>
          <div className="page-actions">
            <LiveIndicator counts={counts} />
            <span className="update-time">
              {counts?.lastSuccessfulUpdate ? (
                <>Last update {formatClock(counts.lastSuccessfulUpdate)}</>
              ) : lastSuccess ? (
                <>Updated {lastSuccess.toLocaleTimeString()} · {timeAgo(lastSuccess)}</>
              ) : (
                'Waiting for the first update'
              )}
            </span>
            <button type="button" className="btn btn-primary" onClick={handleRefresh} disabled={refreshing}>
              {refreshing ? 'Refreshing…' : 'Refresh'}
            </button>
          </div>
        </div>

        {error || countsError ? (
          <div className="notice notice-red" role="alert">
            <strong>Could not update part of the dashboard.</strong> {error || countsError}
            {' '}Showing the last successful data where available.
          </div>
        ) : null}

        {counts && counts.liveDataAvailable !== true && counts.message ? (
          <div className="notice notice-grey" role="status">{counts.message}</div>
        ) : null}

        {loading && !data ? (
          <LoadingState />
        ) : (
          <>
            <div className="data-slot">
              <ProviderBanner
                provider={data?.provider}
                tracking={data?.tracking}
                generatedAt={data?.generatedAt}
                lastSuccess={lastSuccess}
              />
            </div>

            <SummarySection counts={counts} connected={connected} />

            <SimulationPanel />

            <GateOverview
              gates={gates}
              selectedGateId={selectedGateId}
              onSelect={setSelectedGateId}
              loading={loading}
            />

            <section className="card gate-detail" aria-labelledby="gate-heading">
              {selectedGate ? (
                <>
                  <GatePanel gate={selectedGate} prediction={prediction} status={detailStatus} />

                  {prediction ? (
                    <>
                      <CountdownPanel
                        prediction={prediction}
                        generatedAt={data.generatedAt}
                        nextTrain={prediction.nextTrain}
                      />
                      <TrainsPanel tracking={data.tracking} prediction={prediction} />
                    </>
                  ) : (
                    <div className="detail-loading" role="status">
                      <span className="spinner spinner-small" aria-hidden="true" />
                      Loading train data for {selectedGate.name}…
                    </div>
                  )}
                </>
              ) : (
                <div className="empty-state">
                  <span className="empty-title">No railway gate selected</span>
                  <p>Choose a gate from the table above to see its trains and timings.</p>
                </div>
              )}
            </section>

            {selectedGateId ? <GateDetailPanel gateId={selectedGateId} /> : null}

            {prediction ? (
              <div className="meta-line">
                <span>
                  Data source: <strong>{sourceLabel}</strong>
                </span>
                <span>
                  Automatic refresh every {data?.pollIntervalSeconds || 20} s. Countdowns tick in the browser and
                  are estimates, not sensor readings.
                </span>
              </div>
            ) : null}

            {selectedGate ? (
              <section className="routes-area" aria-labelledby="alternate-heading">
                <div className="section-head">
                  <div>
                    <span className="eyebrow">Route assistance</span>
                    <h2 id="alternate-heading">Alternate routes &amp; impact</h2>
                    <p className="muted small">
                      Primary and genuine verified alternatives are shown together. Distances come from
                      OpenStreetMap and OSRM; nothing is generated or simulated.
                    </p>
                  </div>
                </div>
                <RoutesPanel gate={selectedGate} prediction={prediction} />
              </section>
            ) : null}

            <TrainReferencePanel />

            <details className="disclosure">
              <summary>
                <span>Alerts &amp; notifications</span>
                <span className="disclosure-note">
                  {user ? 'History, browser alerts and delivery preferences' : 'Sign in to see your alert history'}
                </span>
              </summary>
              <div className="disclosure-body">
                <NotificationsPanel user={user} notifications={notifications} />
              </div>
            </details>

            <details className="disclosure about-data">
              <summary>
                <span>About the data</span>
                <span className="disclosure-note">How to read status, ETAs and countdowns</span>
              </summary>
              <div className="disclosure-body">
                <div className="honesty-grid">
                  <div>
                    <span className="tag tag-green">VERIFIED</span>
                    <p>A gate is shown as OPEN or CLOSED only when the stored status is fresh and verified by staff or an authorised sensor.</p>
                  </div>
                  <div>
                    <span className="tag tag-orange">ESTIMATED</span>
                    <p>ETAs and countdowns are predictions from the available feed. They are never a guarantee.</p>
                  </div>
                  <div>
                    <span className="tag tag-orange">DATA SOURCES</span>
                    <p>Reported, verified and third-party information is labelled. Estimates are advisory only; verify locally and obey physical barriers.</p>
                  </div>
                  <div>
                    <span className="tag tag-grey">UNAVAILABLE</span>
                    <p>When no reliable data exists, RailSafe says so instead of inventing a train, time or route.</p>
                  </div>
                </div>
              </div>
            </details>
          </>
        )}
      </main>

      <footer className="app-footer">
        <div className="footer-inner">
          <div>
            <strong>RailSafe</strong> · Smart Railway Gate Alert &amp; Alternative Route Assistance
          </div>
          <div className="app-footer-links">
            <a href="/index.html">Home</a>
            <a href="/map.html">Gate map</a>
            <a href="/notifications.html">Alerts</a>
            <a href="/api/live/provider" target="_blank" rel="noreferrer">Provider status</a>
          </div>
          <small>
            Safety first: never cross a closed railway gate, never bypass a barrier, and always follow
            physical signals and authorised staff. This dashboard is decision support, not a safety
            certification.
          </small>
        </div>
      </footer>
    </div>
  );
}
