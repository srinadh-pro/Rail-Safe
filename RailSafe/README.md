# RailSafe – Smart Railway Gate Alert & Route Assistance System

RailSafe is a Java Full Stack hackathon demo for checking **reported** railway-crossing status, viewing nearby crossings on a map, saving routes, submitting commuter reports, and receiving alerts. The upgraded version adds a **React live-assistance dashboard** with Indian train tracking, estimate-only countdowns, next-train prediction, alternative routes and per-user notification preferences. It runs on a clearly labelled **synthetic demo dataset** out of the box.

> **RailSafe is a demonstration project. It has no connection to Indian Railways.**
> All crossings are fictional **DEMO** records. The shipped train/ETA/route dataset is synthetic. When `RAILSAFE_LIVE_MODE=railradar` and an API key are configured, train values come from RailRadar, an **independent third-party provider** — never from Indian Railways, IRCTC or NTES. Reported status can be stale and is never a guarantee that a crossing is safe. Never bypass a closed barrier, never drive around a gate, and always obey physical barriers, signals and authorised railway staff.
>
> The shipped default is **SIMULATION**: a 20-crossing, 30-train synthetic dataset that is always labelled `SIMULATION MODE - NOT LIVE RAILWAY DATA`. Set `RAILSAFE_LIVE_MODE=unavailable` (optionally with `RAILSAFE_SYNTHETIC_DATA=false`) to show `LIVE TRAIN DATA UNAVAILABLE` and invent nothing. Set `RAILSAFE_LIVE_MODE=railradar` plus `RAILSAFE_RAILRADAR_KEY` to use the labelled third-party provider.
>
> Full live-layer documentation: [`docs/LIVE_SYSTEM.md`](docs/LIVE_SYSTEM.md) · Presentation material: [`docs/PRESENTATION.md`](docs/PRESENTATION.md) · Viva prep: [`docs/VIVA.md`](docs/VIVA.md)

---

## 1. What the app does

| Feature | Description |
|---|---|
| Gate map | Leaflet + OpenStreetMap map with green **OPEN**, red **CLOSED** and grey **UNKNOWN** pins |
| Gate details | Source, verification state, last-updated time, stale warning, status history and nearby crossings |
| Commuter reports | Logged-in users report OPEN/CLOSED; reports stay **PENDING** until staff review |
| Staff/admin console | Add/edit crossings, record status updates, inspect history, approve/reject reports |
| Route assistance | OSRM route alternatives; crossings within 150 m of a route are flagged |
| Saved routes | Store start/destination and associated gate IDs per user |
| Alerts | In-app notifications plus optional browser notifications for status changes |
| **Live dashboard** | React 18/Vite app at `/live/` with provider mode banner, verified gate state, train list, countdowns, map and alerts |
| **Train tracking** | Multi-train simulated tracking with statuses APPROACHING / NEAR_GATE / CROSSING / PASSED; optional authorised HTTP JSON provider |
| **Synthetic dataset** | 20 crossings, 30 Indian train services, 50 tracking rows, 30 history rows, 30 demo routes and 50 notifications, seeded idempotently |
| **Countdowns** | Closing countdown only when verified OPEN with usable data; opening countdown only when verified CLOSED; absolute target timestamps survive refresh |
| **Next train** | Nearest relevant train, with later trains chained so the barrier is not reopened between them |
| **Verified alternatives** | OSRM routes are rejected if their geometry comes within the configurable avoid radius of the selected crossing |
| **Demo alternatives** | Clearly labelled synthetic routes per crossing for the closed-gate demo; never presented as OSRM-verified |
| **Live alerts** | Server-side polling for approaching, closing soon, closed, crossing, passed, opening soon, reopened and alternative route |
| **Preferences** | Per-user browser/email/SMS notification preferences; optional channels require server configuration |
| **Login-first access** | Login page is the only public page; every dashboard, gate, train and report API requires a session |
| **Account administration** | SUPER_ADMIN can create, edit and deactivate ADMIN accounts; roles cannot be escalated through the UI or API |
| Roles | USER, STAFF, ADMIN and SUPER_ADMIN with backend-enforced permissions |
| Stale protection | A stored status older than 120 minutes is displayed as **UNKNOWN** |

### Data honesty model

| Mode | How it is enabled | What the UI shows |
|---|---|---|
| `UNAVAILABLE` | `RAILSAFE_LIVE_MODE=unavailable` or no provider | Grey `LIVE TRAIN DATA UNAVAILABLE`; no counts, ETAs or invented positions |
| `SCHEDULE` | Provider returns timetable data only | Blue `SCHEDULED DATA ONLY - NOT LIVE TRAIN POSITIONS` |
| `LIVE` | Authorised HTTP JSON provider configured | Provider name and source timestamp; predictions still labelled estimates |
| `THIRD_PARTY` | `provider-mode=railradar` with `RAILSAFE_RAILRADAR_KEY` | Orange `THIRD-PARTY LIVE DATA - NOT OFFICIAL INDIAN RAILWAYS DATA`; values reproduced from RailRadar |
| `SIMULATION` (shipped default) | `provider-mode=simulation` **and** `simulation-acknowledged=true` | Striped orange `SIMULATION MODE - NOT LIVE RAILWAY DATA`; never persisted as live audit data |

### Optional third-party live data

RailRadar is an independent Indian Railways API, not an official feed. To enable it:

```powershell
$env:RAILSAFE_LIVE_MODE = "railradar"
$env:RAILSAFE_RAILRADAR_KEY = "rg_your_key"
$env:RAILSAFE_RAILRADAR_MONTHLY_QUOTA = "100"   # set this to your real plan limit
java -jar target\railsafe-1.0.0.jar --spring.profiles.active=h2
```

The adapter polls the configured train numbers in rotation, matches them to
crossings by route proximity (5 km default), and keeps the last successful values
when the monthly quota is exhausted. Cached values older than the freshness window
are marked stale and predictions are withheld. The free 100-request/month plan is
not enough for continuous tracking; details and configuration are in
[`docs/LIVE_SYSTEM.md`](docs/LIVE_SYSTEM.md).

A gate is shown as `OPEN` or `CLOSED` only when its stored status is fresh and verified by staff or an authorised sensor. Otherwise the verified status is `UNKNOWN`, even if the last reported status was different.

### Important honesty rules built into the UI

- The source is always shown (`DEMO`, `STAFF`, `USER` or `SENSOR`).
- Every gate is marked **DEMO / NOT LIVE DATA** in this project.
- User reports are labelled **UNVERIFIED UNTIL REVIEWED**.
- A stale status is converted to **UNKNOWN** for display.
- The app never claims that a green pin means “safe to cross”.
- Route analysis says what it found in the data; it never guarantees avoidance of a closed gate.
- No provider and simulation disabled means **LIVE TRAIN DATA UNAVAILABLE**; no position, countdown or train is invented.
- Countdowns require a verified gate state and usable train data.
- Simulation is always labelled; synthetic routes are never presented as OSRM-verified.
- Routes are shown as verified only when OSRM geometry stays outside the crossing avoid radius.

---

## 2. Technology stack

| Layer | Technology |
|---|---|
| Language | Java 17 |
| Backend | Spring Boot 3.5.16, Spring Web, Spring Data JPA, Spring Security, Bean Validation, Spring Scheduling |
| Database | Oracle Database 19c+ (default), H2 (smoke tests), MySQL 8 (optional profile) |
| Auth | Session-based login with BCrypt password hashes and `JSESSIONID` |
| Build | Maven (wrapper included) |
| Frontend | HTML5, CSS3, vanilla JavaScript, Bootstrap 5.3, Bootstrap Icons |
| **Live dashboard** | React 18, Vite 5, Leaflet 1.9 (build output copied into Spring static resources) |
| Maps | Leaflet 1.9, OpenStreetMap tiles |
| Routing | Public OSRM demo server (network required); replace for production |
| Place search | OpenStreetMap Nominatim (network required) |
| Optional delivery | JavaMailSender and a generic SMS webhook, both disabled by default |

Node.js is required only to build the React dashboard (`npm install`, `npm run build`). The packaged Spring Boot JAR serves the pre-built dashboard; Node is not needed at runtime.

---

## 3. Project structure

