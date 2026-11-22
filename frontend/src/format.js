import { gateStatusTone } from './status.js';

export function formatClock(value) {
  if (!value) return 'Not available';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return 'Not available';
  return date.toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' });
}

export function formatDateTime(value) {
  if (!value) return 'Not available';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return 'Not available';
  return date.toLocaleString(undefined, {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit'
  });
}

export function timeAgo(value) {
  if (!value) return 'not available';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return 'not available';
  const seconds = Math.round((date.getTime() - Date.now()) / 1000);
  const formatter = new Intl.RelativeTimeFormat(undefined, { numeric: 'auto' });
  const ranges = [
    ['year', 31536000],
    ['month', 2592000],
    ['week', 604800],
    ['day', 86400],
    ['hour', 3600],
    ['minute', 60]
  ];
  for (const [unit, size] of ranges) {
    if (Math.abs(seconds) >= size) {
      return formatter.format(Math.round(seconds / size), unit);
    }
  }
  return formatter.format(seconds, 'second');
}

export function formatCountdown(totalSeconds) {
  if (totalSeconds === null || totalSeconds === undefined || Number.isNaN(totalSeconds)) {
    return '--:--';
  }
  const safe = Math.max(0, Math.floor(totalSeconds));
  const hours = Math.floor(safe / 3600);
  const minutes = Math.floor((safe % 3600) / 60);
  const seconds = safe % 60;
  if (hours > 0) {
    return `${hours}:${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`;
  }
  return `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`;
}

export function formatMinutes(seconds) {
  if (seconds === null || seconds === undefined) return 'unavailable';
  const value = Math.max(1, Math.ceil(seconds / 60));
  return value === 1 ? 'about 1 minute' : `about ${value} minutes`;
}

export function formatKm(value) {
  if (value === null || value === undefined || Number.isNaN(Number(value))) return 'unavailable';
  return `${Number(value).toFixed(1)} km`;
}

export function formatMeters(value) {
  if (value === null || value === undefined || Number.isNaN(Number(value))) return 'unavailable';
  return `${Math.round(Number(value))} m`;
}

export function titleCase(value) {
  if (!value) return 'Unknown';
  return String(value)
    .toLowerCase()
    .replaceAll('_', ' ')
    .replace(/\b\w/g, (letter) => letter.toUpperCase());
}

/** Compact HH:MM:SS (or MM:SS) for the browser-ticked status duration. */
export function formatDuration(totalSeconds) {
  if (totalSeconds === null || totalSeconds === undefined || Number.isNaN(Number(totalSeconds))) {
    return 'Not Available';
  }
  const safe = Math.max(0, Math.floor(Number(totalSeconds)));
  const hours = Math.floor(safe / 3600);
  const minutes = Math.floor((safe % 3600) / 60);
  const seconds = safe % 60;
  const pad = (value) => String(value).padStart(2, '0');
  return hours > 0
    ? `${pad(hours)}:${pad(minutes)}:${pad(seconds)}`
    : `${pad(minutes)}:${pad(seconds)}`;
}

export function gateStatusClass(status) {
  return gateStatusTone(status);
}

export function trainStatusClass(status) {
  if (status === 'APPROACHING' || status === 'NEAR_GATE') return 'approaching';
  if (status === 'CROSSING') return 'closed';
  if (status === 'PASSED') return 'unknown';
  return 'unknown';
}
