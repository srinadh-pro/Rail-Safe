# RailSafe Delivery Report — Login-first Roles, Gate Staff Management, Manual Verification, Demo Simulation and Alternate Routing

**Date:** 2026-11-19  
**Application URL:** `http://localhost:8080`  
**Scope:** Incremental extension of the existing RailSafe application. Existing pages, routes, authentication, records and UI patterns were preserved; no replacement site was created.

---

## 1. Delivered capability

### Login-first access and four roles
- Roles: `CUSTOMER`, `STAFF`, `ADMIN`, `SUPER_ADMIN`; legacy `USER` retained with customer permissions.
- All application pages and data APIs require an authenticated session. Unauthenticated API calls return `401`; protected pages redirect to `/login.html?next=...`.
- New registrations default to `CUSTOMER`. There is no public privileged registration path.
- Login page displays four demo role cards (role, username/email, password, one-click login) using the public demo-account endpoint available only in `h2`/`dev` or when explicitly enabled.
- Role home pages:
  - `CUSTOMER`/`USER` → `/dashboard.html`
  - `STAFF` → `/staff.html`
  - `ADMIN`/`SUPER_ADMIN` → `/admin.html`
  - `SUPER_ADMIN` → `/super-admin.html`
- Manual URL access to another role's page is denied server-side and redirected to that role's permitted home, with no redirect loops.

### Live database-derived dashboard
- `GET /api/live/counts` returns non-negative, deduplicated totals for total/open/closed/closing/opening/maintenance/awaiting gates, approaching trains and active crossings.
- Staff-scoped counts use active `staff_assignments`; Admin/Super Admin/Customer use all-gate scope.
- SSE stream `GET /api/live/stream` emits `connected`, `counts`, `gate-status` and `heartbeat` events. It broadcasts database-derived snapshots and gate-change signals only; it never generates simulated status values.
- The `LIVE` badge is shown only when `liveDataAvailable=true`. Otherwise the UI reads **Live data unavailable** with an explanatory message. The simulation feed never enables it.
- Client disconnects no longer produce noisy stack traces; `IOException` during async SSE dispatch is handled quietly.

### Visible manual gate verification
- Staff and Admin gate tables now show a clear **Verify / Set status** button. Staff can verify only their assigned crossings; Admin/Super Admin can verify any operational gate.
- `PUT /api/gates/{id}/verification` accepts `{"status":"OPEN"|"CLOSED"|..., "verificationStatus":"VERIFIED"|"UNVERIFIED", "note":"..."}`. The source is always forced to `STAFF`, `DEMO` is rejected, and `VERIFIED` requires an explicitly observed status.
- The modal prefills the current observable status and verification state, supports `OPEN`, `CLOSED`, `CLOSING`, `OPENING` and `MAINTENANCE`, and submits to the dedicated verification endpoint.
- Verification-only edits that change the public status now create an in-app notification and publish a `gate-status` SSE event followed by fresh DB-derived counts. Repeated verification of the same public status creates no duplicate notification.
- Withdrawing verification downgrades `dataClassification` from `VERIFIED_LIVE` to `VERIFIED_REFERENCE`; public status returns to **Awaiting Verification**.
- Smoke test: verifying one OSM reference gate as `CLOSED` changed counts from `153 awaiting / 0 closed` to `152 awaiting / 1 closed`, with `status=STAFF`, `verificationStatus=VERIFIED` and unread notifications `2 → 3`. Repeating the same verification left counts and unread notifications unchanged.

### Gate staff management and live gate status
- Dedicated **Railway staff sign-in** at `/staff-login.html` and `POST /api/auth/staff-login`. Admin, Super Admin and commuter credentials are rejected by this endpoint with HTTP 403 before a session is created; commuter `login.html` no longer advertises the STAFF demo card.
- Staff accounts use the existing `User.role=STAFF`, `staff_assignments` and seeded demo account `staff@railsafe.demo / Staff@123`. No separate Staff entity or demo-only hidden account was introduced.
- Admin/Super Admin can create, edit, activate/deactivate, assign and reassign STAFF accounts, with BCrypt password hashing and server-side RBAC. The seeded demo STAFF account is deliberately manageable so assignments can be demonstrated; demo-data filtering was removed only from the staff-management view.
- Staff-scoped reads use active assignments for `/api/gates`, `/api/gates/{id}`, `/api/gates/{id}/history`, nearby gates, reports, maintenance, live counts, live dashboard and `/api/live/stream`. Unassigned gates return 404/write denial and their SSE `gate-status` events are withheld.
- Large **OPEN GATE** / **CLOSE GATE** buttons on `/staff.html` require a confirmation modal. Confirming writes `STAFF`/`VERIFIED` status immediately with actor and timestamp, broadcasts a `gate-status` SSE event and refreshes database-derived counts. No train prediction or simulation value is used.
- Every staff/admin/super-admin dashboard shows a **Last synchronised** badge derived from the server count snapshot. When `liveDataAvailable=false` the badge reads **Live data unavailable** and no unverified or simulated value is labelled LIVE.
- Staff **My activity** shows only that account's status changes. Admin/Super Admin **Staff activity** shows all staff updates with crossing, actor, action, previous/new status and note; `GET /api/staff/activity` and `GET /api/admin/staff/activity` enforce the same scope.
- `PUT /api/gates/{id}/override` is SUPER_ADMIN-only and requires a non-blank authorization reason. The confirmation modal explains the audited action; the reason, actor and timestamp are written to `gate_status_history` and `audit_logs` as `GATE_STATUS_OVERRIDE`.
- Status colours remain green OPEN, red CLOSED, orange TRAIN APPROACHING/awaiting, and grey OFFLINE/UNVERIFIED.