```text
RailSafe/
├── pom.xml
├── mvnw, mvnw.cmd, .mvn/            # Maven wrapper (no global Maven required)
├── README.md
├── .gitignore
├── database/
│   ├── oracle_setup.sql             # one-time SYSDBA setup (PDB + RAILSAFE user)
│   ├── railsafe_schema.sql          # optional manual Oracle schema + 5 demo gates + live tables
│   ├── railsafe_live_migration_oracle.sql  # additive migration for existing Oracle installs
│   ├── railsafe_schema_mysql.sql    # optional MySQL 8 schema
│   └── oracle_reset.sql             # clears demo rows so the app reseeds them
├── docs/
│   ├── LIVE_SYSTEM.md               # live architecture, API, DB, tests, limitations
│   ├── PRESENTATION.md              # abstract, PPT content, demo sequence, 3-minute script
│   └── VIVA.md                      # technical viva questions and answers
├── frontend/                        # React + Vite source for the /live/ dashboard
├── data/                            # H2 files, created only in the h2 profile
└── src/main/
    ├── java/com/railsafe/
    │   ├── RailSafeApplication.java
    │   ├── config/
    │   │   ├── DataInitializer.java  # demo users, gates, route, report, alerts
    │   │   └── SecurityConfig.java   # endpoint rules, BCrypt, JSON 401/403
    │   ├── controller/               # REST endpoints
    │   ├── dto/                      # request/response records
    │   ├── entity/                   # JPA entities + enums
    │   ├── exception/                # consistent JSON error handling
    │   ├── repository/               # Spring Data JPA interfaces
    │   ├── security/                 # principal, UserDetailsService, login lockout
    │   ├── service/                  # business logic
    │   └── util/GeoUtils.java        # Haversine / point-to-route distance
    └── resources/
        ├── application.properties     # Aiven MySQL default configuration
        ├── application-h2.properties  # optional H2 profile
        └── static/
            ├── index.html, login.html, register.html
            ├── dashboard.html, map.html, gate.html
            ├── notifications.html, admin.html, super-admin.html
            ├── css/styles.css
            └── js/*.js
```

### Backend file-by-file

| File | Responsibility |
|---|---|
| `RailSafeApplication.java` | Spring Boot entry point |
| `config/SecurityConfig.java` | Login-first URL rules, CSRF, HSTS/secure-cookie flags, BCrypt bean, JSON 401/403 |
| `config/TimeConfig.java` | Single UTC clock bean for deterministic time-dependent tests |
| `security/LoginAttemptService.java` | Per-client failed-login counters and temporary lockout |
| `security/LoginRateLimitFilter.java` | Rejects locked-out login attempts with JSON 429 before authentication |
| `security/LoginAttemptListener.java` | Feeds Spring auth success/failure events into the lockout tracker |
| `config/DataInitializer.java` | Seeds 3 demo accounts, 5 DEMO gates, 1 saved route, 1 pending report, 2 alerts |
| `config/SuperAdminBootstrap.java` | Creates the first SUPER_ADMIN from environment credentials only when none exists |
| `controller/AuthController.java` | Register, login, `/me`; stores the security context in the HTTP session |
| `controller/GateController.java` | Authenticated gate reads; STAFF/ADMIN/SUPER_ADMIN writes; ADMIN/SUPER_ADMIN delete |
| `controller/ReportController.java` | Submit reports, list own reports, staff/admin moderation |
| `controller/NotificationController.java` | List, unread count, mark one/all read |
| `controller/SavedRouteController.java` | List/create/delete the current user's routes |
| `controller/AdminController.java` | Dashboard statistics |
| `controller/SuperAdminController.java` | SUPER_ADMIN-only list/create/edit/deactivate ADMIN accounts |
| `service/AuthService.java` | Normalises email, checks duplicates, hashes password with BCrypt |
| `service/GateService.java` | Gate CRUD, status changes, history, notifications, nearby search |
| `service/GateMapper.java` | Entity → response DTO; the single place that applies stale → UNKNOWN |
| `service/ReportService.java` | Report creation, duplicate-pending guard, approve/reject side effects |
| `service/NotificationService.java` | User alerts, dedupe keys, read state |
| `service/SavedRouteService.java` | Route ownership and gate-ID serialisation |
| `service/AdminService.java` | Counts for the operations dashboard |
| `service/SuperAdminService.java` | ADMIN account create/edit/deactivate; never changes roles |
| `security/AppUserPrincipal.java` | Authenticated user data used by controllers |
| `security/AppUserDetailsService.java` | Loads a user by email for Spring Security |
| `util/GeoUtils.java` | Distance calculations used by nearby search and route checks |
| `live/properties/LiveProperties.java` | Typed live/routing/alert configuration |
| `live/provider/*` | Unavailable, simulation and authorised HTTP JSON provider adapters |
| `live/service/TrainTrackingService.java` | One cached feed per poll, normalisation and mode reporting |
| `live/service/GatePredictionService.java` | Verified state, closing/opening countdowns, opening buffer and multi-train chaining |
| `live/service/GateTimingService.java` | Compact `/timing` payload with absolute countdown targets |
| `live/service/AlternateRouteService.java` | Synthetic demo alternatives per crossing |
| `live/service/RailwayGateService.java` | Thin read-only facade for gate timing and demo routes |
| `live/service/TrainCatalogService.java` | `/api/trains/{trainNumber}` catalogue lookup and live assignments |
| `live/service/SyntheticDataService.java` | Idempotent 20/30/50/30/30/50 synthetic dataset seeder |
| `live/service/LiveDashboardService.java` | Dashboard payload and compact gate summaries |
| `live/service/RouteCalculationService.java` | OSRM call, polyline verification, cache and audit persistence |
| `live/service/LiveAlertScheduler.java` | Scheduled live alerts, snapshot capture and dedupe |
| `live/service/TrainSnapshotService.java` | Optional audit of real provider data with retention |
| `live/service/AlertDeliveryService.java` | Optional email/SMS delivery, disabled by default |
| `controller/LiveController.java` | Provider, gate list, dashboard and sensor endpoints |
| `controller/LivePageController.java` | Forwards `/live` and `/live/` to the built React index |
| `controller/AlternativeRouteController.java` | Verified OSRM alternatives and the route-available alert |
| `controller/TrainController.java` | Public synthetic train catalogue lookup |
| `controller/NotificationPreferenceController.java` | Browser/email/SMS channel preferences |
| `entity/*` | Eleven tables plus role/status/source/verification/mode enums |
| `repository/*` | Database queries through Spring Data JPA |
| `dto/*` | Safe JSON contracts; entities and password hashes are never returned |
| `exception/GlobalExceptionHandler.java` | Converts validation/conflict/not-found/auth errors into JSON |

### Frontend file-by-file

| File | Responsibility |
|---|---|
| `index.html` + `home.js` | Landing page, demo gate preview, safety banner |
| `login.html`, `register.html` + `auth.js` | Login/registration, demo-account fill buttons, safe `next` redirect |
| `dashboard.html` + `dashboard.js` | Nearby crossings, saved routes, unread count, geolocation |
| `map.html` + `map.js` | Leaflet pins, filters, place search, route alternatives, 150 m analysis, route saving and watching |
| `gate.html` + `gate.js` | Status labels, mini-map, history, report form, my recent reports |
| `notifications.html` + `notifications.js` | Alert list, unread filter, mark read, browser notification permission |
| `admin.html` + `admin.js` | Role guard, stats, gate CRUD, status updates, history, report moderation |
| `super-admin.html` + `super-admin.js` | SUPER_ADMIN-only ADMIN account management page |
| `css/styles.css` | Design system, status colours, map/admin/history styles |
| `js/api.js` | Fetch wrapper, JSON errors, 401 handling |
| `js/common.js` | Shared navigation, footer, role-aware links, badges, toasts, formatting |
| `frontend/src/App.jsx` | React dashboard composition, gate selector, auth state |
| `frontend/src/hooks.js` | Dashboard polling, countdown ticking, notification polling |
| `frontend/src/components/*` | Provider, gate, countdown, trains, demo routes, OSRM routes, map and notification panels |
| `frontend/src/components/DemoRoutesPanel.jsx` | Synthetic per-crossing alternatives with map and explicit demo labels |
| `frontend/src/styles.css` | React dashboard design system and responsive rules |
| `static/live/*` | Vite production build served at `/live/` |

---

## 4. Architecture

