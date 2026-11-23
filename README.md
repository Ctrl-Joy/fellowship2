# Howlers

A community discussion platform built to turn conversations into knowledge. Familiar if you've used
threaded-community sites, but with original branding, ranking and product direction.

**Status:** Phases 0–2 complete: auth, profiles, onboarding, communities, posts, voting, nested comments,
feeds, search and notifications. See [docs/IMPLEMENTATION_STATUS.md](docs/IMPLEMENTATION_STATUS.md) and
[docs/ROADMAP.md](docs/ROADMAP.md).

| | |
|---|---|
| Backend | Java 21, Spring Boot 3.5, Spring Security (JWT + rotating refresh cookie), JPA + SQL read models, Flyway, PostgreSQL 17, Redis |
| Frontend | React 18, TypeScript, Vite, Tailwind, TanStack Query, Zustand, React Hook Form + Zod, TipTap, Recharts |
| Ops | Docker Compose, nginx, GitHub Actions |

## Run it

### Option A: Docker
```bash
cp .env.example .env      # set DATABASE_PASSWORD and JWT_SECRET (openssl rand -base64 48)
docker compose up --build
```
Open http://localhost:8088. API docs: http://localhost:8088/swagger-ui.html.

### Option B: Local processes (no Docker)
Prereqs: JDK 21, Node 22+, PostgreSQL 15+ and Redis (optional: rate limiting falls back to memory without it).

```bash
# database (defaults expect port 5433, db/user/password "strata")
createdb -p 5433 -U strata strata

cd backend && ./mvnw spring-boot:run        # http://localhost:8080, runs migrations + demo seed
cd frontend && npm install && npm run dev   # http://localhost:5173 (proxies /api to :8080)
```
Override the DB with `DATABASE_URL`, `DATABASE_USER` and `DATABASE_PASSWORD`. Every setting is listed in
`backend/src/main/resources/application.yml` and `.env.example`.

### Demo accounts
With `SEED_DEMO_DATA=true` (the default outside the `prod` profile), fictional demo users are created, such as
`mira_k`, `nadia_sec` and `first_job_fern`, all with the password **`strata-demo-1`**. The content is
illustrative demo data, not real activity. Disable it in production.

`admin_demo` (same password) is a platform admin: sign in and open **Admin dashboard** from the account menu (`/admin`).
It's created whenever demo seeding is on, including on databases seeded before it existed.

### Community AI
Set `ANTHROPIC_API_KEY` (server-side) and `AI_MODEL` (the model id from your AI provider) to get answers from a hosted AI model (override with
`AI_MODEL` / `AI_EFFORT`). Without it, Community AI still works in offline mode: it returns cited summaries of the
most relevant discussions and says clearly that they aren't generated answers.

Verification and password-reset emails are printed to the backend log (`MAIL-DEV`) unless `MAIL_MODE=brevo` (Brevo's
API) or `MAIL_MODE=smtp` is set; see docs/DEPLOYMENT.md, "Email with Brevo".

## Test
```bash
cd backend && ./mvnw test      # unit + integration tests on embedded PostgreSQL (no Docker needed)
cd frontend && npx vitest run && npm run build
```

## Docs
[Product spec](docs/PRODUCT_SPEC.md) · [Architecture](docs/ARCHITECTURE.md) · [Database](docs/DATABASE.md) ·
[API](docs/API.md) · [Ranking](docs/RANKING.md) · [Design system](docs/DESIGN_SYSTEM.md) ·
[Frontend](docs/FRONTEND.md) · [Decisions](docs/DECISIONS.md) · [Roadmap](docs/ROADMAP.md) · [Deployment](docs/DEPLOYMENT.md)

## Production notes
Use `SPRING_PROFILES_ACTIVE=prod` (secure cookies, seeding off), serve over HTTPS (`COOKIE_SECURE=true`), set a
strong `JWT_SECRET`, and put Postgres backups on the `pgdata` volume. Flyway applies migrations on startup.
To troubleshoot, check `/actuator/health` first. Every error response carries a `traceId` that matches
the backend log line.
