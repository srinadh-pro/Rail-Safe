# RailSafe deployment runbook — Vercel (frontend) + Render (backend)

The repository is a two-project monorepo. Each side is self-contained and deploys independently:

- **`frontend/`** — all browser assets: original HTML/CSS/JS pages in `public/`,
  React dashboard source in `src/`, and its own `vercel.json`. Builds to `frontend/dist/`.
- **`backend/`** — the complete Spring Boot API: `src/`, `pom.xml`, `Dockerfile`,
  `.dockerignore`, SQL scripts, docs and the gitignored local env file.
- **`render.yaml`** — root Blueprint that points Render at `backend/` via `rootDir`.

Vercel proxies `/api/*` to Render, so the browser sees one origin. `JSESSIONID`,
the `XSRF-TOKEN` cookie, CSRF headers, login redirects and SSE keep working without
CORS or `SameSite=None` changes.

No secrets are stored in Git. Set them in the Render and Vercel dashboards.

---

## 1. Repository layout used by the platforms

| Path | Purpose |
|---|---|
| `frontend/vercel.json` | Vercel install/build commands, `dist/` output, `/api/*` proxy, `/live/` rewrite, API no-cache header |
| `frontend/.vercelignore` | Keeps `node_modules`, local env files and logs out of the upload |
| `frontend/public/` | All original static pages, CSS, JS and favicon (copied into `dist/` on build) |
| `frontend/src/` | React source for the `/live/` dashboard (built into `dist/live/`) |
| `backend/Dockerfile` | Multi-stage Maven build + small JRE 17 runtime image |
| `backend/.dockerignore` | Keeps `target`, local data/secrets and logs out of the image context |
| `render.yaml` (repo root) | Render Blueprint; `rootDir: backend`, Docker build and health check |
| `backend/src/main/java/com/railsafe/controller/HealthController.java` | Public `GET /api/health` probe |

The backend reads Render's injected `PORT` automatically:

```properties
server.port=${PORT:${RAILSAFE_SERVER_PORT:8080}}
server.address=${RAILSAFE_SERVER_ADDRESS:0.0.0.0}
```

Do **not** set `RAILSAFE_SERVER_ADDRESS=127.0.0.1` on Render.

---

## 2. Deploy the backend on Render

