# RailSafe — Presentation Pack

Everything needed to present the smart railway-crossing assistance upgrade:
abstract, slide-by-slide content, a live demo sequence and a timed 3-minute script.

> Keep the honesty message visible in every version of the presentation:
> this is a hackathon prototype with fictional demo crossings and no live Indian
> Railways feed unless an authorised provider is configured.

---

## 1. Project title

**RailSafe — Smart Railway Crossing Assistance with Live Train Awareness,
Countdowns, Verified Alternative Routes and Alerts**

Short title for slides: **RailSafe: Know Before You Go**

---

## 2. Abstract (150–200 words)

Railway level crossings are a daily bottleneck for commuters, ambulances and
goods traffic. Existing navigation apps know about road congestion but not about
a closed railway gate around the next corner. RailSafe is a full-stack assistance
system that combines gate-status reporting, staff verification, map-based route
planning and a new live-assistance dashboard. The dashboard shows the selected
crossing, the trains relevant to it, their direction and estimated time at the
gate, and a countdown that is displayed only when the underlying data is safe to
use. A closing countdown appears only for a verified-open gate with a reliable
approaching train; an opening countdown appears only for a verified-closed gate.
When no authorised railway feed is configured, RailSafe states
“LIVE TRAIN DATA UNAVAILABLE” instead of inventing a train. Alternative routes
come from OSRM and are rejected unless their geometry stays outside a configurable
radius around the crossing. Server-side polling raises alerts for an approaching
train, a gate closing soon, closure, reopening soon, reopening and a verified
diversion. The original RailSafe pages, session login and demo data remain
intact, so the upgrade is additive rather than a rewrite.

---

## 3. The problem

- A commuter cannot see that the next crossing is closed until they are already
  in the queue.
- Ambulances and fire services lose critical minutes at closed gates.
- Reported status is often stale, and an unverified “OPEN” is dangerous.
- Existing route planners do not treat a railway crossing as a live constraint.
- A prediction shown without context can be worse than no prediction.

### Design principle

> **It is better to say “I do not know” than to show a confident lie.**

Every screen distinguishes VERIFIED, ESTIMATED, SCHEDULED and SIMULATION data.

---

## 4. What is new

| Area | Before | After |
|---|---|---|
| Gate status | reported / staff verified | same, plus sensor webhook and stricter display rules |
| Train awareness | none | provider adapter, multiple trains, direction, ETA, status |
| Countdowns | none | safety-gated closing and opening countdowns |
| Next train | none | nearest relevant train with estimate labels |
| Routes | OSRM alternatives with a 150 m warning | server-verified detours that must avoid the crossing |
| Alerts | status changes and reports | six live events with dedupe and preferences |
| UI | Bootstrap static pages | additive React dashboard at `/live/` |
| Data honesty | DEMO labels | explicit UNAVAILABLE / SCHEDULE / LIVE / SIMULATION modes |

The existing website is preserved. Navigation now includes a **Live** link, and
every original page continues to work.

---

## 5. Architecture slide

```text
Browser
  ├── existing static pages (Bootstrap + vanilla JS)
  └── React dashboard at /live/ (20 s polling)

Spring Boot
  ├── existing controllers and services (unchanged)
  ├── live dashboard, prediction and sensor APIs
  ├── OSRM route verification and audit
  ├── scheduled alert evaluation
  └── notification history + optional email/SMS

Data and services
  ├── Oracle 19c (default) / H2 (demo) / MySQL (optional)
  ├── authorised HTTP JSON train provider (configured, not bundled)
  ├── OSRM + OpenStreetMap
  └── gate sensor webhook (shared key, freshness checked)
```

Talking point: the train-provider credential stays on the server. The browser
only sees normalised RailSafe JSON.

---

## 6. Safety rules slide

### Gate status

- OPEN or CLOSED only when fresh and verified.
- Otherwise UNKNOWN, with the last reported value shown separately.
- Stale threshold defaults to 120 minutes.

