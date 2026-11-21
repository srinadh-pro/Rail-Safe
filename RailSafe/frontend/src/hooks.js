import { useCallback, useEffect, useRef, useState } from 'react';
import { apiFetch } from './api.js';

const POLL_MS = 20000;

/** Gate list + one aggregated dashboard payload, refreshed by polling. */
export function useLiveDashboard() {  const [gates, setGates] = useState([]);
  const [selectedGateId, setSelectedGateId] = useState(null);
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState(null);
  const [lastSuccess, setLastSuccess] = useState(null);
  const [reloadKey, setReloadKey] = useState(0);
  const inFlight = useRef(false);

  const loadGates = useCallback(async () => {
    const list = await apiFetch('/api/live/gates');
    setGates(list);
    setSelectedGateId((current) => {
      if (current && list.some((gate) => gate.id === current)) return current;
      return list.length ? list[0].id : null;
    });
    return list;
  }, []);

  useEffect(() => {
    let cancelled = false;
    loadGates()
      .catch((err) => {
        if (!cancelled) setError(err.message);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [loadGates]);

  useEffect(() => {
    if (!selectedGateId) return undefined;

    let cancelled = false;
    let timer = null;

    const poll = async (manual) => {
      if (inFlight.current || document.hidden) return;
      inFlight.current = true;
      if (manual) setRefreshing(true);
      try {
        const result = await apiFetch(`/api/live/dashboard?gateId=${selectedGateId}`);
        if (cancelled) return;
        setData(result);
        setLastSuccess(new Date());
        setError(null);
        // Keep the overview table (counts, next train, expected opening) in step
        // with the selected-gate payload. A gate-list failure must not discard a
        // good dashboard response.
        loadGates().catch(() => {});
      } catch (err) {
        if (!cancelled) setError(err.message);
      } finally {
        inFlight.current = false;
        if (!cancelled) setRefreshing(false);
      }
    };

    const start = () => {
      poll(false);
      timer = window.setInterval(() => poll(false), POLL_MS);
    };

    const onVisibility = () => {
      if (!document.hidden) {
        poll(false);
      }
    };

    start();
    document.addEventListener('visibilitychange', onVisibility);
    return () => {
      cancelled = true;
      if (timer) window.clearInterval(timer);
      document.removeEventListener('visibilitychange', onVisibility);
    };
  }, [selectedGateId, reloadKey, loadGates]);

  const refresh = useCallback(() => {
    setReloadKey((value) => value + 1);
  }, []);

  return {
    gates,
    selectedGateId,
    setSelectedGateId,
    data,
    loading,
    refreshing,
    error,
    lastSuccess,
    refresh,
    reloadGates: loadGates
  };
}

/**
 * Network/glance counts from /api/live/counts, kept fresh by the /api/live/stream
 * SSE feed. `counts` and `gate-status` events refresh the counts; `heartbeat`
 * and `connected` only prove that the stream is alive. The LIVE indicator is
 * driven strictly by counts.liveDataAvailable, never by connection state.
 */
export function useLiveCounts() {
  const [counts, setCounts] = useState(null);
  const [connected, setConnected] = useState(false);
  const [error, setError] = useState(null);
  const [reloadKey, setReloadKey] = useState(0);

  const refresh = useCallback(async () => {
    try {
      const result = await apiFetch('/api/live/counts');
      setCounts(result);
      setError(null);
      return result;
    } catch (err) {
      if (err.status !== 401) setError(err.message);
      return null;
    }
  }, []);

  useEffect(() => {
    refresh();
  }, [refresh, reloadKey]);

  useEffect(() => {
    if (typeof EventSource === 'undefined') return undefined;

    let source;
    try {
      source = new EventSource('/api/live/stream', { withCredentials: true });
    } catch {
      return undefined;
    }

    const applyPayload = (raw) => {
      try {
        const payload = typeof raw === 'string' ? JSON.parse(raw) : raw;
        if (payload && typeof payload === 'object') {
          setCounts(payload);
          setError(null);
        }
      } catch {
        // Ignore a malformed event and keep the last good snapshot.
      }
    };

    const onCounts = (event) => {
      applyPayload(event.data);
      setConnected(true);
    };
    const onGateStatus = () => {
      // A gate status change can alter every count, so re-read the endpoint.
      refresh();
      setConnected(true);
    };
    const onHeartbeat = () => setConnected(true);
    const onConnected = () => setConnected(true);
    const onError = () => setConnected(false);

    source.addEventListener('counts', onCounts);
    source.addEventListener('gate-status', onGateStatus);
    source.addEventListener('heartbeat', onHeartbeat);
    source.addEventListener('connected', onConnected);
    source.addEventListener('error', onError);

    return () => {
      source.removeEventListener('counts', onCounts);
      source.removeEventListener('gate-status', onGateStatus);
      source.removeEventListener('heartbeat', onHeartbeat);
      source.removeEventListener('connected', onConnected);
      source.removeEventListener('error', onError);
      source.close();
    };
  }, [refresh]);

  const manualRefresh = useCallback(() => setReloadKey((value) => value + 1), []);

  return { counts, connected, error, refresh, manualRefresh };
}

/** Full detail payload for one gate; re-fetched only when the selection changes. */
export function useGateDetail(gateId) {
  const [detail, setDetail] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  useEffect(() => {
    if (!gateId) {
      setDetail(null);
      setError(null);
      setLoading(false);
      return undefined;
    }

    let cancelled = false;
    setLoading(true);
    setError(null);
    apiFetch(`/api/gates/${encodeURIComponent(gateId)}/detail`)
      .then((result) => {
        if (!cancelled) setDetail(result);
      })
      .catch((err) => {
        if (!cancelled) {
          setDetail(null);
          if (err.status !== 401) setError(err.message);
        }
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [gateId]);

  return { detail, loading, error };
}

/** Verified reference train catalogue (never a live tracking feed). */
export function useTrainReference() {
  const [trains, setTrains] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    apiFetch('/api/trains')
      .then((result) => {
        if (!cancelled) {
          setTrains(Array.isArray(result) ? result : []);
          setError(null);
        }
      })
      .catch((err) => {
        if (!cancelled && err.status !== 401) setError(err.message);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [reloadKey]);

  const refresh = useCallback(() => setReloadKey((value) => value + 1), []);

  return { trains, loading, error, refresh };
}

/** Ticks once per second and accounts for server/browser clock skew. */
export function useCountdown(targetIso, generatedAt) {
  const skew = useRef(0);
  const [now, setNow] = useState(() => Date.now());

  useEffect(() => {
    if (generatedAt) {
      const generated = new Date(generatedAt).getTime();
      if (!Number.isNaN(generated)) {
        skew.current = Date.now() - generated;
      }
    }
    setNow(Date.now());
  }, [generatedAt]);

  useEffect(() => {
    if (!targetIso) return undefined;
    const timer = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, [targetIso]);

  if (!targetIso) return null;
  const target = new Date(targetIso).getTime();
  if (Number.isNaN(target)) return null;
  return Math.max(0, Math.round((target - (now - skew.current)) / 1000));
}

function showBrowserAlert(item) {
  if (typeof window === 'undefined' || !('Notification' in window)) return;
  if (Notification.permission !== 'granted') return;
  try {
    new Notification(item.title, {
      body: item.message,
      tag: `railsafe-live-${item.id}`,
      icon: '/favicon.svg'
    });
  } catch {
    // Some browsers block constructor notifications in background tabs.
  }
}

/** Notification history, preferences and browser-permission state. */
export function useNotifications(user) {
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [permission, setPermission] = useState(
    typeof window !== 'undefined' && 'Notification' in window ? Notification.permission : 'unsupported'
  );
  const [preferences, setPreferences] = useState(null);
  const [savingPreferences, setSavingPreferences] = useState(false);
  const lastSeen = useRef(Number(window.localStorage.getItem('railsafe.live.lastNotificationId') || 0));
  const firstLoad = useRef(true);

  const load = useCallback(async () => {
    if (!user) return;
    setLoading(true);
    try {
      const list = await apiFetch('/api/notifications');
      setItems(list);
      setError(null);

      const maximum = list.reduce((max, item) => Math.max(max, item.id), 0);
      if (firstLoad.current) {
        firstLoad.current = false;
        if (!lastSeen.current) {
          lastSeen.current = maximum;
          window.localStorage.setItem('railsafe.live.lastNotificationId', String(maximum));
        }
      } else {
        const fresh = list.filter((item) => item.id > lastSeen.current && !item.read);
        if (fresh.length) {
          fresh.slice().reverse().forEach(showBrowserAlert);
          lastSeen.current = maximum;
          window.localStorage.setItem('railsafe.live.lastNotificationId', String(maximum));
        }
      }
    } catch (err) {
      if (err.status !== 401) setError(err.message);
    } finally {
      setLoading(false);
    }
  }, [user]);

  const loadPreferences = useCallback(async () => {
    if (!user) return;
    try {
      setPreferences(await apiFetch('/api/notifications/preferences'));
    } catch (err) {
      if (err.status !== 401) setError(err.message);
    }
  }, [user]);

  useEffect(() => {
    if (!user) {
      setItems([]);
      setPreferences(null);
      firstLoad.current = true;
      return undefined;
    }
    load();
    loadPreferences();
    const timer = window.setInterval(load, POLL_MS);
    return () => window.clearInterval(timer);
  }, [user, load, loadPreferences]);

  const requestPermission = useCallback(async () => {
    if (!('Notification' in window)) {
      setPermission('unsupported');
      return 'unsupported';
    }
    const result = await Notification.requestPermission();
    setPermission(result);
    if (result === 'granted') {
      try {
        new Notification('RailSafe browser alerts enabled', {
          body: 'Gate and train alerts will appear here while this dashboard is open.',
          icon: '/favicon.svg'
        });
      } catch {
        // Ignore constructor failures; in-app history still works.
      }
    }
    return result;
  }, []);

  const markRead = useCallback(async (id) => {
    await apiFetch(`/api/notifications/${id}/read`, { method: 'PUT' });
    setItems((current) => current.map((item) => (item.id === id ? { ...item, read: true } : item)));
  }, []);

  const markAllRead = useCallback(async () => {
    await apiFetch('/api/notifications/read-all', { method: 'PUT' });
    setItems((current) => current.map((item) => ({ ...item, read: true })));
  }, []);

  const savePreferences = useCallback(async (next) => {
    setSavingPreferences(true);
    try {
      const saved = await apiFetch('/api/notifications/preferences', {
        method: 'PUT',
        body: JSON.stringify(next)
      });
      setPreferences(saved);
      return saved;
    } finally {
      setSavingPreferences(false);
    }
  }, []);

  return {
    items,
    loading,
    error,
    permission,
    preferences,
    savingPreferences,
    requestPermission,
    markRead,
    markAllRead,
    savePreferences,
    refresh: load
  };
}
