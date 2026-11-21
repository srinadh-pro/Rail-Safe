# RailSafe deployment runbook — Vercel + Render

This repository is configured for Git-based deployment:

- **Vercel** publishes the existing static UI from `RailSafe/src/main/resources/static`
  and rebuilds the React `/live/` bundle from `RailSafe/frontend`.
- **Render** builds and runs the Spring Boot API from the root `Dockerfile`.
- Vercel proxies `/api/*` to Render, so the browser sees one origin. This keeps
  `JSESSIONID`, the `XSRF-TOKEN` cookie, CSRF headers, login redirects and SSE
  working without CORS or `SameSite=None` changes.

No secrets are stored in Git. Set them in the Render and Vercel dashboards.

---

## 1. Repository layout used by the platforms

| File | Purpose |
|---|---|
| `vercel.json` (repo root) | Vercel install/build commands, static output directory, `/api/*` proxy, `/live/` rewrite, API no-cache header |
| `render.yaml` (repo root) | Render Blueprint for the Docker web service |
| `Dockerfile` (repo root) | Multi-stage Maven build + small JRE runtime image |
| `.dockerignore`, `.vercelignore` | Keep secrets, local data and build caches out of uploads |
| `RailSafe/src/main/java/com/railsafe/controller/HealthController.java` | Public `GET /api/health` liveness probe |

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
4. Render detects root `render.yaml`. Name the Blueprint/environment as you like.
5. Fill the prompted secret values:

   | Variable | Value |
   |---|---|
   | `DB_URL` | Full JDBC URL, e.g. `jdbc:mysql://mysql-e1e61fd-railsafe.j.aivencloud.com:17447/defaultdb?sslMode=VERIFY_CA&trustCertificateKeyStoreUrl=classpath:aiven-truststore.p12&trustCertificateKeyStoreType=PKCS12&serverTimezone=UTC&useUnicode=true&characterEncoding=utf8` |
   | `DB_USERNAME` | Aiven user (default in Blueprint: `avnadmin`) |
   | `DB_PASSWORD` | Aiven database password |
   | `AIVEN_TRUSTSTORE_PASSWORD` | Password for the bundled Aiven truststore |
   | `RAILSAFE_SUPER_ADMIN_EMAIL` | Bootstrap SUPER_ADMIN email |
   | `RAILSAFE_SUPER_ADMIN_PASSWORD` | Strong bootstrap SUPER_ADMIN password |

   `DB_URL` may be either a full `jdbc:mysql://...` URL or the short
   `mysql://user:pass@host:port/db` URI copied from Aiven. The app normalises the
   short form at startup (including `ssl-mode=REQUIRED` → `VERIFY_CA`) before the
   MySQL driver sees it, so both work. Credentials found in the short URI are
   kept out of the JDBC URL; `DB_USERNAME`/`DB_PASSWORD` from the dashboard take
   precedence, and the truststore password is passed as a driver property via
   `AIVEN_TRUSTSTORE_PASSWORD`.

6. Keep the non-secret values from the Blueprint:
   - `RAILSAFE_FORWARD_HEADERS_STRATEGY=framework`
   - `RAILSAFE_COOKIE_SECURE=true`
   - `RAILSAFE_HSTS_ENABLED=true`
   - `RAILSAFE_LIVE_MODE=simulation`
   - `RAILSAFE_DEMO_ACCOUNTS_ENABLED=false`
   - `RAILSAFE_DDL_AUTO=update`

7. Allow Render's outbound IP address(es) in the Aiven service allowlist if your
   Aiven plan uses IP restrictions. A connection failure at startup is usually this
   step, not a Dockerfile problem.

8. After the first deploy, open `https://<your-service>.onrender.com/api/health`.
   Expected response:

   ```json
   {"status":"UP","service":"railsafe"}
   ```

9. Copy the Render service URL (for example `https://railsafe-backend.onrender.com`)
   for the Vercel `BACKEND_URL` variable.

### Render operational notes

- Keep **one instance**. Sessions, CSRF state, login lockout and SSE subscribers are
  in memory. `numInstances: 1` is set in `render.yaml`.
- The first request after an idle free/starter plan can be slow. Vercel simply waits;
  allow extra time for the first login after inactivity.
