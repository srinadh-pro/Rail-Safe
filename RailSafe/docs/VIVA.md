# RailSafe — Viva Preparation

Technical questions and answers for a project viva, grouped by topic. Answers are
written to be spoken aloud. Replace bracketed placeholders with your own details.

> Core answer to remember: **RailSafe is decision support, not a safety
> certification. It never invents data. When it does not know, it says so.**

---

## 1. Project overview

### Q1. What problem does RailSafe solve?

Railway level crossings create unpredictable queues, and existing navigation apps
do not treat a crossing as a live constraint. RailSafe shows verified crossing
status, the trains relevant to that crossing, safety-gated countdowns, verified
alternative routes and alerts, so a commuter can decide before arriving.

### Q2. What was your original project and what did you add?

The original project was a Spring Boot + Bootstrap railway-gate reporting system
with session login, staff verification, reports, saved routes, maps and alerts.
The upgrade adds a React live dashboard, a provider abstraction, train tracking,
countdown prediction, OSRM route verification, live alert types, notification
preferences and new storage tables. The original pages and endpoints were kept.

### Q3. Why is the project called additive?

Because no existing endpoint contract was broken. New controllers and services
were added, the schema gained three tables, and the old static pages still load
and authenticate. The React bundle is additional static content under `/live/`.

### Q4. What is the most important design principle?

Honesty. A confident wrong answer is worse than an explicit “unavailable”.
RailSafe distinguishes verified, estimated, scheduled and simulated data, and it
shows `LIVE TRAIN DATA UNAVAILABLE` when no authorised feed exists.

---

## 2. Architecture

### Q5. Describe the architecture.

A React/Vite dashboard polls Spring Boot REST endpoints. The server reads a cached
train feed, normalises it, applies safety rules and returns one dashboard payload.
A scheduled job evaluates alerts. Routing calls OSRM and verifies each polyline
against the crossing. Oracle 19c is the default database; H2 is used for demos and
MySQL is an optional profile.

### Q6. Why did you choose polling instead of WebSockets?

The data changes on the order of tens of seconds, and polling is simpler to
deploy, debug and demo. Dashboard polling is 20 seconds and server alert
evaluation is 60 seconds. WebSocket/SSE push is listed as future work.

### Q7. Why is polling done on the server?

Train-provider credentials must never reach the browser. The server also needs to
apply freshness, verification and dedupe rules consistently for every client.

### Q8. What is the role of `LiveDashboardService`?

It composes the gate, tracking, prediction and provider metadata into one payload,
and builds the compact gate list for the selector. It is transactional and
read-only so lazy relationships can be safely mapped.

### Q9. What is the role of `GatePredictionService`?

It decides the verified status, whether data is usable, the closing and opening
countdowns and the next train. All safety rules for what may be displayed live
there.

### Q10. What is the role of `LiveAlertScheduler`?

It runs on a fixed delay, reads the current feed, captures an audit snapshot for
real provider data, evaluates each gate and creates deduplicated notifications.
It is annotated `@Transactional` because it accesses lazy `User` proxies with
open-in-view disabled.

### Q11. Why did you add `@Transactional` to the scheduler?

The first version failed with `Could not initialize proxy [User#2] - no session`
because the scheduled method ran outside a transaction and open-in-view was
disabled. Making the method transactional keeps the persistence context open for
the whole evaluation.

---

## 3. Safety and prediction logic

### Q12. When is a gate shown as OPEN or CLOSED?

Only when its stored status is fresh and verified, or when it came from an
authorised sensor. Otherwise the verified status is `UNKNOWN`, even if the last
reported value was different.

### Q13. Why not simply show the last reported status?

A report can be old or wrong. Showing an unverified OPEN in green could encourage
someone to cross a closed gate. Separate fields (`reportedStatus` and
`verifiedStatus`) let the UI be honest without losing history.

### Q14. When is the closing countdown shown?

Only when the gate is verified OPEN, the provider data is available and not stale,
a relevant approaching train has a usable ETA, and the remaining time is positive.

### Q15. When is the opening countdown shown?

Only when the gate is verified CLOSED and a crossing or predicted clear time is
available. If the gate is OPEN, the UI explicitly says the countdown is not
applicable.