### Clearly labelled demo simulation
- `GET /api/simulation/snapshot` returns `dataMode=SIMULATION` and `liveDataAvailable=false` with the warning **DEMO SIMULATION — NOT LIVE RAILWAY DATA** and the explicit note that names, gate/status pairings and expected times are fictional.
- The snapshot contains demo gates with OPEN/CLOSED/CLOSING statuses, fictional train names/numbers, gate names, `expectedArrivalAt`, `expectedDepartureAt` and per-second countdown values.
- Labelled, non-live simulation panels were added to `dashboard.html`, `staff.html`, `admin.html` and the React `/live` dashboard. Countdowns tick every second; snapshots refresh every 10 seconds. No panel can enable the `LIVE` badge.
- Simulation rows remain excluded from operational counts and alerts. The snapshot is restricted to demo gate codes; managed tracking rows attached to OSM/reference gates are never shown on the demo board.

### Gate detail and per-second timers
- Gate detail API includes station, district, state, coordinates, stored status, timestamps, expected/actual closing and opening durations, next train and responsible staff.
- Frontend timers tick once per second from stored timestamps and clamp to zero. Missing values display **Not Available**.
- Raw `UNKNOWN` and stale/unverified statuses are never shown publicly; they display **Awaiting Verification**. Raw values remain in the database and history.
- Detailed status colours: green OPEN, red CLOSED, orange CLOSING/awaiting, blue OPENING, purple MAINTENANCE, grey unknown/unavailable, yellow warning.

### Verified train references
- 30 checked train reference records in `TrainReferenceCatalogue` with source references and corrected names, including:
  - `12731/12732` Tirupati–Secunderabad Superfast Express
  - `12761/12762` Tirupati–Karimnagar Superfast Express
  - `12785/12786` Kacheguda–Ashokapuram Superfast Express
- Records are classified `VERIFIED_REFERENCE`, never `VERIFIED_LIVE`; `liveDataAvailable=false`. No simulated train position is presented as genuine live tracking.
- Restricted train numbers remain in the `27004`–`27023` review range; the duplicated `17016` review rows were removed.

### 150+ gates without invented data
- 153 static railway crossings were imported from OpenStreetMap node data (`reference/osm-crossings.json`) with real coordinates and per-node source URLs.
- Each row is `dataClassification=VERIFIED_REFERENCE`, `statusSource=REFERENCE`, `verificationStatus=UNVERIFIED`, `status=AWAITING_VERIFICATION`.
- No coordinate, current status, ETA or train position was invented. Restricted/simulation gates remain hidden in Super Admin Source Review and are never promoted into operational data.
- An additive H2 migration widened seven legacy ENUM status/source columns to `VARCHAR(30)` without deleting rows. Fresh databases and Oracle/MySQL schemas are unaffected.

### Genuine alternate routing
- Route search uses OSRM over OpenStreetMap. Primary and alternate route geometries come from OSRM only; static illustrative fallback geometry was removed.
- The map shows the original route and verified alternates with distinct colours, distance, duration, extra distance/time and avoided-gate radius.
- Candidate routes are accepted only when their geometry genuinely stays outside the crossing avoidance radius.
- If no qualifying detour exists, the response and UI say **No verified alternative route avoiding this crossing was found** and no route is drawn.
- Route results are persisted in `route_search_history`/`route_alternatives` and recalculated on verified gate changes.

### Role workspaces
- **Staff:** assigned gates only, status updates, crossing create/edit, trains, history, notifications, reports, activity and profile. No delete or global permissions.
- **Admin:** all staff capabilities plus all gates/trains, staff account CRUD, assignments, approvals, maintenance, reports, incidents, risk/system monitoring. No Super Admin security edits.
- **Super Admin:** admin/user/role/permission management, audit logs, restricted Source Review, analytics, health/config/data-source/security/backup controls.

### Notifications
- In-app notifications for gate closing/closed/opening/opened, train approaching/crossing/passed, alternate route, maintenance and status updates.
- Operational events use `dataSource=APPLICATION`, `dataMode=REAL_API`; simulation events are suppressed from operational alerts.
- All authenticated roles can be recipients. Repeated identical status updates do not create duplicates.
- Ready for configured email/SMS delivery through the existing notification service hooks.

---

## 2. Files changed or added (most recent increment)

