/**
 * Single source of truth for railway-gate status handling.
 *
 * Every component (badges, overview table, summary chart and map markers) uses
 * these helpers so colours and wording stay consistent. Raw UNKNOWN is never
 * shown to a user: it is normalised to AWAITING_VERIFICATION and labelled
 * "Awaiting Verification".
 */

export const GATE_STATUS_META = {
  OPEN: { tone: 'green', color: '#16a34a', label: 'Open', badgeLabel: 'OPEN' },
  CLOSED: { tone: 'red', color: '#dc2626', label: 'Closed', badgeLabel: 'CLOSED' },
  CLOSING: { tone: 'orange', color: '#ea580c', label: 'Closing', badgeLabel: 'CLOSING' },
  OPENING: { tone: 'blue', color: '#2563eb', label: 'Opening', badgeLabel: 'OPENING' },
  MAINTENANCE: { tone: 'grey', color: '#64748b', label: 'Maintenance', badgeLabel: 'MAINTENANCE' },
  AWAITING_VERIFICATION: {
    tone: 'yellow',
    color: '#eab308',
    label: 'Awaiting Verification',
    badgeLabel: 'Awaiting Verification'
  },
  UNKNOWN: {
    tone: 'yellow',
    color: '#eab308',
    label: 'Awaiting Verification',
    badgeLabel: 'Awaiting Verification'
  }
};

/** Train movement statuses (kept separate from barrier states). */
export const TRAIN_STATUS_META = {
  APPROACHING: { tone: 'orange', label: 'APPROACHING' },
  NEAR_GATE: { tone: 'orange', label: 'NEAR GATE' },
  CROSSING: { tone: 'red', label: 'CROSSING' },
  PASSED: { tone: 'grey', label: 'PASSED' }
};

/** Colours used for the primary route and up to four genuine alternatives. */
export const ROUTE_COLORS = ['#2563eb', '#dc2626', '#16a34a', '#ea580c', '#ca8a04'];

export function normalizeGateStatus(status) {
  if (status === null || status === undefined) return 'AWAITING_VERIFICATION';
  const value = String(status).trim().toUpperCase().replace(/[\s-]+/g, '_');
  if (!value || value === 'UNKNOWN' || value === 'UNVERIFIED') return 'AWAITING_VERIFICATION';
  if (GATE_STATUS_META[value]) return value;
  return 'AWAITING_VERIFICATION';
}

export function gateStatusMeta(status) {
  return GATE_STATUS_META[normalizeGateStatus(status)];
}

export function gateStatusLabel(status) {
  return gateStatusMeta(status).label;
}

export function gateStatusBadgeLabel(status) {
  return gateStatusMeta(status).badgeLabel;
}

export function gateStatusTone(status) {
  return gateStatusMeta(status).tone;
}

export function gateStatusColor(status) {
  return gateStatusMeta(status).color;
}

export function isGateStatus(status) {
  if (status === null || status === undefined) return false;
  return Boolean(GATE_STATUS_META[String(status).trim().toUpperCase().replace(/[\s-]+/g, '_')]);
}

export function trainStatusMeta(status) {
  if (!status) return null;
  return TRAIN_STATUS_META[String(status).trim().toUpperCase()] || null;
}

/** Blank / null-safe display used by the detail panels. */
export function notAvailable(value, fallback = 'Not Available') {
  if (value === null || value === undefined) return fallback;
  if (typeof value === 'string' && value.trim() === '') return fallback;
  if (Array.isArray(value) && value.length === 0) return fallback;
  return value;
}