### Q16. How do you decide a train is relevant to a crossing?

The provider feed is normalised with gate codes or geographic proximity. A train
is relevant when it is travelling toward the gate and its projected path or
assigned crossing matches. Direction and status (`APPROACHING`, `NEAR_GATE`,
`CROSSING`, `PASSED`) are part of the model.

### Q17. What does `etaSource` mean?

It records where the ETA came from: live provider position, schedule, or
simulation. The UI also exposes an `estimated` flag. This prevents a scheduled
time from being presented as a live measurement.

### Q18. What happens when the feed is stale?

`stale=true` means data exists but must not be trusted. The countdown and alerts
are suppressed, and the UI warns the user instead of showing a confident number.

### Q19. How do you stop an unverified gate from being coloured green?

The API supplies `verifiedStatus`, and the React dashboard colours and labels that
field, not the raw stored status. `GatePredictionService` returns `UNKNOWN` when
verification or freshness fails.

### Q20. What is the difference between reported, verified and displayed status?

- Reported: the last value someone submitted or staff recorded.
- Verified: the value accepted after freshness and source checks.
- Displayed: what the UI shows, which is `UNKNOWN` when the verified value is not
  safe to present.

---

## 4. Simulation and data honesty

### Q21. How does simulation work?

Two conditions are required: `railsafe.live.provider-mode=simulation` and
`railsafe.live.simulation-acknowledged=true`. The provider then generates trains
and transitions for demonstration. The dashboard always shows
`SIMULATION MODE - NOT LIVE RAILWAY DATA`.

### Q22. How do you prevent simulation from mixing with live data?

The provider mode is exclusive, the label is mandatory in the UI, simulated train
names contain `(DEMO)`, snapshots exclude simulation, and simulated alerts are off
unless explicitly enabled.

### Q23. Why require two flags for simulation?

It is a deliberate speed bump. A single typo or environment variable should not
silently turn a safety-related demo into fake live data.

### Q24. Could a user mistake simulation for live data?

The banner is large, striped and uses the exact required wording. Every simulated
alert also contains “SIMULATED”. The demo script tells the presenter to point at
the banner.

---

## 5. Database and JPA

### Q25. What tables did you add?

`train_snapshots` for optional audit of real provider responses,
`route_alternatives` for verified OSRM options, and
`notification_preferences` for per-user delivery channels.

### Q26. Why does `train_snapshots` have no foreign key to gates?

It is an audit log keyed by provider, train number and gate code. Keeping it
independent means audit history survives gate deletion or renaming.

### Q27. How do you prevent duplicate notifications?

The `notifications` table has a unique `dedupe_key`. The service checks it before
inserting, and the database constraint is the final protection.

### Q28. Why does `NotificationType` need no migration?

The type is stored as a string (`@Enumerated(EnumType.STRING)`), so adding enum
values does not change the column definition. New values are documented in the
migration notes.

### Q29. Why is `ddl-auto=update` acceptable here?

It keeps the hackathon demo easy to run. For production, the project documents
Flyway/Liquibase as the correct approach and provides explicit Oracle and MySQL
scripts.

### Q30. What is `open-in-view` and why did you disable it?

Spring's open-in-view keeps an EntityManager open during view rendering, which can
hide lazy-loading problems. RailSafe disables it so services map entities to DTOs
inside explicit transactions, which is better practice.

### Q31. How are entities protected from leaking to the browser?

Controllers return DTO records, not JPA entities. Password hashes and internal
relationships never appear in API responses.

### Q32. Why are there both Oracle and MySQL scripts?

Oracle 19c is the default and verified database. MySQL is an optional profile for
teams without Oracle. Having both scripts makes the schema portable and explicit.

### Q33. What retention policy do you recommend?

Delete snapshots and route alternatives older than 30 days with a scheduled
database job. The SQL is included in the migration file.

---

## 6. Routing and geospatial logic

### Q34. How are alternative routes produced?

The server calls OSRM with `alternatives=true` and full GeoJSON geometry. It
decodes the polyline, measures the distance from the crossing to every segment,
and keeps only routes whose closest approach is outside the configured avoid
radius.

### Q35. Why not trust OSRM's alternatives directly?