### Added
- `src/main/java/com/railsafe/dto/GateOverrideRequest.java` — SUPER_ADMIN override payload with mandatory authorization reason.
- `src/main/java/com/railsafe/dto/UpdateStaffRequest.java` — admin STAFF account update payload.
- `src/main/resources/static/staff-login.html`, `src/main/resources/static/js/staff-login.js` — dedicated railway staff sign-in.
- `src/main/java/com/railsafe/dto/GateVerificationRequest.java` — explicit staff/admin verification payload.
- `src/main/java/com/railsafe/dto/SimulationSnapshotResponse.java` — labelled simulation JSON shape.
- `src/main/java/com/railsafe/service/SimulationService.java` — demo-only gate/train/timing snapshot generator.
- `src/main/java/com/railsafe/controller/SimulationController.java` — `GET /api/simulation/snapshot`.
- `frontend/src/components/SimulationPanel.jsx` — React demo board with ticking countdowns.
- `src/main/java/com/railsafe/config/OsmCrossingReferenceInitializer.java` — idempotent OSM crossing reference seeder.
- `src/main/java/com/railsafe/config/H2StatusColumnMigration.java` — additive non-destructive H2 ENUM-to-VARCHAR migration.
- `src/main/resources/reference/osm-crossings.json` — 153 verified OSM crossing locations with source URLs.
- `src/test/java/com/railsafe/RoleSecurityIntegrationTest.java` — MockMvc login/page/API role-boundary tests.

### Modified
- `src/main/java/com/railsafe/service/GateService.java` — added `verifyGate(...)`, STAFF-source enforcement, audited override, scoped history queries, public-status notification/SSE on verification-only edits and classification downgrade.
- `src/main/java/com/railsafe/service/StaffAccountService.java` — STAFF account CRUD, activation and assignment management; demo STAFF account is now visible/manageable to admins.
- `src/main/java/com/railsafe/controller/GateController.java` — scoped gate/history/dashboard reads and `PUT /api/gates/{id}/override`.
- `src/main/java/com/railsafe/controller/AuthController.java` — `POST /api/auth/staff-login`.
- `src/main/java/com/railsafe/config/SecurityConfig.java` — staff-login permissions and role-specific redirects; SSE async completion excluded from re-filtering to avoid post-logout access-denied noise.
- `src/main/java/com/railsafe/dto/StatusHistoryResponse.java` — added gate/crossing/actor/action fields for staff and admin activity views.
- `src/main/java/com/railsafe/live/service/LiveUpdateBroadcaster.java`, `LiveDashboardService.java` — staff-scoped SSE and live dashboard data.
- `src/main/resources/application.properties` — `spring.security.filter.dispatcher-types=request,error` for clean SSE logout.
- `src/main/resources/static/css/styles.css` — gate-control, sync-time, UNVERIFIED/awaiting and approaching colours.
- `src/main/resources/static/js/admin.js` — staff accounts/activity tabs, activation, sync badge and `assignedGateIds` rendering fix.
- `src/main/resources/static/js/super-admin.js` — override form, confirmation modal, audit refresh and sync badge.
- `src/main/resources/static/js/staff.js` — large gate controls, confirmation modal, own activity and sync badge.
- `src/main/resources/static/js/common.js` — shared sync-time renderer and live-status labels.
- `src/main/java/com/railsafe/controller/GateController.java` — added `PUT /api/gates/{id}/verification`.
- `src/main/resources/static/staff.html`, `src/main/resources/static/admin.html` — visible Verify / Set status guidance, modal fields and simulation panel containers.
- `src/main/resources/static/js/staff.js`, `src/main/resources/static/js/admin.js` — verification button, modal prefill, verification endpoint submission and simulation wiring.
- `src/main/resources/static/dashboard.html`, `src/main/resources/static/js/dashboard.js` — commuter simulation panel.
- `src/main/resources/static/js/common.js` — shared simulation renderer, countdown ticking and 10-second refresh.
- `frontend/src/App.jsx`, `frontend/src/styles.css` — React simulation mount point and styling.
- `src/main/java/com/railsafe/entity/StatusSource.java` — added `REFERENCE`.
- `src/main/java/com/railsafe/repository/RailwayCrossingRepository.java` — added lookup by crossing code.
- `src/main/java/com/railsafe/service/NotificationService.java` — source label for `REFERENCE`.
- `src/main/java/com/railsafe/exception/GlobalExceptionHandler.java` — quiet SSE `IOException` handling.
- `src/main/java/com/railsafe/entity/{RailwayGate,GateStatusHistory,Notification,UserReport}.java` — status columns widened to 30.
- `src/main/resources/static/js/common.js` — expanded status colours/labels and reference source label.
- `src/main/resources/static/css/styles.css` — expanded status badge and gate-card colours.
- `src/main/resources/static/js/{auth,dashboard,gate,admin,staff,super-admin,map,notifications}.js` — role dashboards, live badges, detail timers, route legend/colours and no-fallback routing.
- `src/main/resources/static/{login,dashboard,staff,admin,super-admin,gate,map,notifications}.html` — role cards, dashboards, panels and responsive layouts.
- `frontend/src/**` — React live counts, SSE, strict LIVE badge, gate detail, train reference panel, route assistance and role-aware navigation.
- `database/*.sql` — additive Oracle/MySQL migrations for all required tables and the expanded status vocabulary.
- `DELIVERY_REPORT.md` — this report.