```text
┌──────────────────────────────────────────────────────────────┐
│ Browser                                                      │
│ Bootstrap pages + React /live/ + Leaflet/OSM + fetch()       │
└──────────────────────────────┬───────────────────────────────┘
                               │ JSON over HTTP (same origin)
┌──────────────────────────────▼───────────────────────────────┐
│ Spring Security filter chain                                 │
│  - public: static pages, /live/**, GET gates/live/routes     │
│  - authenticated: reports, routes, notifications, /me        │
│  - STAFF/ADMIN: gate writes, moderation, admin stats         │
│  - sensor key: POST /api/live/sensor/**                      │
└──────────────────────────────┬───────────────────────────────┘
┌──────────────────────────────▼───────────────────────────────┐
│ Controllers  →  Services  →  Repositories                    │
│                 │                                            │
│                 ├─ GateMapper: stale status → UNKNOWN        │
│                 ├─ GateService: history + alerts             │
│                 ├─ ReportService: PENDING → APPROVED/REJECTED│
│                 ├─ NotificationService: dedupe + channels    │
│                 ├─ TrainTrackingService: provider + cache    │
│                 ├─ GatePredictionService: safety rules       │
│                 ├─ RouteCalculationService: OSRM + verify    │
│                 └─ LiveAlertScheduler: scheduled evaluation  │
└──────────────────────────────┬───────────────────────────────┘
                               │ JPA / Hibernate
┌──────────────────────────────▼───────────────────────────────┐
│ Oracle DB 19c+ (default), H2 (demo) or MySQL 8 (optional)   │
│ users, railway_gates, gate_status_history, notifications,    │
│ user_reports, saved_routes, train_snapshots,                 │
│ route_alternatives, notification_preferences                 │
└──────────────────────────────────────────────────────────────┘

External: authorised train provider (configured), OSRM, OSM tiles,
Nominatim, optional mail server and SMS webhook.
```

### Beginner-friendly explanation

- **Entity** = a database table represented as a Java class.
- **Repository** = the records clerk; it fetches and saves entities.
- **Service** = the manager; it applies business rules such as “stale means UNKNOWN”.
- **Controller** = the receptionist; it receives HTTP requests and calls the service.
- **DTO** = a sealed envelope sent to the browser. It contains only safe public fields, never a password hash.
- **Mapper** = the translator between an entity and a DTO.
- **Session cookie** = the browser’s proof of login. After `POST /api/auth/login`, the browser automatically sends `JSESSIONID` with later requests.

A request such as “show gate 3” travels:

```text
GET /api/gates/3
  → SecurityConfig checks that GET gate reads are public
  → GateController.get(3)
  → GateService.get(3)
  → RailwayGateRepository.findById(3)
  → GateMapper applies stale/UNKNOWN logic
  → JSON response
```

---

## 5. Database design

### Relationships

```text
users 1 ─── N saved_routes
users 1 ─── N user_reports
users 1 ─── N notifications
users 1 ─── 1 notification_preferences
users 1 ─── N railway_gates        (updated_by_id)
users 1 ─── N gate_status_history  (changed_by_id)

railway_gates 1 ─── N gate_status_history
railway_gates 1 ─── N notifications
railway_gates 1 ─── N user_reports
railway_gates 1 ─── N route_alternatives

train_snapshots is an independent audit table keyed by provider, train number and
gate code. It intentionally has no foreign key so audit history survives gate
deletion. Simulation rows are never written to it.
```

### Tables

| Table | Purpose | Important columns |
|---|---|---|
| `users` | Accounts | `email` unique, `password_hash` (BCrypt), `role`, `enabled` |
| `railway_gates` | Crossings | `gate_code` unique, coordinates, stored `status`, `status_source`, `verification_status`, `last_updated_at` |
| `gate_status_history` | Audit trail | `gate_id`, `old_status`, `new_status`, `source`, `changed_by_id`, `note`, `changed_at` |
| `notifications` | Per-user alerts | `user_id`, `gate_id`, `type`, `is_read`, `status_at_event`, unique `dedupe_key` |
| `user_reports` | Commuter submissions | `reported_status`, `report_comment` (JSON field: `comment`), `status` (`PENDING/APPROVED/REJECTED`), reviewer fields |
| `saved_routes` | Favourite routes | start/destination labels + coordinates, comma-separated `gate_ids` |
| `train_snapshots` | Optional audit of authorised provider responses | `provider`, `data_mode`, `train_number`, `gate_code`, `eta_at_gate`, `observed_at`, `recorded_at`, `feed_stale`; retention-limited |
| `route_alternatives` | Persisted OSRM options that were checked against one crossing | `gate_id`, `route_key`, `distance_km`, `duration_minutes`, `distance_from_gate_meters`, `recommended`, `geometry_json` |
| `notification_preferences` | Per-user delivery channels | unique `user_id`, `browser_enabled`, `email_enabled`, `sms_enabled` |

Existing Oracle installations only need `database/railsafe_live_migration_oracle.sql`.
A fresh manual install can use `database/railsafe_schema.sql`; MySQL users can use
`database/railsafe_schema_mysql.sql` with the optional `mysql` profile. Hibernate
`ddl-auto=update` remains an alternative in all profiles.

### Status model

**Stored status** (`railway_gates.status`): what was last recorded.
**Displayed status** (`displayStatus` in the API): what the UI should show.

```text
if last_updated_at is older than railsafe.gate.stale-minutes (default 120):
    displayed status = UNKNOWN
    stale = true
else:
    displayed status = stored status
    stale = false
```

**Sources**: `DEMO`, `STAFF`, `USER`.
**Verification**: `DEMO`, `UNVERIFIED`, `VERIFIED`.

Every write to a gate status creates a `gate_status_history` row. Notifications are created only when the status actually changes.

Commuter reports do **not** change the gate immediately. They remain `PENDING`. When staff approve a report, the reported status is applied with source `STAFF` and verification `VERIFIED`.

---

## 6. Quick start

### Prerequisites

- Windows with Oracle Database 19c installed locally (CDB `ORCL`, PDB `ORCLPDB`)
- Java JDK 17 or newer (`java -version`)
- Internet access for map tiles, OSRM and Nominatim

The Maven wrapper avoids a separate Maven installation. The first wrapper run downloads Maven 3.9.16.

### Option A — Run with Oracle (default profile, recommended)

**1. Make sure Oracle is running.** In PowerShell:

```powershell
Get-Service OracleServiceORCL, OracleOraDB19Home1TNSListener
```

Both should be `Running`. If not:

```powershell
Start-Service OracleServiceORCL
Start-Service OracleOraDB19Home1TNSListener
```

**2. Create the database user once** (as SYSDBA). From the project root:

```powershell
sqlplus / as sysdba "@database\oracle_setup.sql"
```

If `sqlplus` is not on your PATH:

```powershell
& "C:\Oracle\WINDOWS.X64_193000_db_home\bin\sqlplus.exe" / as sysdba "@database\oracle_setup.sql"
```

This opens and persists `ORCLPDB` and creates:

| Item | Value |
|---|---|
| Username | `railsafe` |
| Password | `RailSafe_2026` |
| Connection | `jdbc:oracle:thin:@//localhost:1521/orclpdb` |
| Tablespace | `USERS` |

**3. Start the app:**

```powershell
cd "C:\Users\lenovo thinkPad\Documents\Default Project\RailSafe"
.\mvnw.cmd spring-boot:run
```

Open <http://localhost:8080>.

Hibernate `ddl-auto=update` creates the nine tables on first run, and `DataInitializer` seeds the demo accounts, five DEMO crossings, one route, one pending report and two notifications. You do **not** need to run `railsafe_schema.sql` manually; it is provided for SQL*Plus users and for reference.

If your Oracle credentials differ, set environment variables before starting:

```powershell
$env:DB_URL = "jdbc:oracle:thin:@//localhost:1521/orclpdb"
$env:DB_USERNAME = "railsafe"
$env:DB_PASSWORD = "your_oracle_password"
.\mvnw.cmd spring-boot:run
```

