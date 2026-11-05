# FlowerConnect Deployment & Production Readiness Guide

This document defines the production architecture, profile matrices, operational configurations, local verification procedures, and backup/restore runbooks for FlowerConnect.

---

## 1. Profile Architecture & Environment Matrix

FlowerConnect supports three Spring Boot runtime profiles: `dev`, `staging`, and `prod`.

| Feature / Setting | `dev` | `staging` | `prod` |
|---|---|---|---|
| **Primary Purpose** | Local development & visual verification | Pre-release integration & staging verification | Production deployment |
| **Configuration File** | `application-dev.yml` | `application-staging.yml` | `application-prod.yml` |
| **Database DDL** | `ddl-auto: validate`, Flyway baseline V1 | `ddl-auto: validate`, Flyway baseline V1 | `ddl-auto: validate`, Flyway baseline V1 |
| **Demo Seed Runner** | Enabled (`@Profile("dev")`) behind `app.seed.enabled` | Excluded | Excluded |
| **Payment Simulator** | Enabled (`@Profile("!prod")`) | Excluded / Disabled in prod | Excluded (`@Profile("!prod")` returns 404) |
| **OpenAPI / Swagger UI** | Enabled (`@Profile("dev")`) at `/swagger-ui.html` | Disabled (`springdoc.swagger-ui.enabled: false`) | Disabled (`springdoc.swagger-ui.enabled: false`) |
| **Actuator Exposure** | `health`, `info`, `metrics` (`show-details: always`) | `health`, `info` (`show-details: never`) | `health`, `info` (`show-details: never`) |
| **Health Probes** | `liveness`, `readiness` enabled | `liveness`, `readiness` enabled | `liveness`, `readiness` enabled |
| **Logging Level** | `DEBUG` (`com.flowerconnect`, security) | `INFO` (`com.flowerconnect`, security `WARN`) | `INFO` (`com.flowerconnect`, security `WARN`) |
| **Log Scrubbing** | Sanitized | Sanitized (no secrets, tokens, PII, stack traces) | Sanitized (no secrets, tokens, PII, stack traces) |
| **Refresh Cookie** | `HttpOnly`, `SameSite=Strict`, `Secure=false` | `HttpOnly`, `SameSite=Strict`, `Secure=true` | `HttpOnly`, `SameSite=Strict`, `Secure=true` |
| **Mail Delivery** | MailHog (port 1025, unauthenticated) | Authenticated SMTP / Relay (port 587, TLS) | Authenticated SMTP / Relay (port 587, TLS) |

---

## 2. Externalized Configuration & Secret Validation

All secrets and infrastructure parameters must be externalized via environment variables.

### Required Secrets & Fail-Fast Guarantees

In both `prod` and `staging` profiles, FlowerConnect validates secrets on startup and **fails fast** before accepting any network traffic if any secret is missing, too short, or set to an insecure default:

1. **`JWT_SECRET` (`jwt.secret`)**:
   - Minimum length: 32 characters (256-bit).
   - Enforced by: `JwtService.init()`.
   - Failure behavior: Throws `IllegalStateException("JWT_SECRET must be at least 32 characters")`. The exception message never prints or leaks the rejected secret value.
2. **`APP_WEBHOOK_SECRET` (`app.webhook-secret`)**:
   - Minimum length: 32 bytes (UTF-8).
   - Enforced by: `WebhookSecretValidator.validate()` (`@Profile({"prod", "staging"})`).
   - Failure behavior: Throws `IllegalStateException("app.webhook-secret must be set and at least 32 bytes in prod and staging profiles")`. The exception message never leaks the rejected secret value.
3. **`APP_CHECKOUT_QUOTE_SIGNING_SECRET` (`app.checkout.quote-signing-secret`)**:
   - Minimum length: 32 characters.
   - Enforced by: `CheckoutProperties.validate()`.
   - Failure behavior: Rejects `null`, values `< 32` characters, and rejects the default string `dev-only-insecure-default-change-in-production-min-32-chars` under `prod` and `staging` profiles (`IllegalStateException`). The exception message never leaks the rejected secret value.