Earlier increments also added the RBAC entities/services/controllers, `TrainReferenceCatalogue`, `ReferenceDataInitializer`, `LiveCountService`, `LiveUpdateBroadcaster`, audit services, role security rules and all operational DTOs/repositories.

---

## 3. Database changes

Required tables are present and mapped: `users`, `roles`, `permissions`, `gates`/`railway_gates`, `gate_status_history`, `railway_crossings`, `trains`, `train_schedules`, `train_positions`, `gate_train_mapping`, `staff_assignments`, `maintenance_requests`, `route_search_history`, `notifications`, `audit_logs`, `railway_zones`, `railway_divisions`, `data_sources`.

- Roles, permissions, role grants, zones, divisions and data sources are seeded idempotently.
- Restricted simulation rows remain in `imported_source_rows` with `dataMode=SIMULATION` and `dataClassification=RESTRICTED`-equivalent review treatment.
- 153 operational-looking gate reference rows are `VERIFIED_REFERENCE`/`AWAITING_VERIFICATION`; they are static location references, not live status records.
- Five demo staff assignments were created so staff scope is demonstrable without fake status.
- Oracle/MySQL migration files are additive and idempotent. H2 legacy ENUM columns are widened at startup before seeders run.
- No backup was deleted: `data\backup-pre-masterprompt-20261118`, `data\backup-pre-superadmin-20261113` and `data\backup-20261107-184051` remain intact.

### Aiven MySQL production database (2026-11-21)
- `application.properties` now targets Aiven MySQL 8 by default at `mysql-e1e61fd-railsafe.j.aivencloud.com:17447/defaultdb`. Credentials can be overridden with `DB_URL`, `DB_USERNAME` and `DB_PASSWORD`.
- Connections use TLS with server-certificate verification (`sslMode=VERIFY_CA`). The Aiven CA is bundled as `src/main/resources/aiven-truststore.p12` (PKCS12, alias `aiven-mysql-ca`) so no external certificate file is needed at runtime.
- The Hibernate schema was created in `defaultdb` and the complete local H2 dataset was migrated: **5,711 rows across 25 application tables**, including 7 users, 5 roles, 13 permissions, 34 role grants, 173 railway gates, 153 OSM crossings, 17 staff assignments, 3568 notifications, 520 train-tracking rows, 185 route alternatives and 360 restricted source-review rows.
- `imported_source_rows.detail` and `route_alternatives.geometry_json` are explicitly mapped with `@JdbcTypeCode(SqlTypes.LONGVARCHAR)` so MySQL creates/preserves `LONGTEXT` instead of `TINYTEXT(255)`.
- H2 remains available for local development with `--spring.profiles.active=h2`; `application-mysql.properties` is an explicit alias for the default Aiven configuration.
- All database, truststore and SUPER_ADMIN bootstrap secrets were removed from `application.properties` and are supplied as environment variables at startup. The original exposed Aiven DB password, truststore password and SUPER_ADMIN password were rotated directly in Aiven/MySQL and in the rebuilt truststore; the old values are stale. See the Security hardening section below.
- Login-page demo account shortcuts are now off by default (`RAILSAFE_DEMO_ACCOUNTS_ENABLED=false`). `GET /api/auth/demo-accounts` returns an empty array instead of a 404, and the static login pages no longer carry hard-coded fallback credentials, so no stale SUPER_ADMIN password remains in source.
- The optional `h2` profile now overrides the MySQL dialect with `H2Dialect` so local tests and demos create tables correctly while the default profile targets Aiven MySQL.

---

## 3b. Deployment and security hardening (2026-11-21)

### Secrets externalized and rotated
- `application.properties` now contains placeholders only: `${DB_PASSWORD}`, `${AIVEN_TRUSTSTORE_PASSWORD}`, `${RAILSAFE_SUPER_ADMIN_EMAIL}`, `${RAILSAFE_SUPER_ADMIN_PASSWORD}`. No password literal remains in source or configuration.
- `spring.datasource.hikari.data-source-properties.trustCertificateKeyStorePassword` passes the truststore password as a JDBC driver property (not in the URL) so generated special characters do not break URL parsing.
- Added `deployment/railsafe.env.example` as the safe template and `deployment/railsafe.env.local` as the gitignored file holding real rotated values. Added `.gitignore` covering local secrets, build output and local `data/`.
- Added `deployment/start-railsafe.ps1`, which loads `railsafe.env.local` into the process environment before starting `target/railsafe-1.0.0.jar`. The jar no longer starts with plain `java -jar` because required secrets have no defaults.
- Rotation completed:
  - Aiven MySQL user password changed with `ALTER USER USER() IDENTIFIED BY ...`; new password verified by a direct TLS connection.
  - `aiven-truststore.p12` regenerated with a new password and alias `aiven-mysql-ca`; the old truststore password is stale.
  - Existing `SUPER_ADMIN` password hash updated in MySQL with a fresh BCrypt hash; the old `SuperAdmin@2026` value is stale.

