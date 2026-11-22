# RailSafe Live Assistance Layer

This document describes the additive smart-crossing layer built on top of the
original RailSafe application. It covers architecture, data honesty rules, API
contracts, database changes, configuration, tests and known limitations.

> RailSafe is a hackathon demonstration. It has no connection to Indian Railways
> and ships with no authorised live train feed. This build defaults to the
> **SIMULATION** provider with a clearly labelled synthetic dataset so the demo
> works out of the box. Every simulated value is marked
> **SIMULATION MODE - NOT LIVE RAILWAY DATA**. Set `RAILSAFE_LIVE_MODE=unavailable`
> to return to the strict **LIVE TRAIN DATA UNAVAILABLE** state.

---

## 1. Goals and non-goals

### Goals

- Show verified gate status, train identity, direction, ETA and status for a
  selected crossing.
- Show a closing countdown only when the gate is verified OPEN and the
  approaching-train data is reliable.
- Show an opening countdown only when the gate is verified CLOSED.
- Predict the next train without pretending that a scheduled time is a live
  position.
- Offer alternative routes from OSRM/OpenStreetMap and verify that each route
  actually avoids the selected crossing.
- Offer clearly labelled synthetic demo alternatives per crossing so the closed
  gate flow can be demonstrated without a routing engine.
- Seed a realistic synthetic Indian Railways dataset (crossings, services,
  tracking rows, history, routes, notifications) while keeping it separate from
  the authorised audit tables.
- Deliver in-app, browser and optional email/SMS alerts for every required event
  (approaching, closing soon, closed, crossing, passed, opening soon, reopened,
  alternative route).
- Keep the original pages, endpoints, authentication and demo data working.

### Non-goals

- Replacing physical barriers, signals or railway staff.
- Claiming that a reported or simulated status is a live railway signal.
- Inventing train positions, ETAs, sensor readings, distances or durations
  outside the explicitly labelled simulation mode.
- Guaranteeing that a route is safe at the moment a driver reaches it.

---

## 2. Architecture

```text
Existing static site (Bootstrap + vanilla JS)
  /index.html /map.html /dashboard.html /gate.html
  /notifications.html /login.html /register.html /admin.html
        │
        │  same Spring Boot origin, session cookie
        ▼
Spring Boot 3.5 application
  ├── Existing controllers and services (unchanged contracts)
  ├── LiveController
  │     /api/live/provider
  │     /api/live/gates
  │     /api/live/dashboard
  │     /api/live/sensor/**
  ├── GateController additions
  │     /api/gates/{id}/trains
  │     /api/gates/{id}/prediction
  │     /api/gates/{id}/timing
  │     /api/gates/{id}/alternate-routes
  ├── TrainController
  │     /api/trains/{trainNumber}
  ├── AlternativeRouteController
  │     /api/routes/alternatives
  ├── NotificationPreferenceController
  │     /api/notifications/preferences
  └── Live services
        TrainTrackingService       provider adapter + cache + mode
        GatePredictionService      verified status, countdowns, next train
        GateTimingService          compact closing/opening timing payload
        AlternateRouteService      synthetic demo alternatives
        RailwayGateService         thin facade over gate + timing + routes
        TrainCatalogService        synthetic train catalogue lookup
        SyntheticDataService       idempotent demo dataset seeder
        LiveDashboardService       one polling payload and gate summaries
        RouteCalculationService    OSRM + polyline verification + audit
        LiveAlertScheduler         scheduled event evaluation and dedupe
        TrainSnapshotService       optional audit of real provider data
        NotificationService        history, watchers, optional channels
        AlertDeliveryService       email/SMS adapters, off by default

React 18 + Vite dashboard (built into static/live/)
  /live/
  ├── ProviderBanner      mode and data-honesty banner
  ├── GatePanel           verified status, source, timestamps
  ├── CountdownPanel      closing, opening and next-train cards
  ├── TrainsPanel         multiple relevant trains, speed and NEXT badge
  ├── DemoRoutesPanel     synthetic demo alternatives and map geometry
  ├── RoutesPanel         OSRM alternatives with map geometry
  └── NotificationsPanel  history, permission, channel preferences

External services (network required)
  ├── Authorised HTTP JSON train provider   (configured by the operator)
  ├── OSRM routing engine                   (public demo by default)
  └── OpenStreetMap tiles and Nominatim
```

### Request flow

1. The dashboard calls `GET /api/live/dashboard?gateId=...` every 20 seconds.
2. The server reads one cached provider response per interval, normalises it
   into `TrainFeed` and derives a `GatePredictionResponse`.
3. Prediction logic checks freshness, verification and direction before it
   exposes any countdown.
4. A separate scheduled job evaluates alert conditions every 60 seconds.
5. Route requests call OSRM, then measure the returned polylines against the
   selected crossing before presenting alternatives.