OSRM returns fast routes, not routes that avoid a specific point. A route can pass
straight through the crossing. RailSafe verifies the geometry itself.

### Q36. How is point-to-segment distance calculated?

Coordinates are projected into local metres around the query point, then the
distance from the point to each segment is computed with a clamped projection.
The minimum across all segments is the closest approach.

### Q37. What happens if no safe route exists?

The API returns `available=false` with a message telling the user to wait or use an
official diversion. It does not fabricate a route, distance or time.

### Q38. Why did the default demo sometimes show no alternatives?

The five crossings use fictional coordinates that may not lie on a road. With the
default 150 m radius, OSRM often says the direct route already avoids the
crossing. That is a correct result. A detour was verified at a 500 m radius with a
documented coordinate pair.

### Q39. Is a verified route permanently safe?

No. It is a snapshot at calculation time. A gate can close later, and road
conditions change. The UI says this explicitly.

### Q40. Why use OSRM instead of computing a straight-line detour?

A straight-line detour would be invented data. OSRM provides real road geometry,
distance and duration, and RailSafe then verifies it rather than trusting it.

---

## 7. Notifications

### Q41. Which events generate alerts?

Train approaching, gate closing soon, gate closed, gate opening soon, gate
reopened and alternative route available.

### Q42. Who receives an alert?

Users who saved a route containing the gate. If nobody watches it, the demo keeps
the original broadcast-to-all behaviour. A production system would use explicit
subscriptions.

### Q43. How are browser notifications implemented?

The dashboard uses the Notification API after user permission. Browser alerts only
work while a RailSafe page is open; there is no service worker or push server.

### Q44. What about email and SMS?

They are optional adapters disabled by default. They require server configuration
(`spring.mail.*` or an SMS webhook) and per-user opt-in. Failures are logged and do
not block the in-app notification.

### Q45. Why not send email/SMS by default?

Cost, privacy, deliverability and consent. A demo should not send real messages
without explicit configuration and opt-in.

### Q46. How is alert spam prevented?

Event preconditions, freshness checks, a warning window, a unique dedupe key and
a fixed scheduler interval.

---

## 8. Security

### Q47. How is authentication implemented?

Session-based login with a BCrypt password hash and an `HttpOnly` `JSESSIONID`
cookie. Spring Security enforces roles on the backend.

### Q48. How is the sensor webhook protected?

It requires an `X-API-Key` header matching the configured key. If no key is
configured, the endpoint returns 404. Readings older than 15 minutes are rejected.

### Q49. What are the security limitations?

Token-based CSRF is enabled and failed logins trigger a temporary per-client
lockout (HTTP 429). Remaining gaps: no registration rate limit at the edge, no
email verification or password reset, the lockout is per-instance (not shared
across nodes), and the sensor uses a shared secret. HTTPS/Secure cookies/HSTS
support exists but must be activated with a TLS reverse proxy before public
deployment.

### Q50. How are provider credentials handled?

They live in server configuration or environment variables and are used only by
the backend. The browser receives normalised JSON, never the credential.

### Q51. What would you change before production?

Deploy the supplied TLS reverse-proxy config with real certificates and the
HTTPS env flags, add edge rate limiting, move the local env file into a managed
secret store, adopt versioned database migrations, add backups/monitoring, use
per-device sensor credentials, and obtain an authorised data agreement. CSRF
tokens, audit logging, login lockout and secrets externalization are already in
place.

---

## 9. Frontend

### Q52. Why React for the new dashboard?

The dashboard is stateful: selected crossing, polling, countdown ticks, route form,
map and notification history. React keeps those states predictable. The existing
Bootstrap pages remain vanilla JavaScript to avoid an unnecessary rewrite.

### Q53. How does the dashboard avoid claiming live data?

It renders the provider banner and `verifiedStatus` from the API. The unavailable
state is a first-class UI state, not an error toast.

### Q54. How are countdowns updated without server load?

The server sends a target timestamp. The browser recalculates the display every
second locally, while the authoritative data refreshes on the 20-second poll.

### Q55. How is the build deployed?

Vite writes the production bundle to
`src/main/resources/static/live/`. It is packaged inside the Spring Boot JAR, so
Node.js is not needed at runtime.

### Q56. How does `/live` resolve to the React app?