### Token-based CSRF protection
- `SecurityConfig` replaces `csrf.disable()` with `CookieCsrfTokenRepository.withHttpOnlyFalse()` (double-submit cookie `XSRF-TOKEN` plus `X-XSRF-TOKEN` header) and an explicit `CsrfTokenRequestAttributeHandler`.
- Added `GET /api/auth/csrf` (`CsrfController`) so same-origin clients can obtain the token; it is public and causes the `XSRF-TOKEN` cookie to be written.
- Both API clients (`static/js/api.js` and `frontend/src/api.js`) fetch the token once per page load and attach the header to all `POST`/`PUT`/`PATCH`/`DELETE` requests. Existing element IDs, form names and API shapes are unchanged.
- `/api/live/sensor/**` is CSRF-exempt because it is a shared-secret machine-to-machine webhook, not a browser session.
- The MockMvc role-security test now obtains a CSRF token before login and sends it as `X-XSRF-TOKEN` with the matching cookie, so role-boundary tests continue to cover 22 cases.
- After the changes: `npm run build` succeeded (50 modules; `assets/index-pnihF0IA.css`, `assets/index-DRLKn09D.js`), `.\mvnw.cmd test` passed **22/22**, and `.\mvnw.cmd -q -DskipTests package` succeeded.

### Remaining production hardening (2026-11-21, approved increment)
- **HTTPS / secure cookies / HSTS:** Added `RAILSAFE_COOKIE_SECURE`, `RAILSAFE_HSTS_ENABLED`, `RAILSAFE_HSTS_MAX_AGE_SECONDS`, `RAILSAFE_SERVER_ADDRESS`/`RAILSAFE_SERVER_PORT` and `RAILSAFE_FORWARD_HEADERS_STRATEGY` overrides. `SecurityConfig` now marks the `XSRF-TOKEN` cookie `Secure` and `SameSite=Strict`, and emits HSTS, when secure mode is enabled. Local HTTP development is unchanged (defaults off).
- Added `deployment/nginx-railsafe.conf`, a TLS-terminating reverse-proxy sample with HTTP→HTTPS redirect, modern TLS settings, HSTS/security headers, forwarded headers and SSE-safe proxy settings. Production must supply real certificates and set the env flags above.
- **Login abuse control:** Added `LoginAttemptService`, `LoginRateLimitFilter` and `LoginAttemptListener`. After `RAILSAFE_LOGIN_MAX_ATTEMPTS` (default 10) failed sign-ins from one client, further `/api/auth/login` and `/api/auth/staff-login` attempts get JSON `429` before reaching the `AuthenticationManager`; the lock expires after `RAILSAFE_LOGIN_LOCK_MINUTES` (default 15) and a successful login clears the counter. The filter is registered only in the Spring Security chain (after `CsrfFilter`) and relies on forwarded headers for the real client IP behind a trusted proxy.
- Added `LoginAttemptServiceTest` (2 lockout/expiry/reset cases) and `LoginRateLimitIntegrationTest` (end-to-end 429 via MockMvc). Test total is now **25/25** (22 prior + 3 new).
- **Schema management:** Added `RAILSAFE_DDL_AUTO` override (default `update`). Operators can switch to `validate` after confirming schema parity; versioned Flyway/Liquibase migrations remain the recommended production path.

---

## 4. APIs verified

| Area | Endpoints |
|---|---|
| Auth | `GET /api/auth/csrf`, `POST /api/auth/login`, `POST /api/auth/staff-login`, `GET /api/auth/me`, `GET /api/auth/demo-accounts`, `POST /api/auth/logout` |
| Live | `GET /api/live/counts`, `GET /api/live/stream` (SSE, staff-scoped gate events) |
| Gates | `GET /api/gates`, `GET /api/gates/{id}`, `GET /api/gates/{id}/detail`, `PUT /api/gates/{id}/status`, `PUT /api/gates/{id}/verification`, `PUT /api/gates/{id}/override`, `GET /api/gates/{id}/history` |
| Demo simulation | `GET /api/simulation/snapshot` |
| Staff | `GET /api/staff/gates`, `GET /api/staff/counts`, `GET /api/staff/activity`, staff-scoped gates/reports/maintenance |
| Admin | `GET /api/admin/staff`, `GET /api/admin/staff/activity`, `POST/PUT /api/admin/staff`, `PUT /api/admin/staff/{id}/enabled`, assignments, maintenance, reports, incidents |
| Trains | `GET /api/trains` |
| Routes | `POST /api/routes/alternatives` |
| Notifications | `GET /api/notifications`, `GET /api/notifications/unread-count` |
| Super Admin | `/api/super-admin/**` health, roles, users, permissions, analytics, audit logs, data sources, security, backup, restricted Source Review |

---

## 5. Verification results (2026-11-19)