- The default Blueprint plan is `0.5c-512mb`. You may change it in the dashboard, but
  keep enough memory for the JVM (`JAVA_TOOL_OPTIONS` already caps heap growth).
- Render's disk is ephemeral. Local JSON cache/quota files under `data/` are
  non-critical for the labelled simulation mode.

---

## 3. Deploy the frontend on Vercel

1. Sign in to [Vercel](https://vercel.com/).
2. Click **Add New → Project** and import `srinadh-pro/Rail-Safe`.
3. **Root Directory:** leave at the repository root (do not select `RailSafe`).
4. Framework preset: **Other** (`vercel.json` sets `"framework": null`). The
   install/build/output settings are already in `vercel.json`:
   - Install: `cd RailSafe/frontend && npm ci`
   - Build: `cd RailSafe/frontend && npm run build`
   - Output: `RailSafe/src/main/resources/static`
5. Add one Project Environment Variable for every environment you deploy:

   | Name | Value |
   |---|---|
   | `BACKEND_URL` | Render service URL, no trailing slash, e.g. `https://railsafe-backend.onrender.com` |

   Add it to **Production** and **Preview**. If it is missing, Vercel leaves the
   literal `${BACKEND_URL}` in the proxy destination and API calls fail.

6. Click **Deploy**. Vercel rebuilds the React bundle into
   `RailSafe/src/main/resources/static/live` and publishes all static pages.

---

## 4. Verify the split deployment

On the Vercel domain, check:

| URL | Expected |
|---|---|
| `/api/health` | `{"status":"UP","service":"railsafe"}` from Render, no URL change |
| `/login.html` | RailSafe login page |
| `/live/` | React dashboard (login redirects to `/login.html?next=live/`) |
| `/dashboard.html`, `/admin.html`, `/staff.html`, `/super-admin.html` | Role-gated pages |
| `/favicon.svg`, `/css/styles.css`, `/js/api.js` | Static assets from Vercel |

Then sign in and confirm:

- CSRF bootstrap writes `XSRF-TOKEN` on the Vercel origin.
- Write actions (report, route save, gate update) succeed with `X-XSRF-TOKEN`.
- SSE stream `/api/live/stream` stays connected.
- Logout returns to `/login.html` and clears the session.
- The simulation UI remains labelled **SIMULATION** / **NOT LIVE**.

Useful local checks before pushing:

```powershell
cd RailSafe
.\mvnw.cmd clean test
.\mvnw.cmd -q -DskipTests package
cd frontend
npm ci
npm run build
```

If Docker is installed:

```powershell
docker build -f Dockerfile -t railsafe-backend .
```

---

## 5. Git workflow

The branch and remote already exist:

```powershell
git -C "C:\Users\lenovo thinkPad\Documents\Default Project" status
git -C "C:\Users\lenovo thinkPad\Documents\Default Project" add -A
git -C "C:\Users\lenovo thinkPad\Documents\Default Project" commit -m "Add Vercel and Render deployment configuration"
git -C "C:\Users\lenovo thinkPad\Documents\Default Project" push origin main
```

Pushing to `main` triggers Vercel and Render (auto-deploy is enabled in both
configurations). Never add `RailSafe/deployment/railsafe.env.local` or any secret
value to Git; it is gitignored.

---

## 6. Why no CORS configuration was added

All browser URLs are root-relative (`/api/...`, `/login.html`, `/live/`). Vercel
forwards `/api/*` to Render under the same visible origin. Cookies therefore remain
same-site and are stored for the Vercel domain, and the existing double-submit CSRF
flow is unchanged. Adding cross-origin CORS, `SameSite=None`, or a frontend API base
URL would be unnecessary and would weaken the current setup.

## 7. Known constraints

- No real railway feed is connected; the default provider is explicitly simulated.
- The public OSRM demo server is used for alternate routes; production should use a
  dedicated routing service.
- Login lockout is in-memory and per instance; keep one backend instance or add an
  edge rate limiter if you scale out.
- `spring.jpa.hibernate.ddl-auto=update` is the convenience default. Switch to
  `validate` after confirming the Aiven schema, and adopt versioned migrations before
  production releases.