4. **Database Credentials (`DB_URL`, `DB_USER`, `DB_PASS`)**:
   - In `prod` and `staging`, no fallback placeholders are defined in `application-*.yml`. Spring Boot fails immediately on startup if placeholders are unassigned.

---

## 3. Production Single-Origin & Local HTTPS Architecture

In standard production, public clients interact with FlowerConnect over a single origin.

```
Client Browser (https://localhost:8443 or https://flowerconnect.example.com)
       │
       ▼
[ Reverse Proxy / Frontend Container :8443 or :5173 ]
       │
       ├─── /api/v1/* ───► Reverse Proxies to backend:8080 (internal network)
       └─── /* (static) ─► Serves React SPA (dist/) + index.html fallback
```

### Benefits
- **Zero Cross-Origin Requests**: The client never communicates directly with backend port 8080.
- **Strict Cookie Security**: The `refresh_token` cookie is marked `HttpOnly`, `SameSite=Strict`, and `Secure`. Because both the HTML page and the `/api/v1/auth/refresh` endpoints share the identical origin, `SameSite=Strict` cookies are accepted seamlessly on every refresh.
- **Internal Database**: MySQL is not published to the host; only the backend container connects over the Docker internal bridge network.

### Cookie Security & Local Access Options

1. **Local HTTPS with Dev Certificates (Default in `docker-compose.prod.yml`):**
   - Development-only self-signed certificates are stored in `certs/` (gitignored, excluded from version control).
   - Generated via `scripts/generate-dev-certs.ps1` or OpenSSL.
   - Mounted into the frontend container at `/etc/flowerconnect/certs/`.
   - `server.mjs` detects `TLS_CERT` and `TLS_KEY` and serves native HTTPS on port `8443`.
   - Verified via Playwright end-to-end browser test (`frontend/tests/e2e/prod-https-session.test.ts`): Chromium accepts and stores the `Secure` `refresh_token` cookie, automatically transmits it on `/api/v1/auth/refresh`, and successfully restores the session.

2. **Local HTTP Override (RFC 6265bis Secure Context):**
   - If TLS certificates are omitted, `server.mjs` falls back to serving HTTP on port `8088`.
   - Modern Chromium/Firefox browsers treat `http://localhost` and `http://127.0.0.1` as Potentially Trustworthy Origins (RFC 6265bis §3.1 / W3C Secure Contexts). Browsers accept and transmit `Secure` cookies over `http://localhost:<port>` without HTTPS.
   - Any non-localhost domain (such as staging or production) strictly requires real HTTPS for `Secure` cookies to be accepted by client browsers.

---

## 4. Production-Like Compose Runbook

To run the production-like stack locally over HTTPS:

### Step 1: Generate Dev Certificates (outside version control)
In PowerShell:
```powershell
powershell -ExecutionPolicy Bypass -File scripts/generate-dev-certs.ps1
```
Or with OpenSSL:
```bash
mkdir -p certs
openssl req -x509 -newkey rsa:2048 -nodes -keyout certs/localhost-key.pem -out certs/localhost.pem -days 365 -subj "/CN=localhost" -addext "subjectAltName=DNS:localhost,IP:127.0.0.1"
```

### Step 2: Prepare Environment Configuration
Set the required environment variables in your shell or `.env`:

```powershell
$env:COMPOSE_PROJECT_NAME="fc-prod"
$env:PROD_FRONTEND_PORT="8443"
$env:JWT_SECRET="<MIN_32_CHARACTERS_CRYPTOGRAPHIC_SECRET_KEY>"
$env:APP_WEBHOOK_SECRET="<MIN_32_CHARACTERS_CRYPTOGRAPHIC_SECRET_KEY>"
$env:APP_CHECKOUT_QUOTE_SIGNING_SECRET="<MIN_32_CHARACTERS_CRYPTOGRAPHIC_SECRET_KEY>"
$env:ADMIN_EMAIL="admin@flowerconnect.test"
$env:ADMIN_PASSWORD="<STRONG_ADMIN_PASSWORD>"
$env:DB_USER="flowerconnect"
$env:DB_PASS="<STRONG_DB_PASSWORD>"
$env:DB_NAME="flowerconnect"
$env:APP_CORS_ORIGINS="https://localhost:8443,http://localhost:8088"
$env:APP_BASE_URL="https://localhost:8443"
```

### Step 3: Start the Prod Stack
```powershell
docker compose -p fc-prod -f docker-compose.prod.yml up --build -d
```

### Step 4: Verify Service Health
```powershell
docker compose -p fc-prod -f docker-compose.prod.yml ps
```
Wait until `mysql`, `backend`, and `frontend` report `(healthy)`.

Inspect backend actuator health over HTTPS:
```powershell
curl.exe -k -s https://localhost:8443/actuator/health
# Expected output: {"status":"UP","groups":["liveness","readiness"]} (no sensitive details exposed)

curl.exe -k -s https://localhost:8443/actuator/health/liveness
# Expected output: {"status":"UP"}

curl.exe -k -s https://localhost:8443/actuator/health/readiness
# Expected output: {"status":"UP"}
```

### Step 5: Login & Session Smoke Test (Real Browser via Playwright)
Run the automated HTTPS session test to verify the browser stores the `Secure` cookie and refreshes the session:
```powershell
cd frontend
$env:E2E_BASE_URL="https://localhost:8443"
npx playwright test tests/e2e/prod-https-session.test.ts
```
Expected output:
```
Running 1 test using 1 worker
[1/1] [chromium] › tests/e2e/prod-https-session.test.ts:5:1 › Prod-like HTTPS session flow verifies Secure cookie storage and refresh
1 passed
```

### Step 6: Verify Exclusions under `prod`
1. **Swagger / OpenAPI**:
   ```powershell
   curl.exe -k -i https://localhost:8443/swagger-ui.html
   # Expected: HTTP 404 Not Found
   ```
2. **Payment Simulator**:
   ```powershell
   curl.exe -k -i -X POST https://localhost:8443/api/v1/payments/simulator/1/pay
   # Expected: HTTP 404 Not Found
   ```
3. **Dev Demo Seed Runner**:
   In backend container logs, verify:
   ```powershell
   docker logs fc-prod-backend | Select-String "DemoSeedRunner"
   # Expected: No occurrences; DemoSeedRunner is not registered in the ApplicationContext.
   ```

### Step 7: Teardown
```powershell
docker compose -p fc-prod -f docker-compose.prod.yml down -v
```

---

## 5. Backup & Recovery Runbooks

Both PowerShell (Windows) and Bash (Linux/macOS) commands have been exercised and verified against the production-like stack.

### MySQL Database Backup

#### PowerShell (Windows)
```powershell
$env:DB_USER = "flowerconnect"
$env:DB_PASS = "<DB_PASSWORD>"  # Do not echo or hardcode in committed scripts
$container = "fc-prod-mysql"
$database = "flowerconnect"
$backupFile = "backup_flowerconnect_$(Get-Date -Format 'yyyyMMdd_HHmmss').sql"

& docker exec -e "MYSQL_PWD=$env:DB_PASS" $container mysqldump -u $env:DB_USER --no-tablespaces --single-transaction --quick $database > $backupFile
```

#### Bash (Linux / macOS)
```bash
CONTAINER_NAME="fc-prod-mysql"
DB_NAME="flowerconnect"
DB_USER="flowerconnect"
BACKUP_FILE="backup_flowerconnect_$(date +%Y%m%d_%H%M%S).sql"

docker exec -e MYSQL_PWD="${DB_PASS}" "${CONTAINER_NAME}" mysqldump \
  -u "${DB_USER}" \
  --no-tablespaces \
  --single-transaction \
  --quick \
  "${DB_NAME}" > "${BACKUP_FILE}"
```