### Automated
- `node --check` on `common.js`, `staff.js`, `admin.js`, `dashboard.js`: **4/4 passed** for this increment; the earlier full static JS sweep passed **9/9**. Direct Node checking of `.jsx` is not supported; the React file was validated by the Vite build.
- `.\mvnw.cmd test`: **Tests run: 22, Failures: 0, Errors: 0, Skipped: 0**. Includes 1000-gate scaling, role security MockMvc, role values, restricted dataset integrity, train catalogue and gate mapper tests.
- `npm run build`: Vite build succeeded, **50 modules transformed**; emitted `assets/index-sBx9PDxE.css` and `assets/index-CsPD7WMV.js` to `src/main/resources/static/live/`.
- `.\mvnw.cmd -q -DskipTests package`: succeeded after stopping the running Java process that held the jar; `target\railsafe-1.0.0.jar` contains the latest static JS/CSS and React bundle.

### Aiven MySQL final verification (2026-11-21)
- Default-profile startup against Aiven MySQL 8 succeeded with no DDL warnings; `SUPER_ADMIN` login, `/api/auth/me`, `/api/live/counts`, `/api/gates` (153 gates), `/api/simulation/snapshot` (20 simulated gates, 133 simulated trains, `liveDataAvailable=false`) and `/api/notifications` all returned successfully.
- Login page loads with all four demo role shortcuts available; commuter login hides the STAFF card as designed.
- React `/live` simulation board renders gate-code pills with `white-space: nowrap`, including `RS-DEMO-005` on one line, with **SIMULATION** / **NOT LIVE** labels and zero browser console errors.
- Both `LONGTEXT` columns remained `LONGTEXT` after restart (@Lob mapping removed), and all 5,711 migrated rows are intact.

### Post-hardening final verification (2026-11-21)
- Started the newly packaged jar with `deployment/start-railsafe.ps1`; startup against Aiven MySQL succeeded using only environment-supplied secrets.
- `GET /api/auth/csrf` returned a 36-character token and set the `XSRF-TOKEN` cookie. Login with the rotated SUPER_ADMIN password returned `200`, and `/api/auth/me` confirmed `SUPER_ADMIN`.
- `POST /api/reports` with `{}` returned `403` without the CSRF header and `400` (field validation) with the header, proving the token gate runs before controller validation.
- `POST /api/live/sensor/gates/1/status` without a CSRF token returned the webhook's own `404` ("endpoint not available"), confirming the shared-secret exemption still works.
- `GET /api/auth/demo-accounts` returned `[]`; the login page shows **No demo accounts are available on this server** and no hard-coded fallback credentials.
- Browser checks: `/login.html` rendered with zero console errors; after a CUSTOMER login, `/live/` showed **SIMULATION** / **NOT LIVE**, zero `.badge-live` elements, five nowrap `RS-DEMO-*` gate-code pills and zero console errors.
- `.\mvnw.cmd test`: **22/22 passed** after updating the MockMvc CSRF flow and adding the H2 dialect override; `.\mvnw.cmd -q -DskipTests package`: succeeded.

### Final hardening verification (2026-11-21)
- Clean rebuild: `.\mvnw.cmd clean test` **25/25 passed** (22 prior + 2 lockout unit + 1 end-to-end 429 integration); `.\mvnw.cmd -q -DskipTests package` rebuilt the jar (71 MB).
- Restarted via `deployment/start-railsafe.ps1` against Aiven MySQL: CSRF token length 36, rotated admin login `200`, `/api/auth/me` `SUPER_ADMIN`, `DEMO_ACCOUNTS []`.
- CSRF still enforced: write API without the `X-XSRF-TOKEN` header returns `403`; with the header it reaches validation (`400`). Sensor webhook remains exempt (its own `404`).
- Live lockout confirmed: ten bad logins from one client returned `401`, and the next returned JSON `429` with the `Too many failed login attempts` message.
- Data intact: `totalGates=153`, `awaitingVerificationGates=153`, `liveDataAvailable=false`, `dataMode=UNAVAILABLE` (simulation stays a separate, labelled panel).

### Gate staff module smoke (final H2 build)
- API/RBAC smoke: **23/23 passed** — unauthenticated gate access blocked, `/staff.html` redirect, non-STAFF staff-login rejection, STAFF session/scope, unassigned-gate read/write denial, staff OPEN verification, own activity, admin staff list/activity, override reason requirement, SUPER_ADMIN override audit, ADMIN override denial and non-LIVE counts.
- SSE smoke: **7/7 passed** — `connected`, DB-derived `counts`, assigned gate `gate-status` received, unassigned gate `gate-status` withheld, and `lastSuccessfulUpdate` present for sync badges.
- Browser UI: staff sign-in, OPEN/CLOSE confirmation, verified status write, **My activity**, admin staff accounts (5 assigned gates shown), assignment modal (5 boxes checked), complete staff activity, Super Admin override modal/audit row, and commuter sync badge all rendered with **zero browser console errors**.
- Server log after the final build: no `ERROR` or `AuthorizationDeniedException` entries during login, gate updates, SSE propagation or logout. Excluding ASYNC dispatch from security re-filtering removed the post-logout SSE stack traces without changing initial-request authorization.
- Two smoke bugs were found and fixed: the seeded STAFF account was hidden from Admin by the demo classifier, and `admin.js` read `gateIds` while the API returns `assignedGateIds`. Both are corrected and re-verified.
- Smoke-verified gates 23 and 28 were reset to `AWAITING_VERIFICATION` / `UNVERIFIED`; final counts are `total=153, awaiting=153, closed=0, liveDataAvailable=false`.