`LivePageController` forwards `/live` and `/live/` to
`forward:/live/index.html`, because Spring's static handler does not resolve a
subdirectory index automatically. Existing static pages are unaffected.

### Q57. How is accessibility handled?

Semantic headings and regions, a skip link, visible labels, `aria-live` loading
status, `aria-current` for the active gate, keyboard-focusable controls and a
reduced-motion media query.

---

## 10. Testing

### Q58. How did you test the backend?

With H2 in Oracle mode on separate ports for unavailable, simulation, sensor and
alert scenarios. PowerShell scripts exercised login, gates, trains, prediction,
sensor ingest, routing, notifications and preferences. The original Oracle
baseline was verified separately.

### Q59. How did you test the UI?

The in-app browser loaded `/live/` in unavailable and simulation modes. The DOM
was inspected for the mandated labels, the train table, countdown states and
notification history. No uncaught console errors appeared; the only 401 was the
expected `/api/auth/me` check on a public page.

### Q60. What were the most important failure tests?

Wrong and missing sensor keys, stale sensor readings, anonymous API access, a USER
calling an admin endpoint, a direct route that already avoids the crossing, no safe
detour, and a provider that is unavailable.

### Q61. What did the tests prove?

That the system refuses to invent data, that permissions are enforced by the
backend, that simulation is clearly labelled, and that the existing site still
works after the upgrade.

### Q62. What is not covered by automated tests?

There is no full integration-test suite or browser automation committed to the
repository. Testing was scripted and manual for the hackathon. Testcontainers and
Playwright are listed as future work.

---

## 11. Design decisions and trade-offs

### Q63. Why keep the original static frontend instead of rewriting everything in React?

The requirement was to preserve existing pages and behaviour. Rewriting would add
risk without improving the demo. The new dashboard is isolated at `/live/`, and
the old pages keep working.

### Q64. Why Oracle as the default and H2 only for tests?

The original project and its verification target Oracle 19c. H2 makes the demo
portable without changing JPA mappings. MySQL is a documented optional profile.

### Q65. Why server-side alert evaluation instead of the browser?

Alerts must work for logged-in users even when their dashboard is closed, and the
rules must be consistent. Server-side evaluation also allows dedupe and optional
email/SMS delivery.

### Q66. Why not use the train provider directly from the browser?

Credential exposure, CORS, inconsistent payloads, rate limiting and the need for
server-side safety rules.

### Q67. What is the biggest technical risk?

Depending on external services (provider, OSRM, map tiles). The app handles this
by degrading honestly: unavailable data, no route, or a clear error message.

### Q68. What would you do with one more month?

Add a production provider adapter with an authorised agreement, self-host OSRM,
add push notifications, build an automated test suite, and validate against real
crossing coordinates with field sensors.

---

## 12. Rapid-fire answers

| Question | Answer |
|---|---|
| Framework | Spring Boot 3.5, Java 17 target |
| Default DB | Oracle 19c |
| Demo DB | H2 in Oracle compatibility mode |
| Optional DB | MySQL 8 |
| Frontend | React 18 + Vite; original pages are Bootstrap |
| Auth | Session + BCrypt + `HttpOnly` cookie |
| Polling | 20 s dashboard, 60 s alerts |
| Routing | OSRM public demo, verified server-side |
| Default mode | `UNAVAILABLE` |
| Simulation opt-ins | mode + acknowledgement |
| Verified status | fresh + verified, else `UNKNOWN` |
| Closing countdown | verified OPEN + reliable approaching train |
| Opening countdown | verified CLOSED |
| Duplicate alerts | unique `dedupe_key` |
| New tables | `train_snapshots`, `route_alternatives`, `notification_preferences` |
| Packaged dashboard | `src/main/resources/static/live/` |
| Safety rule | never bypass a closed gate |

---

## 13. Closing statement for the viva

> “RailSafe is deliberately conservative. It shows a train only when an
> authorised provider exists, shows OPEN or CLOSED only when the status is
> verified, and shows a countdown only when the data supports it. Alternative
> routes are measured against the crossing, not assumed to avoid it. The result is
> an assistance system that is useful in a demo and honest about its limits in the
> real world. Physical barriers and railway staff always take precedence.”