6. Every in-app notification is protected by a unique `dedupe_key`.

The server does the polling. The browser never calls the train provider directly,
so credentials never reach the client.

---

## 3. Data modes and honesty rules

`LiveDataMode` has five values.

| Mode | Entry condition | UI banner | Meaning |
|---|---|---|---|
| `UNAVAILABLE` | no provider, or simulation disabled | grey `LIVE TRAIN DATA UNAVAILABLE` | no usable data; nothing is invented |
| `SCHEDULE` | provider returns timetable data only | blue `SCHEDULED DATA ONLY - NOT LIVE TRAIN POSITIONS` | known times, no live position |
| `LIVE` | authorised HTTP JSON provider configured | provider name + source timestamp | movement data exists; ETAs remain estimates |
| `THIRD_PARTY` | `provider-mode=railradar` with an API key | orange `THIRD-PARTY LIVE DATA - NOT OFFICIAL INDIAN RAILWAYS DATA` | values reproduced from an independent provider; never called official |
| `SIMULATION` | simulation enabled (shipped default) | striped orange `SIMULATION MODE - NOT LIVE RAILWAY DATA` | generated demo data, never mixed with live data |

### Verified gate status

A gate is shown as OPEN or CLOSED only when:

- the stored status is fresh (default stale threshold: 120 minutes), and
- `verification_status = VERIFIED`, or the status came from an authorised
  sensor webhook.

Otherwise `verifiedStatus = UNKNOWN`, even when the last reported value was
different. The UI never colours an unverified gate green or red.

### Countdown rules

```text
closing countdown available =
    verifiedStatus == OPEN
    AND provider usable and not stale
    AND a relevant approaching train has a usable ETA
    AND the remaining seconds are positive

opening countdown available =
    verifiedStatus == CLOSED
    AND a relevant train has started or is crossing
    AND the predicted clear time is in the future
    OR simulation provides the same derived relation

otherwise countdown.available = false with a plain-language reason
```

The ETA at the gate is an estimate unless the provider explicitly supplies a live
position. `etaSource` and `estimated` are present in every train object.

Additional safety rules for the simulated barrier:

- the opening target adds `opening-buffer-seconds` after the last train clears;
- if another train is due inside `reopen-gap-seconds`, the barrier is chained to
  that train and is **not** reopened in between;
- countdowns are recomputed from absolute target timestamps on every request, so
  they survive a page refresh or server restart and never go negative.

### Simulation safeguards

Simulation starts only when **both** are true:

```properties
railsafe.live.provider-mode=simulation
railsafe.live.simulation-acknowledged=true
```

The shipped `application.properties` sets both to true so the demo works out of
the box. Override with `RAILSAFE_LIVE_MODE=unavailable` to disable it. Simulation
is never written to `train_snapshots`, and simulated alerts are disabled unless
`railsafe.live.simulation-notifications=true`. Every simulated banner repeats the
mandated label, and real train names are used only as clearly labelled synthetic
sample data.

### Third-party RailRadar provider