### Closing countdown

- Gate must be verified OPEN.
- Train data must be available and not stale.
- A relevant approaching train must have a usable ETA.

### Opening countdown

- Gate must be verified CLOSED.
- A current crossing or predicted clear time must exist.

### Simulation

- Two explicit flags are required.
- Always labelled `SIMULATION MODE - NOT LIVE RAILWAY DATA`.
- Never mixed with live data and never written to the live audit table.

---

## 7. Alternative-route slide

1. Ask OSRM for the direct route and alternatives.
2. Decode every polyline.
3. Measure the distance from the crossing to every segment.
4. Reject any route that enters the avoid radius (default 150 m).
5. Present only verified options, with OSRM distance and duration.
6. If nothing safe exists, say so. Never invent a diversion.

Include a screenshot of the route panel showing:

- red “direct route blocked” state,
- blue alternative cards with `distanceFromGateMeters`,
- the map with the crossing and route geometry,
- the “no verified alternative” state as a contrasting example.

---

## 8. Alerts slide

| Alert | When |
|---|---|
| Train approaching | ETA enters the approach window |
| Gate closing soon | verified OPEN and closing countdown is short |
| Gate closed | verified status becomes CLOSED |
| Gate opening soon | verified CLOSED and opening countdown is short |
| Gate reopened | verified status becomes OPEN |
| Alternative route | a closed crossing has a verified detour |

Delivery: in-app history always, browser permission optional, email/SMS only
after server configuration and user opt-in. Dedupe keys prevent repeat alerts.

---

## 9. Slide-by-slide outline

### Slide 1 — Title

- RailSafe: Know Before You Go
- Smart railway-crossing assistance
- Team name, college, hackathon, date
- One line: “Verified status, honest predictions, verified diversions.”

### Slide 2 — Problem

- Queue at a closed gate, no warning upstream.
- Emergency vehicles lose minutes.
- Unverified reports can be dangerous.
- Navigation ignores railway crossings.

### Slide 3 — Insight

- A confident wrong answer is worse than “unavailable”.
- Show the source, the age and the confidence of every number.

### Slide 4 — Solution

- One dashboard per crossing.
- Verified gate state, trains, countdowns, routes, alerts.
- Additive upgrade to an existing working app.

### Slide 5 — Live dashboard screenshot

- Provider banner.
- Gate panel with VERIFIED OPEN / CLOSED / UNKNOWN.
- Closing and opening countdown cards.
- Multiple trains with direction, ETA, status and source.
- Next-train card.
- Label: `SIMULATION MODE - NOT LIVE RAILWAY DATA` if simulated.

### Slide 6 — Honesty model

- Four modes: UNAVAILABLE, SCHEDULE, LIVE, SIMULATION.
- Gate verification and stale rules.
- Countdown preconditions.
- “Never invent a position.”

### Slide 7 — Architecture

- React dashboard, Spring Boot APIs, scheduled alerts.
- Oracle/H2/MySQL, OSRM, provider adapter, sensor webhook.
- Server-side credential handling.

### Slide 8 — Alternative routes

- OSRM only.
- Polyline-to-crossing distance check.
- Reject, never synthesise.
- “No safe detour” is a valid result.

### Slide 9 — Alerts

- Six event types.
- Dedupe.
- Browser, email, SMS options.
- Watchers-first delivery.

### Slide 10 — Demo

- Default unavailable mode.
- Simulation mode.
- Sensor verification.
- Route verification.
- Notifications.

### Slide 11 — Testing

- 20 scenarios, including permission, stale sensor, no-route and reopen.
- Oracle baseline preserved.
- Packaged JAR serves React assets.

### Slide 12 — Impact

- Commuters: leave earlier or choose another route.
- Emergency services: pre-trip awareness.
- Railways: a channel for verified public information.
- Municipalities: crossing congestion data.

### Slide 13 — Limitations and future