### Runtime role matrix (H2)

| Role | dashboard | staff | admin | super-admin | Key APIs |
|---|---:|---:|---:|---:|---|
| `CUSTOMER` | 200 | 302 | 302 | 302 | live/trains/gates 200; staff/admin/super 403 |
| `STAFF` | 302 | 200 | 302 | 302 | staff gates 200; admin staff 403 |
| `ADMIN` | 302 | 302 | 200 | 302 | admin staff 200; super health 403 |
| `SUPER_ADMIN` | 302 | 302 | 200 | 200 | health/roles/demo 200 |

Unauthenticated `/api/live/counts` returns `401`.

### Data and behaviour
- Counts: `total=153`, `awaitingVerification=153`, all open/closed/closing/opening/approaching/active `=0`, `liveDataAvailable=false`, `dataMode=UNAVAILABLE`, scope `ALL_GATES`.
- Gate list: 153 rows with real OSM coordinates, station/district/state, `VERIFIED_REFERENCE`, `REFERENCE`, `AWAITING_VERIFICATION`.
- Gate detail: populated fields; displayed status **Awaiting Verification**; frontend per-second timers and **Not Available** handling in place.
- Trains: 30 reference records, first `12701 Hussainsagar Express`, `VERIFIED_REFERENCE`, `liveDataAvailable=false`.
- Staff: 5 assigned gates. Staff `CLOSED` update produced one customer notification (`GATE_CLOSED`, `REAL_API`); repeating the same update produced no duplicate.
- SSE: emitted `counts`, `connected`, `gate-status` and updated `counts` after a status change. No error logs on client abort.
- Routing: OSRM returned a real route with 2 verified alternates for a Bengaluru crossing (original 3.3 km/4 min, alternates 3.9 km/4 min and 4.0 km/6 min, 150 m avoidance radius). No fallback geometry is drawn when OSRM is unavailable.
- H2 startup log: `Migrated 7 H2 status/source ENUM column(s) to VARCHAR`; `OSM crossing references loaded: 153 gates created, 153 reference rows created, 5 staff assignments created`.

### Manual verification and simulation smoke (H2, ADMIN session)
- `PUT /api/gates/23/verification` with `CLOSED` / `VERIFIED` returned `status=CLOSED`, `displayStatus=CLOSED`, `statusSource=STAFF`, `verificationStatus=VERIFIED`, `dataClassification=VERIFIED_LIVE`.
- Counts changed `total=153, awaiting=153, closed=0` → `awaiting=152, closed=1`; gate detail returned `status=CLOSED`, `dataSource=STAFF`, `verificationStatus=VERIFIED`.
- Unread notifications changed `2 → 3`; repeating the identical verification left both unread count and counts unchanged.
- With an SSE subscriber attached, verifying `OSM-11054542384` as `OPEN` emitted `connected`, `counts`, `gate-status` (`AWAITING_VERIFICATION → OPEN`) and a refreshed `counts` snapshot. No `LIVE` badge was enabled.
- `GET /api/simulation/snapshot` returned `dataMode=SIMULATION`, `liveDataAvailable=false`, warning text, 20 demo gates with mixed OPEN/CLOSED/CLOSING statuses, and train rows with names, numbers, demo gate names, `expectedArrivalAt` and `expectedDepartureAt`.
- The first simulation smoke exposed non-demo gate codes on the train board; `SimulationService` was corrected to restrict both gate and train rows to demo gate codes and to order active/upcoming trains before already-passed sample rows. Final smoke: 133 demo train rows, 0 non-demo gate codes, first rows `APPROACHING` with non-zero countdowns.
- Browser checks: ADMIN `/admin.html`, STAFF `/staff.html`, CUSTOMER `/dashboard.html` and React `/live` all rendered the labelled simulation panel with 24 live countdown elements, **NOT LIVE** text and zero `.badge-live` elements. STAFF showed 5 assigned **Verify / Set status** buttons; ADMIN showed 153. No browser console errors were recorded.

---

## 6. Demo credentials (demo profile only)

| Role | Email | Password |
|---|---|---|
| `SUPER_ADMIN` | `superadmin@railsafe.demo` | Rotated; `RAILSAFE_SUPER_ADMIN_PASSWORD` |
| `ADMIN` | `admin@railsafe.demo` | `Admin@123` |
| `STAFF` | `staff@railsafe.demo` | `Staff@123` |
| `CUSTOMER` | `customer@railsafe.demo` | `Customer@123` |
| `USER` (legacy) | `user@railsafe.demo` | `User@123` |

