# FlowerConnect

Hyperlocal flower marketplace connecting local artisan florists with customers. Built with Spring Boot 3.2 (Java 17) backend and React 18 / TypeScript / Vite / Tailwind CSS frontend.

> **Note:** This is a provisional quick-start guide for developer rehearsals. The comprehensive production-ready README deliverable will be finalized in Phase 14 Stage 5 per `FlowerConnect_Implementation_Plan_v2.2.md`.

---

## Quick Start (Docker Compose Dev Environment)

### 1. Prerequisites
- Docker & Docker Desktop (with Compose v2+)
- Port availability: `8080` (Backend API), `5173` (Frontend Web), `3306`/`3307` (MySQL), `8025` (MailHog)

### 2. Configuration
Copy the template environment configuration file:

```bash
cp .env.example .env
```

Edit `.env` to supply development credentials and secrets:
- `SPRING_PROFILES_ACTIVE=dev`
- `ADMIN_EMAIL=admin@flowerconnect.test`
- `ADMIN_PASSWORD=<choose-a-dev-admin-password>`
- `DEMO_PASSWORD=<choose-a-dev-demo-password>`
- `JWT_SECRET=<at-least-256-bit-random-secret>`
- `APP_CHECKOUT_QUOTE_SIGNING_SECRET=<at-least-32-char-secret>`

### 3. Launch Application Stack
Build and start the application containers in detached mode:

```bash
docker compose up --build -d
```

Check container health status:

```bash
docker compose ps
```

Wait until `flowerconnect-backend` and `flowerconnect-mysql` report `healthy`.

### 4. Application Access Points
- **Frontend Web Application:** [http://localhost:5173](http://localhost:5173)
- **Backend REST API:** [http://localhost:8080/api/v1](http://localhost:8080/api/v1)
- **Backend Actuator Health:** [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health)
- **Swagger / OpenAPI Documentation:** [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- **MailHog In-App Email Viewer:** [http://localhost:8025](http://localhost:8025)

### 5. Running the Demonstration Walkthrough
Refer to [`docs/demo-script.md`](docs/demo-script.md) for the complete, step-by-step walkthrough covering Customer, Vendor, and Admin user flows with seeded demo data.

### 6. Clean Reset
To reset the development database to a pristine seeded state:

```bash
docker compose down -v
docker compose up -d
```