- No bundled authorised feed.
- Public OSRM demo.
- Fictional crossings.
- Next: official data agreement, push notifications, self-hosted routing,
  predictive delay models, multilingual UI.

### Slide 14 — Safety close

- Assistance, not certification.
- Physical barriers and staff always win.
- “Never bypass a closed gate.”

### Slide 15 — Thank you / Q&A

- Repository path.
- Demo URL: `http://localhost:8080/live/`.
- Contact details.

---

## 10. Live demo sequence (6–8 minutes)

### Prepare before judging

```powershell
# Terminal 1 — simulation with fast alerts
java -jar target\railsafe-1.0.0.jar `
  --spring.profiles.active=h2,simulation `
  --railsafe.live.simulation-notifications=true `
  --railsafe.live.alert-initial-delay-millis=5000 `
  --railsafe.live.alert-interval-millis=7000

# Terminal 2 — sensor and route demo
java -jar target\railsafe-1.0.0.jar `
  --spring.profiles.active=h2 `
  --server.port=8081 `
  --railsafe.live.sensor-api-key=demo-sensor-key `
  --railsafe.live.route-avoid-radius-meters=500
```

Use `http://localhost:8080/live/` for the main demo. Keep the default
`application.properties` untouched so the honest unavailable state is still
reproducible.

### Demo steps

1. **Open the existing home page.** Show that the original project still works.
   Point out the new **Live** navigation item.
2. **Open `/live/` in default mode.** Read the grey banner aloud:
   “LIVE TRAIN DATA UNAVAILABLE”. Show that no train, ETA or countdown is
   invented.
3. **Start simulation.** Refresh. Point at the striped orange banner and read
   `SIMULATION MODE - NOT LIVE RAILWAY DATA`.
4. **Select a crossing with a train.** Show name, number, direction, ETA and
   status. Explain that ETA source is visible.
5. **Show the countdown cards.** Closing appears for a verified OPEN gate with a
   reliable train; opening explains why it is unavailable. Switch to a CLOSED
   crossing to show the opening countdown.
6. **Show the honesty warning.** Point out that an OPEN status is still reported
   information, not a guarantee.
7. **Verify a gate with the sensor webhook.** POST a fresh reading with
   `X-API-Key`; refresh and show `SENSOR / VERIFIED` and the changed prediction.
8. **Open the route panel.** Enter coordinates that straddle the crossing, or
   use the prepared request. Show the verified alternative, its distance from the
   crossing and its OSRM metrics.
9. **Show a no-route result.** Explain that RailSafe refuses to invent a detour.
10. **Log in as the demo user.** Show alert history and preferences. Mention
    that email/SMS are disabled until the server is configured.
11. **Close with safety.** “We are not replacing the barrier. We are helping
    people decide before they arrive.”

### Demo fallbacks

- If OSRM is slow or blocked, use the saved response screenshot and explain the
  verification rule.
- If the browser blocks notifications, show the in-app history and the blocked
  permission message; this is honest and expected.
- If a provider is unavailable, demonstrate that the UI says so. That is a
  feature, not a failure.

---

## 11. Three-minute script

### 0:00–0:20 — Hook

> “Every day, thousands of commuters turn a corner and discover a railway gate is
> closed. The queue starts before anyone knows why. Navigation apps know about
> traffic, but they do not know about the train.”

### 0:20–0:45 — Problem and principle

> “RailSafe is an assistance layer for railway crossings. Our most important
> design rule is simple: it is better to say ‘I do not know’ than to show a
> confident lie. That rule shapes every screen.”

### 0:45–1:15 — Dashboard

> “Here is the live dashboard. For the selected crossing we show the verified
> status, every relevant train, its direction, its estimated time at the gate and
> whether that estimate comes from a live feed, a schedule or a simulation. The
> closing countdown appears only when the gate is verified open and the train data
> is reliable. The opening countdown appears only when the gate is verified
> closed.”

### 1:15–1:35 — Unavailable and simulation