> **Security Note:** Supply `${DB_PASS}` via shell environment variable; never hardcode passwords in scripts or command lines. `--no-tablespaces` is required for standard application users without global `PROCESS` privilege.

### MySQL Database Restore

#### PowerShell (Windows)
```powershell
$env:DB_USER = "flowerconnect"
$env:DB_PASS = "<DB_PASSWORD>"
$container = "fc-prod-mysql"
$database = "flowerconnect"
$backupFile = "backup_flowerconnect_20261105_110000.sql"

Get-Content $backupFile -Raw | docker exec -i -e "MYSQL_PWD=$env:DB_PASS" $container mysql -u $env:DB_USER $database
```

#### Bash (Linux / macOS)
```bash
CONTAINER_NAME="fc-prod-mysql"
DB_NAME="flowerconnect"
DB_USER="flowerconnect"
BACKUP_FILE="backup_flowerconnect_20261105_110000.sql"

docker exec -i -e MYSQL_PWD="${DB_PASS}" "${CONTAINER_NAME}" mysql \
  -u "${DB_USER}" \
  "${DB_NAME}" < "${BACKUP_FILE}"
```

### Uploads Storage Volume Backup
Product images and uploaded media reside in the named Docker volume `${COMPOSE_PROJECT_NAME}_prod-uploads` (mounted at `/var/lib/flowerconnect/uploads`).

#### PowerShell (Windows)
```powershell
$volumeName = "fc-prod_prod-uploads"
$backupTar = "backup_uploads_$(Get-Date -Format 'yyyyMMdd_HHmmss').tar.gz"

docker run --rm -v "${volumeName}:/source:ro" -v "$((Get-Location).Path):/backup" alpine tar -czf "/backup/$backupTar" -C /source .
```

#### Bash (Linux / macOS)
```bash
VOLUME_NAME="fc-prod_prod-uploads"
BACKUP_TAR="backup_uploads_$(date +%Y%m%d_%H%M%S).tar.gz"

docker run --rm \
  -v "${VOLUME_NAME}":/source:ro \
  -v "$(pwd)":/backup \
  alpine:latest \
  tar -czf "/backup/${BACKUP_TAR}" -C /source .
```

### Uploads Storage Volume Restore

#### PowerShell (Windows)
```powershell
$volumeName = "fc-prod_prod-uploads"
$backupTar = "backup_uploads_20261105_110000.tar.gz"

docker run --rm -v "${volumeName}:/target" -v "$((Get-Location).Path):/backup" alpine sh -c "tar -xzf /backup/$backupTar -C /target"
```

#### Bash (Linux / macOS)
```bash
VOLUME_NAME="fc-prod_prod-uploads"
BACKUP_TAR="backup_uploads_20261105_110000.tar.gz"

docker run --rm \
  -v "${VOLUME_NAME}":/target \
  -v "$(pwd)":/backup \
  alpine:latest \
  sh -c "tar -xzf /backup/${BACKUP_TAR} -C /target"
```

---

## 6. Known Limitations in Production

1. **Known Issue #020 (Image serving route):**
   Product images uploaded through `ProductImageService` persist in `/var/lib/flowerconnect/uploads`, but dedicated authenticated HTTP image-serving route is not yet introduced in the product roadmap. The UI renders fallback product cards per D-31.
2. **Mail Relay Requirement:**
   `application-prod.yml` and `application-staging.yml` expect an authenticated SMTP relay (`MAIL_HOST`, `MAIL_USER`, `MAIL_PASSWORD`). To prevent container health check failures when using an internal placeholder host (`smtp-relay.internal`), `management.health.mail.enabled: false` is configured so container health reflects app and DB availability.