These demo shortcut values are historical examples only. The Aiven SUPER_ADMIN password was rotated; the active value is stored only in `deployment/railsafe.env.local` and must be supplied as `RAILSAFE_SUPER_ADMIN_PASSWORD`. Production bootstrap requires `RAILSAFE_SUPER_ADMIN_EMAIL` and `RAILSAFE_SUPER_ADMIN_PASSWORD`; demo credentials are not exposed outside h2/dev unless `RAILSAFE_DEMO_ACCOUNTS_ENABLED=true` is set explicitly.

---

## 7. Limitations and operational notes

1. **No live railway feed:** RailSafe is not connected to Indian Railways. Live counts are database-derived and the `LIVE` badge remains off. The 153 OSM crossings verify location only; their current status is deliberately **Awaiting Verification**.
2. **OSRM dependency:** Alternate routing requires public OSRM/OpenStreetMap availability. When unavailable, the UI says route data is unavailable and draws no route.
3. **Oracle/MySQL:** Aiven MySQL 8 is now live and verified with TLS; local H2 data was fully migrated. The Oracle SQL scripts remain additive/idempotent but were only statically validated.
4. **CSRF:** Token-based double-submit CSRF protection is enabled for all browser write APIs, with a shared-secret exemption for the sensor webhook. The `XSRF-TOKEN` cookie becomes `Secure`/`SameSite=Strict` when `RAILSAFE_COOKIE_SECURE=true`; see item 9 for TLS deployment.
5. **Audit listing cap:** All events are stored; the Super Admin UI lists the 100 most recent.
6. **Simulation isolation:** Restricted/simulation gates, trains and positions remain review-only and are never promoted to operational counts or alerts. The optional, explicitly labelled **DEMO SIMULATION** panel is the only place fictional gate/train timings are shown; it never sets `LIVE`.
7. **Manual verification duty:** OSM crossings are location references only until authorised staff record an observed status. Until then they display **Awaiting Verification**.
8. **Backups preserved:** All three pre-existing backup folders remain untouched.
9. **Transport security:** The app still serves plain HTTP on port 8080 by default. `deployment/nginx-railsafe.conf` is a ready TLS-terminating proxy sample; for public exposure deploy it with real certificates and set `RAILSAFE_COOKIE_SECURE=true`, `RAILSAFE_HSTS_ENABLED=true`, `RAILSAFE_FORWARD_HEADERS_STRATEGY=framework` and `RAILSAFE_SERVER_ADDRESS=127.0.0.1`. Application-level HTTPS/HSTS support is implemented but not active in the local demo.
10. **Schema management:** `spring.jpa.hibernate.ddl-auto=update` remains the default for convenience; `RAILSAFE_DDL_AUTO=validate` is available once the schema is confirmed. Replace automatic DDL with versioned, reviewed migrations (Flyway/Liquibase) before production releases.
11. **Abuse controls:** Login now has an in-memory per-client lockout (10 failures / 15 minutes by default) with JSON `429` responses, plus the existing CSRF gate. Registration has no dedicated rate limit yet; add one at the reverse proxy (or application) for public deployments. The lockout is not shared across instances, so a multi-node deployment should also rate-limit at the edge.
12. **Operations:** Automated backups, monitoring/alerting and secret-manager integration are not yet configured for Aiven. `deployment/railsafe.env.local` is a local-file substitute for a managed secret store; move its values into your secret manager and inject them as environment variables in production.

---

## 8. Vercel + Render deployment readiness

- Added root `vercel.json`: Vercel installs/builds `frontend/`, publishes
  `src/main/resources/static`, proxies `/api/*` to the Render service through the
  required `BACKEND_URL` environment variable, and rewrites `/live/` to the built
  React `index.html`. API rewrite caching is disabled.
- Added root `render.yaml` and root `Dockerfile`: one Docker web service,
  multi-stage Maven build, JRE 17 runtime, public `/api/health` health check,
  `PORT` binding, forwarded headers, Secure cookies and HSTS. Secrets use
  `sync: false` and remain in the Render dashboard.
- Added `.dockerignore`, `.vercelignore` and root `DEPLOYMENT.md`. The existing
  static pages, element IDs, form names, root-relative API URLs, token CSRF flow,
  sensor-webhook exemption, login lockout and labelled simulation UI are unchanged.
- The browser continues to call one origin (Vercel), so no CORS, `SameSite=None`
  or frontend API-base changes were introduced.

---

## 9. Conclusion

The existing RailSafe application was extended in place with login-first four-role access, role-specific dashboards, strict live-data gating, visible staff/admin manual verification, dedicated gate-staff sign-in, large confirmed OPEN/CLOSE controls, assignment-scoped staff dashboards/history/SSE, fully audited SUPER_ADMIN overrides, a clearly labelled demo simulation board, 153 verified OSM crossing references, 30 checked train references, genuine OSRM alternate routing, deduplicated real-time notifications, staff/admin/Super Admin operations, comprehensive DB migrations, externalized and rotated secrets, token-based CSRF protection, HTTPS/secure-cookie/HSTS configuration, a TLS reverse-proxy sample, login lockout and a schema-validation override. Automated tests (25/25), static JS checks, frontend build, package build, 23 API/RBAC checks, 7 SSE checks and browser UI checks all passed. No simulation data was presented as genuine live railway information.