> “With no authorised provider, the dashboard says ‘LIVE TRAIN DATA UNAVAILABLE’
> and shows no trains at all. For a demo we can enable simulation, but it requires
> two explicit switches and is always labelled ‘SIMULATION MODE — NOT LIVE
> RAILWAY DATA’.”

### 1:35–2:05 — Routes

> “If the crossing is closed, RailSafe asks OSRM for alternatives. Then it does
> something a normal map does not: it measures every route against the crossing
> and rejects anything that comes too close. If no safe detour exists, it says so.
> It never invents a distance or a route.”

### 2:05–2:30 — Alerts

> “Server-side polling raises six alerts: train approaching, gate closing soon,
> gate closed, gate opening soon, gate reopened and a verified alternative route.
> Duplicate alerts are suppressed, and each user can choose browser, email or SMS
> delivery. Email and SMS are off until the operator configures them.”

### 2:30–2:50 — Engineering

> “The original Spring Boot site, session login and Oracle schema still work. The
> new layer adds three tables, REST endpoints, a scheduled evaluator and a React
> dashboard. The train-provider key never reaches the browser.”

### 2:50–3:00 — Close

> “RailSafe does not replace a barrier or a signal. It helps a commuter, an
> ambulance or a bus driver make a better decision before they arrive. Thank you.”

---

## 12. Judge-facing talking points

- **Safety-first engineering:** countdown preconditions, stale checks, verified
  status, explicit unavailable state.
- **No hallucinated data:** no invented trains, positions, distances or routes.
- **Reproducible demo:** simulation needs two flags and is always labelled.
- **Real verification:** OSRM polylines are checked against the crossing.
- **Additive upgrade:** existing pages, auth and schema remain functional.
- **Operational realism:** sensor freshness, alert dedupe, optional delivery
  channels, audit tables and retention guidance.

### Likely judge questions and one-line answers

| Question | Answer |
|---|---|
| Is this live Indian Railways data? | No. The default mode is unavailable; live data needs an authorised provider. |
| What if the train is late? | Every ETA is labelled an estimate; the countdown is assistance, not a guarantee. |
| How do you stop false alarms? | Verification, freshness checks, mode checks and a unique dedupe key. |
| Why not use WebSockets? | Polling is sufficient for the demo and keeps deployment simple; push is future work. |
| What happens if OSRM fails? | RailSafe reports that no verified alternative is available and does not invent one. |
| How is simulation prevented from leaking into live data? | Two flags, a labelled banner, `(DEMO)` train names and exclusion from snapshots and alert defaults. |
| What is the biggest limitation? | No bundled authorised feed and fictional demo coordinates; both are stated openly. |

---

## 13. Impact and future scope

### Impact

- Commuters can leave earlier or choose a verified diversion.
- Emergency services get pre-trip awareness instead of discovering a queue.
- Staff gain a verified public channel rather than uncontrolled rumours.
- Cities can study crossing congestion once aggregate data is available.

### Future scope

- Official data agreement and a production provider adapter.
- Self-hosted OSRM and map tiles.
- Push notifications with a service worker.
- Historical delay learning, clearly labelled as prediction.
- Real crossing coordinates and field calibration.
- Per-device sensor authentication and replay protection.
- Multilingual UI and a mobile PWA.
- Flyway migrations, Testcontainers and load testing.

---

## 14. Submission checklist

- [ ] `README.md` updated and links to this pack.
- [ ] `docs/LIVE_SYSTEM.md`, `docs/PRESENTATION.md`, `docs/VIVA.md` present.
- [ ] `.\mvnw.cmd clean package` succeeds.
- [ ] `http://localhost:8080/live/` loads from the packaged JAR.
- [ ] Default mode shows `LIVE TRAIN DATA UNAVAILABLE`.
- [ ] Simulation mode shows the mandated label.
- [ ] Existing pages and demo logins still work.
- [ ] Screenshots have DEMO/SIMULATION labels visible.
- [ ] Safety statement appears in the deck and the live demo.