### Option B — Run immediately with H2 (no Oracle required)

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=h2"
```

H2 data is stored in `./data/railsafe-demo.mv.db`. Seed data is created only when the database is empty. This profile is for a quick smoke test only; the default and documented database is Oracle.

To reset the H2 demo:

```powershell
# Stop the running app first (Ctrl+C), then:
Remove-Item .\data -Recurse -Force
```

### Run from VS Code

1. Install the **Extension Pack for Java** and **Spring Boot Extension Pack** from the Extensions view. The project recommends them automatically (`.vscode/extensions.json`).
2. Choose **File → Open Folder…** and open the `RailSafe` folder (the folder containing `pom.xml`), not its parent.
3. Wait for VS Code to finish **Java: Importing Maven project**. The status bar shows the progress.
4. Start the app in one of these ways:
   - **Run and Debug** view (`Ctrl+Shift+D`) → choose **RailSafe (Oracle)** → press `F5`.
   - **Spring Boot Dashboard** view → right-click `railsafe` → **Run**.
   - Open `src/main/java/com/railsafe/RailSafeApplication.java` → click **Run** above `main`.
   - Or open **Terminal → New Terminal** and run `.\mvnw.cmd spring-boot:run`.
5. Open <http://localhost:8080> in your browser.
6. Stop the app with `Shift+F5` (debug) or `Ctrl+C` in the terminal.

The checked-in launch configurations also include **RailSafe (H2 smoke test)**. They are stored in `.vscode/launch.json` and pass the Oracle connection details as environment variables.

### Run from Eclipse

The project root also contains ready-made Eclipse launch configurations:

| Launch configuration | Mode | What it does |
|---|---|---|
| `RailSafe-H2.launch` | `SIMULATION` (default) | Starts the app with the H2 demo database and the labelled synthetic dataset |
| `RailSafe-RailRadar.launch` | `THIRD_PARTY` | Starts the app with the H2 database in RailRadar mode |

To use the third-party mode:

1. Import the project as an **Existing Maven Project** (`File → Import… → Maven → Existing Maven Projects`).
2. Open **Run → Run Configurations… → Java Application → RailSafe-RailRadar**.
3. On the **Environment** tab, paste your key into `RAILSAFE_RAILRADAR_KEY` and set `RAILSAFE_RAILRADAR_MONTHLY_QUOTA` to your plan's real monthly limit.
4. Run it, then open <http://localhost:8080/live/>.

With an empty key the app still starts and honestly shows `LIVE TRAIN DATA UNAVAILABLE` instead of inventing data.

### Reset the Oracle demo data

Stop the app, then run:

```powershell
sqlplus railsafe/RailSafe_2026@//localhost:1521/orclpdb "@database\oracle_reset.sql"
```

Start the app again. `DataInitializer` recreates the demo accounts and sample rows.

### Build a runnable JAR

```powershell
cd frontend; npm run build; cd ..
.\mvnw.cmd clean package
```

The default profile connects to Aiven MySQL and requires secrets from the
environment. No database, truststore or SUPER_ADMIN password is stored in source.

```powershell
copy deployment\railsafe.env.example deployment\railsafe.env.local
# Edit railsafe.env.local and fill in the rotated secrets.
powershell -ExecutionPolicy Bypass -File deployment\start-railsafe.ps1
```

`deployment/railsafe.env.local` is gitignored. Plain `java -jar` will fail because
`DB_PASSWORD`, `AIVEN_TRUSTSTORE_PASSWORD`, `RAILSAFE_SUPER_ADMIN_EMAIL` and
`RAILSAFE_SUPER_ADMIN_PASSWORD` have no defaults. The optional local H2 profile
also requires `RAILSAFE_SUPER_ADMIN_PASSWORD` and can be started with the same
script plus `--spring.profiles.active=h2` appended to the Java command.

The packaged defaults enable the synthetic simulation, so `/live/` works without
any extra profile or environment variable. Its panels are always labelled
**SIMULATION** / **NOT LIVE**.

All browser write APIs use double-submit CSRF. Fetch `GET /api/auth/csrf` once and
echo the `XSRF-TOKEN` cookie value in the `X-XSRF-TOKEN` header on POST/PUT/PATCH/
DELETE. The shared-secret sensor webhook (`/api/live/sensor/**`) is exempt.

Repeated failed sign-ins from one client are locked out after
`RAILSAFE_LOGIN_MAX_ATTEMPTS` (default 10) for `RAILSAFE_LOGIN_LOCK_MINUTES`
(default 15); further attempts return HTTP `429`. For public HTTPS deployment,
see `deployment/nginx-railsafe.conf` and set `RAILSAFE_COOKIE_SECURE=true`,
`RAILSAFE_HSTS_ENABLED=true`, `RAILSAFE_FORWARD_HEADERS_STRATEGY=framework` and
`RAILSAFE_SERVER_ADDRESS=127.0.0.1` in `railsafe.env.local` **only** for that local
single-host nginx setup.

### Vercel + Render (Git-based deployment)

Root `vercel.json` and root `render.yaml` make this repository ready for automatic
Git deploys:

- Vercel publishes `src/main/resources/static` and rebuilds the React `/live/`
  bundle with `npm ci && npm run build` from `frontend/`.
- Render builds the root `Dockerfile` and runs the Spring Boot API. It uses the
  injected `PORT` automatically; do **not** set `RAILSAFE_SERVER_ADDRESS` there.
- Vercel proxies `/api/*` to Render via the required `BACKEND_URL` environment
  variable, so cookies, CSRF and SSE remain same-origin. No CORS changes are needed.
- `GET /api/health` is a public liveness probe used by Render's health check.

See [`DEPLOYMENT.md`](../DEPLOYMENT.md) at the repository root for the full setup
and verification runbook. Keep all secret values in the platform dashboards, never
in Git.

---

## 7. Demo accounts

| Role | Email | Password | Can do |
|---|---|---|---|
| USER | `user@railsafe.demo` | `User@123` | View gates/routes, report status, save routes, read alerts |
| STAFF | `staff@railsafe.demo` | `Staff@123` | Everything above plus gate writes, status updates, report moderation |
| ADMIN | `admin@railsafe.demo` | `Admin@123` | Everything above plus gate deletion |
| SUPER_ADMIN | `superadmin@railsafe.demo` | Rotated; supplied via `RAILSAFE_SUPER_ADMIN_PASSWORD` | Everything ADMIN can do plus ADMIN account management at `/super-admin.html` |

Passwords are stored as BCrypt hashes. Login is session-based, so use one browser or keep the `JSESSIONID` cookie in API tools.

The SUPER_ADMIN row is created once by `SuperAdminBootstrap` only when no SUPER_ADMIN exists. Change it before any non-demo deployment:

```powershell
$env:RAILSAFE_SUPER_ADMIN_EMAIL = "you@example.org"
$env:RAILSAFE_SUPER_ADMIN_PASSWORD = "a-long-unique-password"
```

There is no public SUPER_ADMIN registration endpoint, and the account-management API can only create ADMIN accounts.

### Seeded crossings (2 OPEN, 2 CLOSED, 1 UNKNOWN)

| Code | Name | Stored status | Notes |
|---|---|---|---|
| RS-DEMO-001 | Demo Crossing – Nagole Canal | OPEN | DEMO |
| RS-DEMO-002 | Demo Crossing – Uppal Junction | CLOSED | DEMO |
| RS-DEMO-003 | Demo Crossing – LB Nagar Ring Road | CLOSED | DEMO |
| RS-DEMO-004 | Demo Crossing – Dilsukhnagar Market | OPEN | DEMO |
| RS-DEMO-005 | Demo Crossing – Kothapet Industrial Area | UNKNOWN | 180 minutes old, deliberately stale |

The demo user also receives one saved route, one pending report and two seeded notifications.

### Synthetic live dataset

The live layer seeds an additional, clearly labelled demo dataset on first start:

| Data | Count |
|---|---|
| Crossings | 20 total (5 original DEMO + 15 synthetic, including Medak, Secunderabad, Hyderabad, Nizamabad, Kazipet, Warangal, Lingampally and Begumpet) |
| Train catalogue | 30 services (Hussainsagar, Charminar, Godavari, Intercity, Satavahana and 20 more) |
| Tracking rows | 50 gate/train assignments |
| Gate history | 30 new rows |
| Demo alternate routes | 30 (at least one per crossing) |
| Notifications | 50 across the three demo accounts |

Seeding is idempotent and is skipped when `RAILSAFE_SYNTHETIC_DATA=false`. The
rows live in `train_master` and `train_tracking`; they are never written to the
authorised `train_snapshots` audit table.

On every startup, `DataInitializer` refreshes the timestamps of crossings that are still untouched `DEMO` records, so the demo keeps showing 2 OPEN / 2 CLOSED / 1 UNKNOWN even after the 120-minute stale threshold. A crossing that staff/admin have updated is no longer DEMO-sourced and is never overwritten.

---

## 8. Guided demo script (5–7 minutes)

1. **Home page** – point out the `DEMO` badges, source labels and the safety banner. No live-data claim is made.
2. **Log in as USER** – use `user@railsafe.demo / User@123` or the demo-account button.
3. **Dashboard** – nearby crossings are sorted by distance. Click a gate to open its detail page.
4. **Gate detail** – show displayed vs stored status, source, verification, last-updated time, history and the report form.
5. **Map** – click **Load demo route**. OSRM returns alternatives; crossings within 150 m are flagged. Note the red CLOSED warning. Save the route and optionally use **Watch this route**.
6. **Submit a report** – report a crossing as CLOSED. It appears as **PENDING**; the app refuses a second pending report for the same gate.
7. **Log in as STAFF** – open Admin → Commuter reports, approve the report. The gate receives a `STAFF / VERIFIED` status, history is written, and the user gets an alert.
8. **Log in as ADMIN** – update a gate status, open its history, add a test crossing, and show that only ADMIN sees the delete action.
9. **Notifications** – mark alerts read and explain that browser notifications are optional; in-app alerts still work.
10. **Safety close** – repeat that none of this is live Indian Railways data and that a physical barrier always wins.

> Suggested order: approve the pending report **after** demonstrating the map, because approval changes the gate’s displayed status.

### Live dashboard demo (add 3–4 minutes)

1. **Open `/live/`** – the shipped defaults run the synthetic simulation. Point at
   the striped `SIMULATION MODE - NOT LIVE RAILWAY DATA` banner and the
   `SYNTHETIC` tags. No value is presented as an official railway prediction.
2. **Show the strict no-data state (optional)** – set `RAILSAFE_LIVE_MODE=unavailable`
   and restart. The grey `LIVE TRAIN DATA UNAVAILABLE` banner proves nothing is
   invented when the demo dataset is disabled.
3. **Select a crossing with trains** – show multiple trains, direction, speed, ETA,
   status and ETA source. Explain the closing/opening countdown preconditions and
   that the barrier is not reopened between two close trains.
4. **Repeat the travel-route demo** – show the verified OSRM route finder, then the
   clearly labelled synthetic demo alternatives for a closed crossing.
5. **Verify with the sensor webhook** – POST a fresh reading with `X-API-Key`,
   refresh, and show `SENSOR / VERIFIED`.
6. **Open the OSRM route finder** – show distance, duration, the closest approach
   to the crossing, and the “no safe detour” result when none exists. Contrast it
   with the synthetic demo routes from step 4.
7. **Log in and open alert history** – show the eight event types and the optional
   channel preferences.
8. **Return to the honesty slide** – this is assistance, not a safety
   certification.

Detailed steps, fallbacks and timings are in
[`docs/PRESENTATION.md`](docs/PRESENTATION.md).

---

## 9. API reference

All APIs are same-origin JSON under `/api`. Authentication uses the `JSESSIONID` cookie.
Login-first applies to the whole application: only `/login.html`, `/register.html` and their
assets are public. All gate, train and live APIs require a session; role-restricted endpoints
additionally require STAFF, ADMIN or SUPER_ADMIN.

| Method | Path | Access | Purpose |
|---|---|---|---|
| POST | `/api/auth/register` | Public | Create a USER account |
| POST | `/api/auth/login` | Public | Log in and create a session |
| GET | `/api/auth/me` | Logged in | Current user |
| POST | `/api/auth/logout` | Public | Invalidate session |
| GET | `/api/gates` | Logged in | List gates; optional `search`, `status` |
| GET | `/api/gates/{id}` | Logged in | Gate detail |
| GET | `/api/gates/nearby?lat=&lng=&radiusKm=` | Logged in | Nearby gates, sorted by distance |
| GET | `/api/gates/{id}/status` | Logged in | Same response as detail |
| GET | `/api/gates/{id}/history` | Logged in | Status history |
| POST | `/api/gates` | STAFF/ADMIN/SUPER_ADMIN | Create a crossing |
| PUT | `/api/gates/{id}` | STAFF/ADMIN/SUPER_ADMIN | Edit metadata |
| PUT | `/api/gates/{id}/status` | STAFF/ADMIN/SUPER_ADMIN | Record status + history |
| DELETE | `/api/gates/{id}` | ADMIN/SUPER_ADMIN | Delete a crossing |
| POST | `/api/reports` | Logged in | Submit a report |
| GET | `/api/reports/mine` | Logged in | Own reports |
| GET | `/api/reports` | STAFF/ADMIN/SUPER_ADMIN | All reports |
| PUT | `/api/reports/{id}/approve` | STAFF/ADMIN/SUPER_ADMIN | Approve and apply status |
| PUT | `/api/reports/{id}/reject` | STAFF/ADMIN/SUPER_ADMIN | Reject with a note |
| GET | `/api/notifications` | Logged in | Own alerts |
| GET | `/api/notifications/unread-count` | Logged in | `{ "count": n }` |
| PUT | `/api/notifications/{id}/read` | Logged in | Mark one read |
| PUT | `/api/notifications/read-all` | Logged in | Mark all read |
| GET | `/api/routes` | Logged in | Own saved routes |
| POST | `/api/routes` | Logged in | Save a route |
| DELETE | `/api/routes/{id}` | Logged in | Delete own route |
| GET | `/api/admin/stats` | STAFF/ADMIN/SUPER_ADMIN | Dashboard counts |
| GET | `/api/live/provider` | Logged in | Provider mode, availability, stale flag and user-facing message |
| GET | `/api/live/gates` | Logged in | Crossing list with verified status, next-train summary, approaching count and expected close/open times |
| GET | `/api/live/dashboard?gateId=` | Logged in | One polling payload: gate, tracking, prediction, provider |
| GET | `/api/gates/{id}/trains` | Logged in | Flat JSON array of trains for one crossing (`GateTrainResponse`) |
| GET | `/api/gates/{id}/prediction` | Logged in | Verified state, closing/opening countdown and next train |
| POST | `/api/live/sensor/gates/{id}/status` | Sensor key | Authorised gate-sensor webhook (disabled unless a key is configured) |
| POST | `/api/routes/alternatives` | Logged in | OSRM alternatives verified against one crossing |
| GET | `/api/notifications/preferences` | Logged in | Own delivery-channel preferences |
| PUT | `/api/notifications/preferences` | Logged in | Update browser/email/SMS preferences |
| GET | `/api/super-admin/admins` | SUPER_ADMIN | List ADMIN and SUPER_ADMIN accounts |
| POST | `/api/super-admin/admins` | SUPER_ADMIN | Create an ADMIN account (role forced to ADMIN) |
| PUT | `/api/super-admin/admins/{id}` | SUPER_ADMIN | Edit/deactivate an ADMIN account; roles are immutable |

### Query and body shapes

**Nearby:** `/api/gates/nearby?lat=17.3725&lng=78.5510&radiusKm=10`

**Register**

```json
{
  "fullName": "Asha Rao",
  "email": "asha@example.com",
  "phone": "9876543210",
  "password": "Secret@123"
}
```

**Gate create/update**

```json
{
  "gateCode": "RS-DEMO-006",
  "name": "Test Crossing",
  "latitude": 17.4,
  "longitude": 78.5,
  "location": "Hyderabad (fictional demo location)",
  "notes": "DEMO record"
}
```

**Status update**

```json
{
  "status": "CLOSED",
  "source": "STAFF",
  "verificationStatus": "VERIFIED",
  "note": "Checked on site"
}
```

**Report**

```json
{
  "gateId": 2,
  "reportedStatus": "OPEN",
  "comment": "Barrier appeared open at 09:15."
}
```

**Saved route**

```json
{
  "name": "Home to work",
  "startLabel": "Secunderabad",
  "startLat": 17.4399,
  "startLng": 78.4983,
  "destLabel": "LB Nagar",
  "destLat": 17.3456,
  "destLng": 78.5525,
  "gateIds": [1, 2, 3, 4, 5]
}
```

**Live provider status** (`GET /api/live/provider`) — strict no-data example

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

With the shipped defaults the same endpoint returns `mode: "SIMULATION"` and a
message beginning `SIMULATION MODE - NOT LIVE RAILWAY DATA`.

`available=false` is the only state in which the dashboard may show
`LIVE TRAIN DATA UNAVAILABLE`. `stale=true` with `available=true` means data exists
but countdowns and ETAs must not be trusted.

**Gate sensor webhook** (`POST /api/live/sensor/gates/{id}/status`)

Requires header `X-API-Key` matching `railsafe.live.sensor-api-key`. The endpoint
returns 404 when no key is configured so its existence is not advertised.

```json
{
  "status": "CLOSED",
  "gateCode": "RS-DEMO-004",
  "observedAt": "2026-11-06T05:20:00Z",
  "note": "Barrier down, confirmed by trackside sensor"
}
```

Readings older than 15 minutes are rejected with 400. An accepted reading writes
`status_source=SENSOR` and `verification_status=VERIFIED`.

**Verified alternative route** (`POST /api/routes/alternatives`)

```json
{
  "gateId": 1,
  "startLat": 17.3800,
  "startLng": 78.5420,
  "destLat": 17.3650,
  "destLng": 78.5600
}
```

The response contains `available`, `message`, `source`, `directRouteAvoidsGate`,
`avoidRadiusMeters`, an `original` route and a list of `alternatives`. Every
distance, duration and polyline point comes from OSRM. `alternatives` is empty
when the direct route already avoids the crossing or when OSRM returns no safe
detour. The server never fabricates a route.

**Gate timing** (`GET /api/gates/{id}/timing`)

Returns `verifiedStatus`, `simulation`, absolute `estimatedCloseAt` /
`estimatedOpenAt` targets, snapshot `closingCountdownSeconds` /
`openingCountdownSeconds`, approaching/waiting counts, the relevant trains and the
configured pre-closing, crossing and safety buffers. The absolute targets are
authoritative, so a refreshed browser recalculates the same countdown.

**Synthetic demo routes** (`GET /api/gates/{id}/alternate-routes`)

Returns `available`, `synthetic: true`, an explicit not-verified message and one
or more seeded `routes` with distance, duration, extra distance, a recommended
flag, Google Maps / OpenStreetMap links and geometry. These rows are demo data,
not OSRM results.

**Train detail** (`GET /api/trains/{trainNumber}`)

Returns one synthetic catalogue train and its current simulated gate assignments,
for example `/api/trains/12727` for Godavari Express.

**Notification list** (`GET /api/notifications`) — logged in

Returns the signed-in user's history across train, gate and route event types.

**Notification preferences** (`PUT /api/notifications/preferences`)

```json
{
  "browserEnabled": true,
  "emailEnabled": false,
  "smsEnabled": false
}
```

The response also reports `emailChannelConfigured` and `smsChannelConfigured`.
Optional channels cannot be enabled until the server has been configured.

### PowerShell API test with a session

```powershell
$base = "http://localhost:8080"
$session = New-Object Microsoft.PowerShell.Commands.WebRequestSession

# Login-first: authenticate before requesting any gate or train data.
Invoke-RestMethod "$base/api/auth/login" -Method Post -WebSession $session `
  -ContentType "application/json" `
  -Body '{"email":"user@railsafe.demo","password":"User@123"}'

# Gate list (now requires the session cookie)
Invoke-RestMethod "$base/api/gates" -WebSession $session | Format-Table gateCode, status, displayStatus, stale

# Live and synthetic demo APIs
$gate = (Invoke-RestMethod "$base/api/gates" -WebSession $session)[0]
Invoke-RestMethod "$base/api/gates/$($gate.id)/trains" -WebSession $session | Select-Object trainNumber, trainName, status, gateStatus
Invoke-RestMethod "$base/api/gates/$($gate.id)/timing" -WebSession $session | Select-Object verifiedStatus, estimatedCloseAt, estimatedOpenAt
Invoke-RestMethod "$base/api/gates/$($gate.id)/alternate-routes" -WebSession $session | Select-Object available, synthetic
Invoke-RestMethod "$base/api/trains/12727" -WebSession $session | Select-Object trainNumber, trainName, synthetic

# Authenticated user calls
Invoke-RestMethod "$base/api/auth/me" -WebSession $session
Invoke-RestMethod "$base/api/notifications/unread-count" -WebSession $session
Invoke-RestMethod "$base/api/routes" -WebSession $session

# Submit a report (change gateId if needed)
Invoke-RestMethod "$base/api/reports" -Method Post -WebSession $session `
  -ContentType "application/json" `
  -Body '{"gateId":2,"reportedStatus":"CLOSED","comment":"API test report"}'

# Super Admin account management (log in as superadmin@railsafe.demo first)
$superSession = New-Object Microsoft.PowerShell.Commands.WebRequestSession
Invoke-RestMethod "$base/api/auth/login" -Method Post -WebSession $superSession `
  -ContentType "application/json" `
  -Body (@{ email = $env:RAILSAFE_SUPER_ADMIN_EMAIL; password = $env:RAILSAFE_SUPER_ADMIN_PASSWORD } | ConvertTo-Json -Compress)
Invoke-RestMethod "$base/api/super-admin/admins" -WebSession $superSession
```

### curl.exe example

```bat
curl.exe -c cookies.txt -H "Content-Type: application/json" ^
  -d "{\"email\":\"user@railsafe.demo\",\"password\":\"User@123\"}" ^
  http://localhost:8080/api/auth/login

curl.exe -b cookies.txt http://localhost:8080/api/auth/me
curl.exe -b cookies.txt http://localhost:8080/api/routes
```

### Error format

```json
{
  "timestamp": "2026-11-05T11:07:11",
  "status": 401,
  "error": "Unauthorized",
  "message": "Invalid email or password.",
  "path": "/api/auth/login",
  "fieldErrors": null
}
```

Common statuses:

- `400` validation error or invalid coordinates
- `401` not logged in / bad credentials
- `403` insufficient role
- `404` gate, report or route not found
- `409` duplicate email, duplicate gate code, or already-pending report

---

## 10. Configuration

| Property | Default | Meaning |
|---|---|---|
| `RAILSAFE_SERVER_PORT` | `8080` | HTTP port |
| `RAILSAFE_SERVER_ADDRESS` | `0.0.0.0` | Bind address; use `127.0.0.1` behind a reverse proxy |
| `DB_URL` | Aiven MySQL TLS URL | Optional JDBC URL override |
| `DB_USERNAME` / `DB_PASSWORD` | `avnadmin` / none (required) | Database credentials from `deployment/railsafe.env.local` |
| `AIVEN_TRUSTSTORE_PASSWORD` | none (required) | Password for the bundled Aiven CA truststore |
| `RAILSAFE_DDL_AUTO` | `update` | Hibernate schema mode; set `validate` after confirming schema parity |
| `RAILSAFE_COOKIE_SECURE` | `false` | Set `true` over HTTPS to mark session/CSRF cookies Secure |
| `RAILSAFE_HSTS_ENABLED` | `false` | Set `true` over HTTPS to emit HSTS |
| `RAILSAFE_FORWARD_HEADERS_STRATEGY` | `none` | Set `framework` behind a trusted TLS proxy (for real client IP) |
| `RAILSAFE_LOGIN_MAX_ATTEMPTS` | `10` | Failed logins before a temporary lockout |
| `RAILSAFE_LOGIN_LOCK_MINUTES` | `15` | Lockout duration |
| `railsafe.gate.stale-minutes` | `120` | Age at which displayed status becomes UNKNOWN |
| `server.servlet.session.timeout` | `8h` | Login session lifetime |
| `server.servlet.session.cookie.http-only` | `true` | JavaScript cannot read the session cookie |
| `server.servlet.session.cookie.same-site` | `lax` | Defense in depth alongside token-based CSRF |
| `railsafe.super-admin.email` | `superadmin@railsafe.demo` | Bootstrap email for the first SUPER_ADMIN |
| `railsafe.super-admin.password` | none (required) | Bootstrap password (BCrypt-hashed); supplied via `RAILSAFE_SUPER_ADMIN_PASSWORD` |
| `railsafe.live.provider-mode` | `simulation` | `unavailable`, `http` or `simulation` |
| `railsafe.live.feed-url` | empty | Authorised HTTP JSON provider endpoint |
| `railsafe.live.feed-api-key` | empty | Provider credential, sent using the configured header |
| `railsafe.live.feed-api-key-header` | `X-API-Key` | Header name used for the provider credential |
| `railsafe.live.provider-name` | `Authorised railway feed` | Name shown when a provider is configured |
| `railsafe.live.cache-seconds` | `20` | Provider cache and advertised dashboard polling interval |
| `railsafe.live.feed-stale-seconds` | `300` | Age at which provider data is marked stale |
| `railsafe.live.simulation-acknowledged` | `true` | Second switch required by simulation mode |
| `railsafe.live.simulation-notifications` | `true` | Allows simulated trains to generate demo alerts |
| `railsafe.live.synthetic-data-enabled` | `true` | Seeds the 20/30/50/30/30/50 demo dataset |
| `railsafe.live.simulation-cycle-seconds` | `1800` | Repeating simulation cycle used to project absolute ETAs |
| `railsafe.live.opening-buffer-seconds` | `60` | Extra safety buffer before a simulated barrier reopens |
| `railsafe.live.reopen-gap-seconds` | `300` | Do not reopen the barrier if another train is due inside this gap |
| `railsafe.live.sensor-api-key` | empty | Enables the gate-sensor webhook when set |
| `railsafe.live.alert-interval-millis` | `60000` | Server-side live alert polling interval |
| `railsafe.live.alert-initial-delay-millis` | `15000` | Delay before the first alert evaluation |
| `railsafe.live.osrm-base-url` | public OSRM demo | Replace with a self-hosted or licensed OSRM instance |
| `railsafe.live.route-avoid-radius-meters` | `150` | A detour must stay at least this far from the crossing |
| `railsafe.live.route-max-alternatives` | `3` | Maximum verified alternatives requested/returned |
| `railsafe.live.route-cache-seconds` | `60` | Route result cache lifetime |
| `railsafe.live.snapshot-retention-hours` | `72` | Audit snapshot retention before cleanup |
| `railsafe.notifications.email.enabled` | `false` | Enables optional email delivery |
| `railsafe.notifications.sms.enabled` | `false` | Enables optional SMS delivery |
| `railsafe.notifications.sms.webhook-url` | empty | SMS gateway endpoint when SMS is enabled |
| `spring.mail.*` | empty | Standard Spring mail settings when email is enabled |

The H2 profile overrides the datasource only; all business rules remain the same.

### Simulation, default mode and the strict no-data mode

The shipped defaults enable the synthetic simulation and seed the demo dataset, so
`/live/` is useful immediately:

```powershell
java -jar target\railsafe-1.0.0.jar --spring.profiles.active=h2
```

To return to the strict no-data behaviour, disable both the provider and the
dataset:

```powershell
$env:RAILSAFE_LIVE_MODE = 'unavailable'
$env:RAILSAFE_SYNTHETIC_DATA = 'false'
java -jar target\railsafe-1.0.0.jar --spring.profiles.active=h2
```

The `simulation` profile still works and sets the same values explicitly:

```powershell
java -jar target\railsafe-1.0.0.jar --spring.profiles.active=h2,simulation
```

The dashboard always shows the striped
`SIMULATION MODE - NOT LIVE RAILWAY DATA` banner in this mode and never writes
simulated trains to `train_snapshots`. Synthetic demo routes are stored in
`route_alternatives` with `synthetic=true` and are never presented as
OSRM-verified.

### Optional MySQL profile

Oracle 19c is the default and verified database. MySQL 8 is only an optional
profile for teams without Oracle:

```powershell
java -jar target\railsafe-1.0.0.jar --spring.profiles.active=mysql
```

Set `DB_URL`, `DB_USERNAME` and `DB_PASSWORD` for your MySQL instance and run
`database/railsafe_schema_mysql.sql` if you manage the schema manually.

---

## 11. How route assistance works

1. The user chooses a start and destination (geolocation, place search, map click, or URL parameters).
2. The browser calls the public OSRM demo server with `alternatives=true`.
3. Each returned route geometry is drawn on the map.
4. `GeoUtils` calculates the distance from every gate to the route polyline.
5. A gate within **150 m** is listed as “near route”.
6. If a near-route gate is displayed as CLOSED, the route card shows a red warning.
7. The app only avoids claiming “no closed gates” when the analysis actually found none.

Limitations:

- OSRM and Nominatim are public demo services; they may be slow, rate-limited or unavailable.
- A route check is not a safety certification. Gates can close after the last report.
- Saved routes store gate IDs and endpoints, not a frozen route geometry; recalculating can produce different alternatives.

### Route URL parameters

```text
map.html?focusGate=3
map.html?demo=1
map.html?startLat=17.44&startLng=78.49&startLabel=Start&destLat=17.35&destLng=78.55&destLabel=Dest
map.html?destLat=17.35&destLng=78.55&destLabel=Dest
```

### Verified alternatives on the live dashboard

The React dashboard uses a different, stricter route check than the legacy map
page. It asks OSRM for the direct route plus alternatives, then measures the
distance from the selected crossing to every polyline segment. A route is shown
as an alternative only when it stays outside
`railsafe.live.route-avoid-radius-meters`.

- If the direct route already stays outside the radius, the response says no
  detour is needed and lists no alternatives. This is correct, not a failure.
- If the direct route passes near the crossing, only verified detours are listed.
- If OSRM returns no safe detour, the dashboard says so and tells the driver to
  wait or follow an official diversion. It never invents a route.
- Each option includes OSRM distance, duration, extra distance/time, the closest
  approach to the crossing, a navigation URL and a geometry for the map.

> The five demo crossings use fictional coordinates that may not lie exactly on a
> road. For a route demonstration, use a start/destination pair that straddles a
> crossing, or raise the avoid radius temporarily. A tested example is in
> [`docs/LIVE_SYSTEM.md`](docs/LIVE_SYSTEM.md).

---

## 12. Notifications

- Status changes create notifications for enabled `USER` accounts (demo simplification).
- A unique `dedupe_key` prevents duplicate seed/status alerts.
- The frontend polls every 20 seconds while a page is open.
- Browser notifications use the Notification API and require permission; in-app alerts always work.
- “Watch this route” stores the route in `localStorage` under `railsafe.watchedRoute` and checks it while the map page remains open.

### Live-layer alerts

`LiveAlertScheduler` evaluates crossings on a server-side interval (default 60 s)
and can create these additional notification types:

| Type | Trigger |
|---|---|
| `TRAIN_APPROACHING` | a usable train ETA enters the approach window while the gate is verified `OPEN` |
| `GATE_CLOSING_SOON` | verified `OPEN` and the estimated closing countdown enters the warning window |
| `GATE_CLOSED` | the verified status changes to `CLOSED` |
| `GATE_OPENING_SOON` | verified `CLOSED` and the estimated opening countdown enters the warning window |
| `GATE_OPENED` | the verified status changes back to `OPEN` |
| `ALTERNATIVE_ROUTE` | a verified safe detour becomes available for a closed crossing |

Safety rules enforced by the backend:

- No closing countdown is shown unless the gate is **verified OPEN** and the
  approaching-train data is fresh and usable.
- No opening countdown is shown unless the gate is **verified CLOSED**.
- Alerts are suppressed when the provider is unavailable or stale, and
  simulation alerts are off unless `railsafe.live.simulation-notifications=true`.
- `dedupe_key` uses the gate, event and data window so the same event cannot
  notify the same user twice.

### Delivery channels

| Channel | Default | How it is enabled |
|---|---|---|
| In-app history | On | `notifications` table; always available |
| Browser | On after user permission | Notification API from the open dashboard page |
| Email | Off | `spring.mail.*` plus `railsafe.notifications.email.enabled=true`, then user opt-in |
| SMS | Off | `railsafe.notifications.sms.webhook-url` pointing at an approved gateway, then user opt-in |

Browser alerts only work while a RailSafe page is open; there is no service
worker or push server. Email/SMS failures are logged and never break the in-app
alert.

This is polling, not a push service. A production system would use WebSocket/SSE, user-specific subscriptions and a proper notification queue.

---

## 13. Security notes

Implemented:

- BCrypt password hashing.
- Session-based authentication with `HttpOnly` cookie.
- Login-first access: only login/registration assets are public, so gate and train data never render or load before authentication.
- Backend role checks with Spring Security and `@PreAuthorize`; hidden navigation is never the only control.
- SUPER_ADMIN is bootstrapped once from environment credentials; there is no public SUPER_ADMIN registration and role changes are rejected by the account API.
- DTOs prevent password hashes and internal entities from reaching the browser.
- Validation on registration, gates, reports, status updates and routes.
- Consistent JSON 401/403 responses; expired sessions redirect to the login page.
- Report ownership checks; route ownership checks.
- ADMIN/SUPER_ADMIN-only gate deletion.
- Live provider credentials are read from configuration/environment and never sent to the browser.
- The sensor webhook requires `X-API-Key`; when no key is configured it returns 404.
- Sensor readings older than 15 minutes are rejected.
- Simulation cannot start unless both the mode and acknowledgement flags are set; the shipped demo sets both explicitly.
- The synthetic dataset and demo routes are always labelled and are never written to the authorised `train_snapshots` audit table.
- The live dashboard is read-only; no endpoint lets a browser set a gate status as a “live sensor”.

Deliberately simplified for the hackathon:

- No email verification or password reset.
- Login (not registration) has an in-memory per-client lockout; add edge rate limiting for public registration and multi-node setups.
- Demo notification broadcast reaches all USER accounts.
- The H2 profile is for local demos only.
- Public OSRM/Nominatim usage must respect their usage policies.
- The sensor key is a shared secret, not per-device mutual TLS.

For production: deploy `deployment/nginx-railsafe.conf` with real certificates and the HTTPS env flags above, keep CSRF tokens enabled, add edge rate limiting, move secrets from the local env file into a managed secret store, adopt versioned migrations (Flyway/Liquibase), configure backups/monitoring, and obtain an official data agreement before displaying any real railway information. Audit logging is already in place.

---

## 14. Troubleshooting

| Problem | Fix |
|---|---|
| `mvn` is not recognised | Use `.\mvnw.cmd` (wrapper included) |
| Port 8080 already in use | `netstat -ano \| findstr :8080`, then stop the PID or change `server.port` |
| `ORA-01017: invalid username/password; logon denied` | Use `railsafe` / `RailSafe_2026`, or update `DB_USERNAME` / `DB_PASSWORD` |
| `ORA-12541: TNS:no listener` | Start `OracleOraDB19Home1TNSListener`; check port 1521 |
| `ORA-12514: TNS:listener does not currently know of service` | Open the PDB: `sqlplus / as sysdba`, then `ALTER PLUGGABLE DATABASE ORCLPDB OPEN;` |
| `ORA-28000: the account is locked` | As SYSDBA: `ALTER USER railsafe ACCOUNT UNLOCK;` |
| `ORA-00942: table or view does not exist` | Confirm you are connected to `orclpdb` and that `database/oracle_setup.sql` created the RAILSAFE schema |
| `ORA-01950: no privileges on tablespace 'USERS'` | As SYSDBA: `ALTER USER railsafe QUOTA UNLIMITED ON USERS;` |
| VS Code cannot find the main class | Open the `RailSafe` folder itself, wait for Maven import, then run `.\mvnw.cmd clean compile` |
| VS Code shows “Build path is incomplete” | Run **Java: Clean Java Language Server Workspace**, then reopen the folder |
| H2 database is locked | Stop every other app instance; delete `data/*.lock.db` after stopping |
| All gates show UNKNOWN | Their last update is older than 120 minutes; restart the app to refresh untouched DEMO gates, or update status as STAFF |
| Map is blank | Check internet access and the browser console; OSM tiles/CDN may be blocked |
| Route stays at “Calculating…” | OSRM may be rate-limited or blocked; retry later |
| Map clicks do nothing after login | Make sure you opened `http://localhost:8080`, not a `file://` URL |
| API returns 401 | Log in; API tools must keep the `JSESSIONID` cookie. A 401 from `/api/auth/me` on a public page simply means “not logged in” and is expected. |
| API returns 403 | Use a STAFF/ADMIN account for writes/moderation |
| Demo accounts missing | Stop the app, run `database/oracle_reset.sql`, or check the Oracle connection |
| Emoji/em dash looks garbled in PowerShell | It is a console encoding artefact; the UTF-8 JSON response is correct |

### Reset only the H2 demo

```powershell
# Stop the app first
Remove-Item .\data -Recurse -Force
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=h2"
```

### Verify the backend build

```powershell
.\mvnw.cmd clean package
```

---

## 15. Testing checklist

- [x] Register, login, `/me`, logout, duplicate email, validation errors
- [x] Login-first: pages, built React bundle and every data API return 401/redirect before login
- [x] Authenticated gate list/detail/nearby/history
- [x] Role enforcement: USER cannot create gates or read admin stats; STAFF cannot delete gates
- [x] SUPER_ADMIN can read every page/API; ADMIN receives 403 from `/api/super-admin/**` and `/super-admin.html`
- [x] SUPER_ADMIN can create, edit and deactivate an ADMIN account; no endpoint can create a SUPER_ADMIN or change a role
- [x] Logout invalidates the session and protected data is no longer reachable
- [x] Staff status update writes history and creates an alert
- [x] Staff creates/edits a gate; ADMIN deletes it
- [x] Report submit, duplicate-pending rejection, approve/reject
- [x] Saved route create/list/delete
- [x] Notification list, unread count, mark one/all read
- [x] Map renders five demo pins with correct statuses
- [x] OSRM demo route returns two alternatives and flags a CLOSED gate within 150 m
- [x] Gate detail labels source, verification, timestamp and stale state
- [x] Browser console has no uncaught JavaScript errors during the tested flows
- [x] Oracle 19c schema created, seeded, and every REST flow verified against the running app
- [x] `/live/` page and its built JS/CSS assets return 200 from the packaged JAR
- [x] Shipped default runs the labelled synthetic simulation and seeds 20 crossings, 30 trains, 50 tracking rows, 30 history rows, 30 demo routes and 50 notifications
- [x] `RAILSAFE_LIVE_MODE=unavailable` returns `available=false` with no invented trains
- [x] Simulation requires both switches, reports `SIMULATION`, and shows the mandated label
- [x] Multi-train list with APPROACHING / NEAR_GATE / CROSSING / PASSED, ETAs, speeds and countdowns
- [x] Closing and opening countdowns target absolute timestamps, never go negative and survive refresh
- [x] Barrier is chained across close trains rather than reopening between them
- [x] Required APIs `/api/gates/{id}/timing`, `/alternate-routes` and `/api/trains/{trainNumber}` respond with labelled synthetic data
- [x] RailRadar adapter passes six offline tests: gate matching, observed/predicted/derived ETAs, missing key, rejected key, exhausted budget and stale-cache fallback
- [x] RailRadar mode is labelled `THIRD_PARTY` everywhere and never presented as official Indian Railways data
- [x] Closing countdown is withheld when the gate is not verified OPEN
- [x] Opening countdown appears only for a verified CLOSED gate
- [x] Sensor webhook rejects a missing/wrong key (404) and a stale reading (400)
- [x] Valid sensor reading sets `SENSOR / VERIFIED` and changes the prediction
- [x] OSRM verification returns a safe detour when one exists and refuses to invent one when it does not
- [x] Notification history and browser/email/SMS preferences work for a logged-in user
- [x] Existing static pages, navigation, map and authentication still work after the upgrade

> The checked boxes describe the local verification performed for this project. Re-run the checklist after changing code or configuration. Documented scenario results are in [`docs/LIVE_SYSTEM.md`](docs/LIVE_SYSTEM.md).

---

## 16. Future enhancements

- Official/authorised railway data integration (legal agreement required).
- WebSocket or SSE push instead of polling.
- Service worker or push server so browser alerts work with the tab closed.
- Per-user route watches and notification subscriptions.
- Verified email/SMS destinations and delivery receipts.
- Crowd-report trust scoring and abuse detection.
- Self-hosted OSRM and tile servers for production reliability.
- Predictive delay modelling from historical runs, clearly labelled as estimates.
- Admin user management and audit dashboards.
- Flyway migrations and integration tests with Testcontainers.
- Mobile PWA with offline caching.
- Multilingual UI (Telugu/Hindi/English).

---

## 17. Safety statement

RailSafe demonstrates a reporting workflow. It does not know whether a real crossing is open. A reported OPEN status can be wrong or stale. The only safe signal is the physical barrier, the railway signal and instructions from authorised railway staff.

**Never bypass a closed gate. Never drive around a barrier. Never rely on this demo for a real journey.**