RailSafe can read live running status from [RailRadar](https://railradar.in), an
**independent** Indian Railways API. RailRadar is not Indian Railways, IRCTC or
NTES, and RailSafe never labels its values as official. The provider is disabled
unless both the mode and an API key are configured:

```properties
railsafe.live.provider-mode=railradar
railsafe.live.railradar.api-key=rg_...
```

What the adapter does:

- calls `GET /v1/trains/{number}/live?includeCoordinates=true` for the configured
  train numbers in rotation (one request per poll at most);
- reads `currentLocation`, `speedKmh`, `status`, `delayMinutes`, `lastUpdatedAt`,
  `isLive`, `trackingMode` and the route stops;
- matches a train to a RailSafe crossing only when a stop still ahead on its route
  lies within `railsafe.live.match-radius-km` (default 5 km);
- uses `actualArrival`/`scheduledArrival` plus `delayArrival` for the gate ETA and
  falls back to distance ÷ reported speed, marking the result as estimated;
- skips `not-started`, `completed` and `cancelled` runs;
- treats the monthly request quota as a hard budget, spreads the remaining
  requests over the rest of the month, enforces the per-minute limit, and stores
  the counters in `data/railradar-quota.json` so a restart cannot reset them;
- when no request can be made, returns the last successful values and marks the
  feed stale instead of inventing fresher data.

The free RailRadar plan (100 requests/month) is **not** enough for continuous
tracking of ten trains. Set `RAILSAFE_RAILRADAR_MONTHLY_QUOTA` to the real limit
of your plan. Real trains also legitimately produce empty crossings: a gate only
shows a train when the train's route passes within the match radius.

---

## 4. API reference

All endpoints are JSON and same-origin. Session authentication uses the
`JSESSIONID` cookie. Login-first access applies to the whole application: every
live/train endpoint below requires a session. Only login, registration and the
optional shared-secret sensor webhook are public.

### `GET /api/live/provider` — logged in

Returns provider availability, mode, staleness, the user-facing message and the
advertised poll interval.

```json
{
  "configured": false,
  "available": false,
  "stale": true,
  "provider": "Not configured",
  "mode": "UNAVAILABLE",
  "modeLabel": "Unavailable",
  "message": "Live train data unavailable. No authorised railway data provider is configured.",
  "pollIntervalSeconds": 20
}
```

### `GET /api/live/gates` — logged in

Compact crossing list used by the gate table. Includes `reportedStatus`,
`verifiedStatus`, `statusConfidence`, `stale`, `liveDataAvailable`, `dataMode`,
`relevantTrains`, `approachingTrains`, `nextTrainEta`, `nextTrainLabel`,
`expectedCloseAt` and `expectedOpenAt`.

`relevantTrains` counts every train in the gate window. `approachingTrains` counts
only those in `APPROACHING`, `NEAR_GATE` or `CROSSING`, so it matches the
"Approaching trains" summary card. The `expected*At` values are absolute target
timestamps for the countdowns and are `null` when no reliable estimate exists.
They describe the expected barrier state; they are not sensor readings.

### `GET /api/live/dashboard?gateId={id}` — logged in

One polling payload:

```json
{
  "generatedAt": "2026-11-06T05:30:00Z",
  "pollIntervalSeconds": 20,
  "gate": { "...": "GateResponse" },
  "tracking": {
    "available": true,
    "stale": false,
    "mode": "SIMULATION",
    "modeLabel": "SIMULATION MODE - NOT LIVE RAILWAY DATA",
    "message": "...",
    "trains": [ { "...": "LiveTrainResponse" } ]
  },
  "prediction": {
    "reportedStatus": "OPEN",
    "verifiedStatus": "OPEN",
    "statusConfidence": "SIMULATED",
    "liveDataAvailable": true,
    "liveDataStale": false,
    "closing": { "...": "ClosingCountdownResponse" },
    "opening": { "...": "OpeningCountdownResponse" },
    "nextTrain": { "...": "LiveTrainResponse" }
  },
  "provider": { "...": "ProviderStatusResponse" }
}
```

If no provider is configured, `tracking.available=false` and `trains=[]`.

### `GET /api/gates/{id}/trains` — logged in

Flat array of the trains relevant to one gate. Use this when only train data is
needed. Unlike `/api/live/dashboard`, the response is a plain JSON array of
`GateTrainResponse`:

```json
[
  {
    "trainNumber": 12603,
    "trainName": "Hyderabad Chennai Express",
    "gateId": "RS-SYN-108",
    "direction": "NORTH",
    "source": null,
    "destination": null,
    "currentLocation": null,
    "distanceFromGate": 14.0,
    "speed": 42.0,
    "etaMinutes": 20,
    "status": "APPROACHING",
    "gateStatus": "OPEN"
  }
]
```

`gateStatus` is the expected barrier state derived from the train status:
`CROSSING` maps to `CLOSED`, `NEAR_GATE` maps to `CLOSING`, everything else maps
to `OPEN`. It is a prediction, not a sensor reading. `source`, `destination` and
`currentLocation` are populated from provider route data when available and are
`null` for simulation or when the provider does not report them.

### `GET /api/gates/{id}/prediction` — logged in

The verified state, status message, countdowns and next-train prediction without
the provider metadata.

### `GET /api/gates/{id}/timing` — logged in

Compact countdown payload for a crossing. The absolute target timestamps are
authoritative; the `*CountdownSeconds` values are a snapshot at `generatedAt`.

```json
{
  "gateId": 10,
  "gateCode": "RS-SYN-105",
  "gateName": "Kazipet Railway Gate",
  "verifiedStatus": "OPEN",
  "simulation": true,
  "syntheticData": true,
  "generatedAt": "2026-11-07T11:40:00Z",
  "estimatedCloseAt": "2026-11-07T11:44:30Z",
  "closingCountdownSeconds": 270,
  "estimatedOpenAt": null,
  "openingCountdownSeconds": null,
  "approachingTrains": 1,
  "trainsWaiting": 0,
  "nextTrain": { "...": "LiveTrainResponse" },
  "trains": [ { "...": "LiveTrainResponse" } ],
  "preClosingBufferSeconds": 180,
  "crossingDurationSeconds": 240,
  "safetyBufferSeconds": 60,
  "disclaimer": "SIMULATION MODE - NOT LIVE RAILWAY DATA. ..."
}
```

### `GET /api/gates/{id}/alternate-routes` — logged in

Synthetic demo alternatives stored in `route_alternatives` with `synthetic=true`.
The shortest seeded route is marked `recommended=true`. These rows are **not**
verified by OSRM and the response says so.

```json
{
  "available": true,
  "synthetic": true,
  "message": "SYNTHETIC DEMO ROUTES - NOT VERIFIED BY OSRM. ...",
  "gateId": 10,
  "gateName": "Kazipet Railway Gate",
  "gateStatus": "CLOSED",
  "generatedAt": "2026-11-07T11:40:00Z",
  "routes": [
    {
      "id": 31,
      "label": "Synthetic diversion via Kazipet Railway Gate bypass",
      "distanceKm": 5.6,
      "durationMinutes": 12.9,
      "extraDistanceKm": 0.4,
      "recommended": true,
      "available": true,
      "availabilityNote": "Shortest seeded demo alternative. Not verified by a routing engine.",
      "navigationUrl": "https://www.google.com/maps/dir/?api=1&...",
      "mapUrl": "https://www.openstreetmap.org/directions?...",
      "geometry": [ { "lat": 17.9912, "lng": 79.5051 } ]
    }
  ]
}
```

### `GET /api/trains/{trainNumber}` — logged in

Looks up one synthetic train catalogue row and its current simulated gate
assignments. Example: `/api/trains/12727` returns Godavari Express and every
crossing where the demo dataset assigns it.

```json
{
  "trainNumber": "12727",
  "trainName": "Godavari Express",
  "direction": "EAST",
  "zone": "SCR",
  "synthetic": true,
  "source": "Synthetic demo catalogue - SIMULATION MODE, NOT LIVE RAILWAY DATA",
  "assignments": [
    {
      "gateId": 10,
      "gateCode": "RS-SYN-105",
      "gateName": "Kazipet Railway Gate",
      "trainStatus": "APPROACHING",
      "etaAtGate": "2026-11-07T11:45:00Z",
      "distanceKm": 3.5,
      "speedKmph": 60.0,
      "direction": "EAST",
      "observedAt": "2026-11-07T11:40:00Z"
    }
  ]
}
```

### `POST /api/live/sensor/gates/{id}/status` — sensor key

Requires `X-API-Key` equal to `railsafe.live.sensor-api-key`. If the key is not
configured, the endpoint returns **404** so its existence is not advertised.

```json
{
  "status": "CLOSED",
  "gateCode": "RS-DEMO-004",
  "observedAt": "2026-11-06T05:20:00Z",
  "note": "Barrier down, confirmed by trackside sensor"
}
```

- Wrong or missing key: `404`.
- Reading older than 15 minutes: `400`.
- Accepted reading: writes `status_source=SENSOR` and
  `verification_status=VERIFIED`, then creates history and notifications.

### `POST /api/routes/alternatives` — logged in

```json
{
  "gateId": 1,
  "startLat": 17.3800,
  "startLng": 78.5420,
  "destLat": 17.3650,
  "destLng": 78.5600
}
```

Response shape:

```json
{
  "available": true,
  "message": "One verified alternative route avoids the crossing. No second safe route was returned by OSRM.",
  "source": "OSRM / OpenStreetMap",
  "gateId": 1,
  "gateName": "Demo Crossing - Nagole Canal",
  "generatedAt": "2026-11-06T05:31:00Z",
  "original": {
    "distanceKm": 4.3,
    "durationMinutes": 8.4,
    "avoidsGate": false,
    "distanceFromGateMeters": 0.0,
    "geometry": [ { "latitude": 17.38, "longitude": 78.54 } ]
  },
  "alternatives": [
    {
      "id": "alt-1",
      "label": "Fastest alternative",
      "distanceKm": 5.9,
      "durationMinutes": 11.0,
      "extraDistanceKm": 1.6,
      "extraDurationMinutes": 2.6,
      "recommended": true,
      "distanceFromGateMeters": 703.4,
      "geometry": [ { "latitude": 17.38, "longitude": 78.54 } ],
      "navigationUrl": "https://www.openstreetmap.org/directions?...",
      "mapUrl": "https://www.openstreetmap.org/?mlat=..."
    }
  ],
  "recommendedRouteId": "alt-1",
  "directRouteAvoidsGate": false,
  "avoidRadiusMeters": 500.0
}
```

`available=false` means OSRM returned no route that stayed outside the avoid
radius. The `message` tells the user to wait or use an official diversion. No
distance or duration is ever synthesised.

### `GET /api/notifications/preferences` — logged in

```json
{
  "browserEnabled": true,
  "emailEnabled": false,
  "smsEnabled": false,
  "emailChannelConfigured": false,
  "smsChannelConfigured": false
}
```

### `PUT /api/notifications/preferences` — logged in

```json
{
  "browserEnabled": true,
  "emailEnabled": false,
  "smsEnabled": false
}
```

Email/SMS cannot be enabled while the server reports the channel as
unconfigured.

### Existing endpoints

Every original endpoint and response contract is unchanged. The additions to
`GateController` are the GET routes above (`/timing` and `/alternate-routes`).
`TrainController` adds read-only `/api/trains/**`. All of them now require a logged-in
session; role-restricted gate writes also accept SUPER_ADMIN.

---

## 5. Database changes

### New tables

| Table | Purpose |
|---|---|
| `train_master` | 30 synthetic train catalogue rows (real names used as sample labels) |
| `train_tracking` | 50 simulated gate/train assignments with cycle offsets |
| `train_snapshots` | optional audit of authorised provider responses; simulation excluded |
| `route_alternatives` | persisted OSRM options plus seeded synthetic demo routes |
| `notification_preferences` | one row per user for browser/email/SMS channels |

`route_alternatives` gained a `synthetic` boolean. Seeded rows are never mixed
with the OSRM-verified rows returned by `POST /api/routes/alternatives`.

### Synthetic dataset minimums (seeded idempotently)

| Data | Count |
|---|---|
| Crossings | 20 (5 original demo + 15 new, including Medak, Secunderabad, Hyderabad, Nizamabad, Kazipet, Warangal, Lingampally, Begumpet) |
| Train catalogue | 30 |
| Tracking rows | 50 |
| Gate status history rows | 30 new (35 total with the original demo rows) |
| Synthetic alternate routes | 30 (at least one per crossing) |
| Notifications | 50 seeded across the demo accounts |

The explicitly requested sample services are included in the train catalogue:
12702/12701 Hussainsagar, 12760/12759 Charminar, 12727/12728 Godavari,
17011/17012 Intercity and 12713/12714 Satavahana. Twenty further real service
names are used as synthetic labels. None of them is claimed to actually serve a
listed crossing; the gate assignment is fictional demo data.

Required crossing locations in the seed: Medak, Secunderabad, Hyderabad,
Nizamabad, Kazipet, Warangal, Lingampally and Begumpet.

### Existing table changes

`notifications` already stores the type as a string, so the new values need no
column change. New values:

```text
TRAIN_APPROACHING
TRAIN_CROSSING
TRAIN_PASSED
GATE_CLOSING_SOON
GATE_OPENING_SOON
ALTERNATIVE_ROUTE
```

The existing `dedupe_key` unique constraint keeps live alerts idempotent.

### Migration files

| File | Use |
|---|---|
| `database/railsafe_schema.sql` | fresh Oracle install, now contains all eleven tables |
| `database/railsafe_live_migration_oracle.sql` | additive, idempotent migration for an existing Oracle install |
| `database/railsafe_schema_mysql.sql` | optional MySQL 8 install |

The Oracle migration also adds `notifications.dedupe_key` if an older schema did
not have it, backfilling `legacy:<id>` keys before applying the unique
constraint. It creates `train_master` and `train_tracking`, and adds
`route_alternatives.synthetic` to an older route table.

Retention example:

```sql
DELETE FROM train_snapshots
 WHERE recorded_at < SYSTIMESTAMP - INTERVAL '30' DAY;
DELETE FROM route_alternatives
 WHERE generated_at < SYSTIMESTAMP - INTERVAL '30' DAY;
```

---

## 6. Configuration reference

```properties
# Provider (defaults ship the synthetic simulation so the demo runs out of the box)
railsafe.live.provider-mode=simulation
railsafe.live.feed-url=
railsafe.live.feed-api-key=
railsafe.live.feed-api-key-header=X-API-Key
railsafe.live.provider-name=Authorised railway feed
railsafe.live.cache-seconds=20
railsafe.live.feed-stale-seconds=300

# RailRadar third-party provider (independent; not official Indian Railways)
railsafe.live.railradar.base-url=https://api.railradar.in
railsafe.live.railradar.api-key=
railsafe.live.railradar.train-numbers=12702,12701,12760,12759,12727,12728,17011,17012,12713,12714
railsafe.live.railradar.min-request-interval-seconds=8
railsafe.live.railradar.poll-interval-seconds=10
railsafe.live.railradar.monthly-quota=100
railsafe.live.railradar.initial-burst=3
railsafe.live.railradar.quota-file=data/railradar-quota.json

# Simulation (both required). Set either to false to disable the simulation.
railsafe.live.simulation-acknowledged=true
railsafe.live.simulation-notifications=true

# Synthetic demo dataset + timing
railsafe.live.synthetic-data-enabled=true
railsafe.live.simulation-cycle-seconds=1800
railsafe.live.opening-buffer-seconds=60
railsafe.live.reopen-gap-seconds=300

# Sensor webhook
railsafe.live.sensor-api-key=

# Alerts
railsafe.live.alerts-enabled=true
railsafe.live.alert-interval-millis=60000
railsafe.live.alert-initial-delay-millis=15000
railsafe.live.approach-alert-seconds=900
railsafe.live.closing-soon-seconds=300
railsafe.live.opening-soon-seconds=300

# Routing
railsafe.live.osrm-base-url=https://router.project-osrm.org
railsafe.live.route-avoid-radius-meters=150
railsafe.live.route-max-alternatives=3
railsafe.live.route-cache-seconds=60

# Audit retention
railsafe.live.snapshot-retention-hours=72

# Optional delivery
railsafe.notifications.email.enabled=false
railsafe.notifications.sms.enabled=false
railsafe.notifications.sms.webhook-url=
```

All values can be overridden with the matching environment variable shown in
`application.properties`, for example:

| Property | Environment variable | Default |
|---|---|---|
| `railsafe.live.provider-mode` | `RAILSAFE_LIVE_MODE` | `simulation` |
| `railsafe.live.simulation-acknowledged` | `RAILSAFE_SIMULATION_ACKNOWLEDGED` | `true` |
| `railsafe.live.simulation-notifications` | `RAILSAFE_SIMULATION_NOTIFICATIONS` | `true` |
| `railsafe.live.synthetic-data-enabled` | `RAILSAFE_SYNTHETIC_DATA` | `true` |
| `railsafe.live.simulation-cycle-seconds` | `RAILSAFE_SIMULATION_CYCLE_SECONDS` | `1800` |
| `railsafe.live.opening-buffer-seconds` | `RAILSAFE_OPENING_BUFFER_SECONDS` | `60` |
| `railsafe.live.reopen-gap-seconds` | `RAILSAFE_REOPEN_GAP_SECONDS` | `300` |
| `railsafe.live.railradar.api-key` | `RAILSAFE_RAILRADAR_KEY` | blank (provider disabled) |
| `railsafe.live.railradar.train-numbers` | `RAILSAFE_RAILRADAR_TRAINS` | ten sample services |
| `railsafe.live.railradar.monthly-quota` | `RAILSAFE_RAILRADAR_MONTHLY_QUOTA` | `100` |
| `railsafe.live.railradar.poll-interval-seconds` | `RAILSAFE_RAILRADAR_POLL_SECONDS` | `10` |
| `railsafe.live.railradar.initial-burst` | `RAILSAFE_RAILRADAR_INITIAL_BURST` | `3` |

Set `RAILSAFE_LIVE_MODE=unavailable` (and optionally `RAILSAFE_SYNTHETIC_DATA=false`)
to return to the strict, no-data state. Set `RAILSAFE_LIVE_MODE=railradar` plus
`RAILSAFE_RAILRADAR_KEY` to use the independent third-party provider.

Provider credentials should be supplied through environment variables or an
external secret store, not committed to the repository.

---

## 7. Notification matrix

| Event | Type | Preconditions |
|---|---|---|
| Train approaching | `TRAIN_APPROACHING` | usable train, ETA inside approach window, verified status known |
| Gate closing soon | `GATE_CLOSING_SOON` | verified OPEN, available closing countdown inside warning window |
| Gate closed | `GATE_CLOSED` | verified status change to CLOSED |
| Train crossing | `TRAIN_CROSSING` | relevant train reported `CROSSING` at the gate |
| Train passed | `TRAIN_PASSED` | relevant train reported `PASSED` at the gate |
| Gate opening soon | `GATE_OPENING_SOON` | verified CLOSED, available opening countdown inside warning window |
| Gate reopened | `GATE_OPENED` | verified status change to OPEN |
| Alternative route | `ALTERNATIVE_ROUTE` | closed crossing and at least one synthetic demo route stored |

Dedupe keys include the user, gate, event, data window and train number where
relevant. Live event keys also include the day and, in simulation mode, the
current cycle segment, so a repeated scheduler run cannot notify the same user
twice for the same event.

Recipients are users who saved a route containing the gate. If nobody watches the
gate, the demo keeps its original broadcast-to-all behaviour. Optional email/SMS
delivery is attempted only after the in-app notification is saved, so a delivery
failure never blocks history.

---

## 8. Test results

Testing used H2 in-memory with `MODE=Oracle;DATABASE_TO_LOWER=TRUE` on ports
8081–8084. Oracle 19c was used for the original baseline and schema validation.
Public OSRM was used for routing.

| # | Scenario | Expected | Observed |
|---|---|---|---|
| 1 | Strict mode startup (`RAILSAFE_LIVE_MODE=unavailable`) | `UNAVAILABLE`, `available=false`, no trains | Pass: banner and train panel show `LIVE TRAIN DATA UNAVAILABLE` |
| 2 | Simulation mode (shipped default) | `SIMULATION` label and generated trains | Pass: label `SIMULATION MODE - NOT LIVE RAILWAY DATA`; 2–3 trains per gate |
| 3 | Simulation with only one flag | still unavailable | Pass: mode remains `UNAVAILABLE` |
| 4 | Gate verified OPEN with approaching train | closing countdown only | Pass: closing `available=true`; opening `available=false` |
| 5 | Gate verified CLOSED | opening countdown, no closing countdown | Pass: opening countdown derived; closing unavailable |
| 6 | Gate status unverified/stale | no OPEN/CLOSED claim, no countdown | Pass: verified `UNKNOWN`; both countdowns unavailable |
| 7 | Multiple trains | all relevant trains and the nearest next train | Pass: 2 trains listed with direction, ETA, distance and source |
| 8 | Sensor webhook without/wrong key | 404 | Pass |
| 9 | Sensor webhook with stale reading | 400 | Pass |
| 10 | Sensor webhook with fresh reading | `SENSOR` + `VERIFIED` | Pass: prediction changed to `CLOSED / CONFIRMED SENSOR` |
| 11 | Direct route already avoids crossing | `available=true`, no alternatives | Pass: message says no detour needed; no fake route |
| 12 | Closed crossing with a safe detour | verified alternatives only | Pass at 500 m radius: 1 option, 5.9 km, 11.0 min, 703.4 m from crossing |
| 13 | Closed crossing with no safe detour | `available=false`, no invented route | Pass at 2000 m radius: explicit “no verified alternative” message |
| 14 | Alternative route notification | `ALTERNATIVE_ROUTE` for logged-in user | Pass after closing the gate and requesting a verified detour |
| 15 | Anonymous protected API | 401 | Pass |
| 16 | USER calling admin API | 403 | Pass |
| 17 | Notification history + preferences | list, unread count, GET/PUT preferences | Pass for demo user |
| 18 | Existing static site | pages render after login | Pass: home, map and admin pages require a session and work as before |
| 19 | React dashboard | no uncaught console errors | Pass in unavailable and simulation modes |
| 20 | Packaged JAR | `/live/` and assets return 200 after login | Pass: JS and CSS served from the JAR |
| 21 | Anonymous page/API | redirect or 401 | Pass: `/live/`, `/dashboard.html`, `/api/gates` and `/api/live/gates` are blocked |
| 22 | SUPER_ADMIN account management | ADMIN 403, SUPER_ADMIN 200 | Pass: create/edit/deactivate ADMIN only; role escalation rejected |

### Synthetic dataset verification (H2, packaged JAR)

Verified after startup finished seeding:

| Check | Observed |
|---|---|
| `GET /api/gates` | 20 crossings |
| Startup seeder log | 30 trains, 50 new tracking rows, 30 history rows, 30 demo routes, 50 notifications |
| `GET /api/gates/{id}/timing` | close/open targets with countdown seconds; no negative countdown |
| `GET /api/gates/{id}/trains` | 2–3 simulated trains with speed, distance, direction and status |
| `GET /api/gates/{id}/alternate-routes` | at least one synthetic route for every crossing |
| `GET /api/trains/12727` | Godavari Express with simulated gate assignments |
| `GET /api/notifications` as `user@railsafe.demo` | seeded plus scheduler alerts across train, gate and route types |

The first request immediately after launch can race the seeder, so wait for the
"Synthetic demo dataset ready" log line before asserting counts.

### RailRadar adapter verification (offline, no quota spent)

`src/test/java/com/railsafe/live/provider/RailRadarTrainDataProviderTest.java`
replays a documented-shaped payload from a local `HttpServer`, so no API key and no
real request are needed:

```powershell
.\mvnw.cmd test -Dtest=RailRadarTrainDataProviderTest
```

| # | Scenario | Expected | Observed |
|---|---|---|---|
| 1 | Running train passes two of three gates | only the two gates within 5 km are matched; observed arrival is not marked estimated; quota file records `used=1` | Pass |
| 2 | No route times, speed available | derived ETA in the future, `etaEstimated=true` | Pass |
| 3 | No API key | `UNAVAILABLE`, zero HTTP requests | Pass |
| 4 | API key rejected (401) | `UNAVAILABLE`, message names the rejected key, no trains | Pass |
| 5 | Monthly budget already spent | `UNAVAILABLE`, zero HTTP requests | Pass |
| 6 | Budget spent after a success | cached trains still shown, `stale=true`, message says cached data is older, no extra request | Pass |

### Important test note: demo coordinates

The five seeded crossings are fictional and may not lie on a road. With the
default 150 m avoid radius, OSRM often reports that the direct route already
avoids the crossing. That is a correct answer, not a bug. A working detour was
verified with:

```json
{
  "gateId": 1,
  "startLat": 17.3800,
  "startLng": 78.5420,
  "destLat": 17.3650,
  "destLng": 78.5600
}
```

and `--railsafe.live.route-avoid-radius-meters=500`.

---

## 9. Operational notes

### Run the default (simulation) mode

The packaged defaults already enable the synthetic provider and dataset:

```powershell
.\mvnw.cmd clean package
java -jar target\railsafe-1.0.0.jar --spring.profiles.active=h2
```

Open <http://localhost:8080/live/>. Expect 20 crossings, 30 train catalogue rows,
50 tracking rows, 30 demo routes and 50 seeded notifications in the startup log.
Every simulated value carries a **SIMULATION MODE - NOT LIVE RAILWAY DATA** label.

### Run the strict no-data mode

```powershell
$env:RAILSAFE_LIVE_MODE = 'unavailable'
$env:RAILSAFE_SYNTHETIC_DATA = 'false'
java -jar target\railsafe-1.0.0.jar --spring.profiles.active=h2
```

The dashboard then shows **LIVE TRAIN DATA UNAVAILABLE** and no countdown or
train position is invented.

### Run a faster alert demo

```powershell
java -jar target\railsafe-1.0.0.jar `
  --spring.profiles.active=h2 `
  --railsafe.live.alert-initial-delay-millis=5000 `
  --railsafe.live.alert-interval-millis=7000
```

The short alert interval is only for demonstrating notifications; keep 60 s in
normal use.

### Verify the required APIs quickly

```powershell
$gates = Invoke-RestMethod http://localhost:8080/api/gates
$id = ($gates | Where-Object gateCode -eq 'RS-SYN-105').id
Invoke-RestMethod "http://localhost:8080/api/gates/$id"
Invoke-RestMethod "http://localhost:8080/api/gates/$id/trains"
Invoke-RestMethod "http://localhost:8080/api/gates/$id/timing"
Invoke-RestMethod "http://localhost:8080/api/gates/$id/alternate-routes"
Invoke-RestMethod http://localhost:8080/api/trains/12727
```

`GET /api/notifications` requires a signed-in session:

```powershell
$s = New-Object Microsoft.PowerShell.Commands.WebRequestSession
Invoke-RestMethod http://localhost:8080/api/auth/login -Method Post `
  -ContentType 'application/json' -WebSession $s `
  -Body (@{ email='user@railsafe.demo'; password='User@123' } | ConvertTo-Json)
Invoke-RestMethod http://localhost:8080/api/notifications -WebSession $s
```

### Run with a sensor key and a larger avoid radius

```powershell
java -jar target\railsafe-1.0.0.jar `
  --spring.profiles.active=h2 `
  --railsafe.live.sensor-api-key=change-me `
  --railsafe.live.route-avoid-radius-meters=500
```

### Rebuild the React dashboard

```powershell
cd frontend
npm install
npm run build
```

Vite writes the production bundle to `../src/main/resources/static/live/`.
Re-run `mvn package` afterwards so the JAR contains the new assets.

---

## 10. Known limitations

1. **No authorised live provider is bundled.** Real train positions require a
   licensed data agreement and a provider adapter configuration. The shipped
   build defaults to the clearly labelled synthetic simulation; set
   `RAILSAFE_LIVE_MODE=unavailable` for the strict no-data state.
2. **The synthetic dataset is fictional.** Train names are real service labels,
   but every gate assignment, position, speed, ETA and alternate route is demo
   data. It must never be presented as an official railway prediction.
3. **Public OSRM is a demo service.** It can be slow, rate-limited or blocked.
   Production needs a self-hosted or licensed routing instance. Synthetic demo
   routes stored in `route_alternatives` are not OSRM-verified.
4. **Demo coordinates are fictional.** See the test note above.
5. **Browser alerts need an open tab.** There is no service worker or push
   server; background delivery is listed as a future enhancement.
6. **Email/SMS are unconfigured by default.** They need server credentials and a
   per-user opt-in.
7. **Polling, not push.** Dashboard latency is bounded by the 20 s poll and
   alerts by the 60 s scheduler interval.
8. **Sensor integration is a shared-key webhook.** Production needs per-device
   credentials, replay protection and monitoring.
9. **A route check is a snapshot.** A crossing can close after the route was
   calculated; physical signals always win.
10. **Fictional demo data must not be presented as real.** Keep the DEMO and
    SIMULATION labels in every screenshot and slide.

---

## 11. Safety statement

RailSafe provides decision support only. It does not certify that any crossing is
open, that any train is where the screen says, or that any route is safe. The
only authoritative signals are the physical barrier, the railway signal and
instructions from authorised railway staff.

**Never bypass a closed gate. Never drive around a barrier. Never rely on this
demo for a real journey.**