1. Sign in to [Render](https://dashboard.render.com/).
2. Click **New → Blueprint**.
3. Connect GitHub and select `srinadh-pro/Rail-Safe`, branch `main`.
4. Render detects the root `render.yaml`. The service uses `rootDir: backend`, so
   its Dockerfile and build context are both `backend/`.
   If you create a **manual** Docker web service instead, set its **Root Directory**
   to `backend`, **Dockerfile Path** to `./Dockerfile` and **Build Context** to `.`.
5. Fill the prompted secret values:

   | Variable | Value |
   |---|---|
   | `DB_URL` | Aiven service URI or full JDBC URL (see note below) |
   | `DB_USERNAME` | Aiven user (default in Blueprint: `avnadmin`) |
   | `DB_PASSWORD` | Rotated Aiven database password |
   | `AIVEN_TRUSTSTORE_PASSWORD` | Password for the bundled Aiven CA truststore |
   | `RAILSAFE_SUPER_ADMIN_EMAIL` | Bootstrap SUPER_ADMIN email |
   | `RAILSAFE_SUPER_ADMIN_PASSWORD` | Strong bootstrap SUPER_ADMIN password |

   `DB_URL` may be either the short `mysql://user:pass@host:port/db` URI copied from
   Aiven or a full `jdbc:mysql://...` URL. The app normalises the short form at
   startup (including `ssl-mode=REQUIRED` → `VERIFY_CA`) before the MySQL driver sees
   it. Credentials found in the short URI are kept out of the JDBC URL; dashboard
   `DB_USERNAME`/`DB_PASSWORD` take precedence, and the truststore password is
   passed separately as a driver property via `AIVEN_TRUSTSTORE_PASSWORD`.

6. Keep the non-secret Blueprint values (`RAILSAFE_FORWARD_HEADERS_STRATEGY=framework`,
   `RAILSAFE_COOKIE_SECURE=true`, `RAILSAFE_HSTS_ENABLED=true`,
   `RAILSAFE_LIVE_MODE=simulation`, `RAILSAFE_DEMO_ACCOUNTS_ENABLED=false`,
   `RAILSAFE_DDL_AUTO=update`).
7. Allow Render's outbound IPs in the Aiven service allowlist if your Aiven plan
   restricts access. Connection failures at startup are usually this, not Docker.
8. Check `https://<your-service>.onrender.com/api/health`:

   ```json
   {"status":"UP","service":"railsafe"}
   ```

9. Copy the Render URL (for example `https://railsafe-backend.onrender.com`) for
   Vercel's `BACKEND_URL`.

### Render operational notes

- Keep **one instance**: sessions, CSRF state, login lockout and SSE subscribers
  are in memory (`numInstances: 1` is set in the Blueprint).
- The first request after an idle free/starter plan can be slow; allow extra time.
- Render's disk is ephemeral. JSON cache/quota files under `backend/data/` are
  non-critical for the labelled simulation mode.

---

## 3. Deploy the frontend on Vercel

1. Sign in to [Vercel](https://vercel.com/).
2. **Add New → Project** and import `srinadh-pro/Rail-Safe`.
3. **Root Directory: `frontend`** (click *Edit* in Build & Deploy). This is required:
   `vercel.json` lives inside `frontend/`, and its commands are relative to that
   folder. Selecting the repository root or `RailSafe` causes
   `cd: ... No such file or directory` during install.
4. Framework preset: **Other** (`vercel.json` sets `"framework": null`). Settings
   come from `frontend/vercel.json`:
   - Install: `npm ci`
   - Build: `npm run build` (Vite builds `dist/live/`, then copies `public/` into `dist/`)
   - Output: `dist`
5. Add one Project Environment Variable for every environment you deploy:

   | Name | Value |
   |---|---|
   | `BACKEND_URL` | Render service URL, no trailing slash, e.g. `https://railsafe-backend.onrender.com` |

   Add it to **Production** and **Preview**. If it is missing, Vercel leaves the
   literal `${BACKEND_URL}` in the proxy destination and API calls fail.

6. Click **Deploy**. The published site contains every original page at the root
   and the React dashboard at `/live/`.

---

## 4. Run and verify locally

### Backend (Spring Boot API)

```powershell
cd backend
# Local H2 profile needs no MySQL. Set any local-only bootstrap password:
$env:RAILSAFE_SUPER_ADMIN_PASSWORD = 'LocalDemoOnly@123'
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=h2"
```

The API listens on `http://localhost:8080`. Check `http://localhost:8080/api/health`.
For an Aiven-backed run, load the gitignored `backend/deployment/railsafe.env.local`
into your shell instead and omit the `h2` profile.

### Frontend (static pages + React dashboard)

```powershell
cd frontend
npm ci
npm run build
npm run preview  # http://localhost:4173 — same layout as Vercel
# or, for hot reload while editing React:
npm run dev      # http://localhost:5173
```

`npm run preview` mirrors production: `/` is the original home page and `/live/`
is the React dashboard. In `npm run dev`, Vite serves its React shell at `/`, so
use `/live/` for the dashboard. Both proxies forward `/api` to `localhost:8080`.

### Pre-push checks

```powershell
cd backend
.\mvnw.cmd -B -ntp test
.\mvnw.cmd -B -ntp -DskipTests package

cd ../frontend
npm ci
npm run build
```

If Docker is installed, verify the production image from the backend folder:

```powershell
cd backend
docker build -t railsafe-backend .
```

---

## 5. Verify the split deployment

On the Vercel domain, check:

| URL | Expected |
|---|---|
| `/api/health` | `{"status":"UP","service":"railsafe"}` from Render, no URL change |
| `/` | RailSafe home page |
| `/login.html` | Login page |
| `/live/` | React dashboard (login redirects to `/login.html?next=live/`) |
| `/dashboard.html`, `/admin.html`, `/staff.html`, `/super-admin.html` | Role-gated pages |
| `/favicon.svg`, `/css/styles.css`, `/js/api.js` | Static assets from Vercel |

Then sign in and confirm:

- CSRF bootstrap writes `XSRF-TOKEN` on the Vercel origin.
- Write actions (report, route save, gate update) succeed with `X-XSRF-TOKEN`.
- SSE stream `/api/live/stream` stays connected.
- Logout returns to `/login.html` and clears the session.
- The simulation UI remains labelled **SIMULATION** / **NOT LIVE**.

---

## 6. Git workflow

```powershell
cd "C:\Users\lenovo thinkPad\Documents\Default Project"
git status
git add -A
git commit -m "Split frontend and backend into independent deployables"
git push origin main
```

Pushing `main` triggers the Render Blueprint and the Vercel project. A change under
`frontend/` only rebuilds Vercel; a change under `backend/` only rebuilds Render.
Never add `backend/deployment/railsafe.env.local`, `.env` files or any secret value
to Git — they are gitignored.

---

## 7. Why no CORS configuration was added

All browser URLs are root-relative (`/api/...`, `/login.html`, `/live/`). Vercel
forwards `/api/*` to Render under the same visible origin. Cookies remain same-site
and are stored for the Vercel domain, and the existing double-submit CSRF flow is
unchanged. Cross-origin CORS, `SameSite=None`, or a frontend API base URL would be
unnecessary and would weaken the setup.

## 8. Known constraints

- No real railway feed is connected; the default provider is explicitly simulated.
- The public OSRM demo server is used for alternate routes; production should use a
  dedicated routing service.
- Login lockout is in-memory and per instance; keep one backend instance or add an
  edge rate limiter if you scale out.
- `spring.jpa.hibernate.ddl-auto=update` is the convenience default. Switch to
  `validate` after confirming the Aiven schema, and adopt versioned migrations before
  production releases.
