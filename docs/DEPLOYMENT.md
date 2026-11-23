# Deployment

How to run Strata in production on a single VPS with Docker Compose behind Caddy (automatic TLS). Everything here
uses files in `deploy/`. Decisions behind it: D-023 (storage), D-025 (feed cache), D-026 (observability and guard rails).

> **Verification status.** The compose files, Caddyfile, Prometheus and Grafana files were validated as YAML/JSON and
> reviewed, but **not executed**: Docker isn't installed on the development machine. The backend, its tests and the
> frontend build were run. Treat the first deploy as a rehearsal on a staging host.

## 1. What runs

| Service | Image | Purpose | Exposed |
|---|---|---|---|
| `caddy` | `caddy:2.8-alpine` | TLS (Let's Encrypt), HTTP→HTTPS, HTTP/2+3, HSTS, gzip/zstd | **80, 443** (the only published ports) |
| `web` | built from `frontend/` | nginx: SPA + reverse proxy for `/api`, `/ws`, Swagger | internal |
| `backend` | built from `backend/` | Spring Boot API, profile `prod`; 8080 app, 8081 management/metrics | internal |
| `postgres` | `postgres:17-alpine` | primary database (`pgdata` volume) | internal |
| `pgbackup` | `postgres:17-alpine` | nightly `pg_dump` to `./backups` | internal |
| `redis` | `redis:7-alpine` | rate limits, feed cache (no persistence, password) | internal |
| `s3` | `chrislusf/seaweedfs:4.55` | S3-compatible object storage for uploads; one access identity, anonymous access refused; the backend creates the private bucket at start | internal |

> **Why SeaweedFS and not MinIO:** MinIO stopped publishing public images (Docker Hub reports the repository missing and
> quay.io answers 401 to anonymous pulls), so the bundled object store is SeaweedFS. The backend only speaks the S3 API, so
> any S3-compatible service works (see below).
| `prometheus`, `grafana` | optional (`--profile monitoring`) | metrics and dashboard | internal (Grafana via Caddy if you enable it) |

Request path: browser → Caddy (TLS) → nginx (`web`) → backend. nginx keeps Caddy's `X-Forwarded-Proto: https`, and the
backend (`server.forward-headers-strategy: native`) trusts private-network proxies, so it sees HTTPS requests: secure
cookies, HSTS and correct client IPs for rate limits.

## 2. First deploy

1. A VPS with 2 vCPU / 4 GB RAM or more (the JVM takes ~1–1.5 GB), Docker Engine and the Compose plugin, a DNS
   `A`/`AAAA` record for your domain, and ports 80/443 open.
2. Clone the repo, then `cp deploy/.env.example deploy/.env && chmod 600 deploy/.env` and fill in **every** value.
   Generate secrets with `openssl rand -base64 48` (JWT) and `openssl rand -hex 32` (others).
3. `docker compose -f deploy/docker-compose.prod.yml --env-file deploy/.env up -d --build`
4. Watch `docker compose -f deploy/docker-compose.prod.yml logs -f backend`. If a secret is missing or a dev default,
   the backend stops with a list of every problem ("Refusing to start the prod profile…"); fix `deploy/.env` and rerun.
5. Open `https://<your-domain>`, register, then promote yourself to admin once:
   `docker compose -f deploy/docker-compose.prod.yml exec postgres psql -U strata -d strata -c "UPDATE users SET role='ADMIN' WHERE username='you'"`.

Keep `SEED_DEMO_DATA=false` for a real community. If you enable it for a demo instance, the UI shows a "Demo data" notice.

## 3. Environment reference

Required in production (the compose file refuses to start without them, and the backend's `ProductionSafetyCheck`
re-checks the important ones):

| Variable | Meaning |
|---|---|
| `STRATA_DOMAIN`, `ACME_EMAIL` | public host name; email for certificate notices |
| `DATABASE_PASSWORD` (+ `DATABASE_USER`) | PostgreSQL credentials (≥ 12 chars, not a default) |
| `REDIS_PASSWORD` | Redis `requirepass` |
| `JWT_SECRET` | base64 of ≥ 32 random bytes; signs access tokens and download grants. Rotating it signs everyone out |
| `FILE_VISITOR_SALT` | server secret for hashing share-link visitors (no IPs are stored) |
| `FUNDING_DEMO_WEBHOOK_SECRET` | ≥ 16 chars; HMAC for demo funding webhooks (D-022) |
| `S3_ACCESS_KEY`, `S3_SECRET_KEY`, `S3_BUCKET` | object storage credentials (the `s3` service is configured with the same pair) |
| `MAIL_FROM`, `SMTP_HOST`, `SMTP_PORT`, `SMTP_USER`, `SMTP_PASSWORD` | email verification and password reset (`MAIL_MODE=smtp`) |

Optional:

| Variable | Default | Meaning |
|---|---|---|
| `ANTHROPIC_API_KEY`, `AI_MODEL`, `AI_EFFORT` | empty, `claude-opus-5-5`, `medium` | Community AI; without a key, answers are offline cited summaries |
| `FUNDING_ENABLED` | `true` | moderator funding |
| `FUNDING_PROVIDER` | `demo` | `demo` (simulated) or `stripe` (hosted Checkout, D-034); see "Stripe payments" below |
| `STRIPE_SECRET_KEY` | empty | Stripe secret key, server-side only. `sk_test_…` = test mode. A live key refuses to start without the two settings below |
| `STRIPE_WEBHOOK_SECRET` | empty | signing secret of the webhook endpoint (`whsec_…`); required in production with Stripe |
| `FUNDING_LIVE_MODE`, `FUNDING_COMPLIANCE_ACK` | `false`, empty | both needed before a live key is accepted; the acknowledgement records who signed off the compliance review (D-034) |
| `SEED_DEMO_DATA` | `false` | fictional demo content |
| `DATABASE_POOL_SIZE` | 15 | Hikari pool size |
| `FEED_CACHE_ENABLED`, `FEED_CACHE_TTL` | `true`, `PT30S` | first-page feed id cache (D-025) |
| `RATE_LIMIT_ENABLED` | `true` | Redis rate limiter |
| `SPAM_HEURISTICS_ENABLED` | `true` | write-time spam checks (D-021) |
| `MAX_MEDIA_MB`, `MAX_FILE_MB`, `FILE_QUOTA_MB` | 500, 500, 1024 | upload limits (keep below nginx's `client_max_body_size 600m`) |
| `MANAGEMENT_PORT` | 8081 | actuator/metrics port (never publish it) |
| `LOG_FORMAT` | `ecs` | structured log format: `ecs`, `logstash` or `gelf` |
| `JAVA_OPTS` | `-XX:MaxRAMPercentage=70 …` | JVM flags |
| `BACKUP_DIR`, `BACKUP_KEEP_DAYS`, `BACKUP_INTERVAL_SECONDS` | `./backups`, 14, 86400 | database dump schedule |
| `STRATA_BACKEND_IMAGE`, `STRATA_WEB_IMAGE` | local build | use images built by CI instead of building on the server |

### Using AWS S3 (or another S3 service) instead of the bundled SeaweedFS
Set `S3_ENDPOINT` to empty (AWS) or the provider's endpoint, `S3_PATH_STYLE=false` for AWS, `S3_REGION`, and credentials
(`S3_ACCESS_KEY`/`S3_SECRET_KEY`, or leave them blank to use an instance role). Remove the `s3` service and the backend's
`s3` dependency, and set `S3_CREATE_BUCKET=false`. Create the bucket with **Block Public Access** on; the API never needs public
objects (every read is proxied, D-023). Grant the key only `s3:GetObject`, `s3:PutObject`, `s3:DeleteObject`,
`s3:AbortMultipartUpload` on `arn:aws:s3:::<bucket>/*` and `s3:ListBucket` on the bucket (for health checks).

### nginx + certbot instead of Caddy
Drop the `caddy` service, publish `web` on 127.0.0.1:8088, and put a host nginx in front:
```nginx
server {
    listen 443 ssl http2;
    server_name strata.example.com;
    ssl_certificate     /etc/letsencrypt/live/strata.example.com/fullchain.pem;   # certbot --nginx -d strata.example.com
    ssl_certificate_key /etc/letsencrypt/live/strata.example.com/privkey.pem;
    add_header Strict-Transport-Security "max-age=31536000; includeSubDomains" always;
    client_max_body_size 600m;
    location / {
        proxy_pass http://127.0.0.1:8088;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;       # /ws (STOMP)
        proxy_set_header Connection $connection_upgrade;  # needs the usual map $http_upgrade block
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto https;
        proxy_read_timeout 1h;
        proxy_request_buffering off;
    }
}
server { listen 80; server_name strata.example.com; return 301 https://$host$request_uri; }
```
Certbot's systemd timer renews certificates; add `--deploy-hook "systemctl reload nginx"`.

## 4. Volumes and backups

| Volume | Holds | Back up? |
|---|---|---|
| `pgdata` | database | yes, via the `pgbackup` dumps (not by copying the live directory) |
| `s3data` | uploads (images, podcast media, private files, DM images) | yes |
| `caddy_data` | TLS certificates and ACME account | recommended (avoids re-issuing and rate limits) |
| `promdata`, `grafanadata` | metrics, Grafana state | optional |

- **Database.** `pgbackup` writes `strata-<UTC timestamp>.dump` (custom format) every 24 h and deletes dumps older than
  `BACKUP_KEEP_DAYS`. Copy `deploy/backups/` off the host (restic, rclone, borg, or your provider's snapshots) on its own
  schedule; a backup on the same disk isn't a backup. Restore into an empty database:
  `docker compose ... exec -T postgres pg_restore -U strata -d strata --clean --if-exists < strata-….dump`.
  Test a restore at least monthly on a scratch host.
- **Uploads.** Mirror the bucket to another location with any S3 client from a cron job, e.g. the AWS CLI inside the
  compose network: `docker run --rm --network <project>_default -e AWS_ACCESS_KEY_ID=$S3_ACCESS_KEY
  -e AWS_SECRET_ACCESS_KEY=$S3_SECRET_KEY -e AWS_DEFAULT_REGION=us-east-1 -v /backups/uploads:/backup
  amazon/aws-cli:2.42.1 --endpoint-url http://s3:8333 s3 sync s3://strata /backup`. Database and bucket backups should be taken close together: a row without its object
  shows a broken image, an object without its row is just unused space.
- **Secrets.** Keep `deploy/.env` in your password manager. Without `JWT_SECRET` existing sessions end (people sign in
  again); without `FILE_VISITOR_SALT` access-log hashes from before can't be linked to new ones (no other effect).

## 5. Migrations and upgrades

- **Flyway on boot.** The backend applies pending migrations (`backend/src/main/resources/db/migration/V*.sql`) at
  startup, inside a transaction per migration, before it reports ready. Applied migrations are never edited.
- **Backward-compatible (expand/contract).** Each release's migrations must work with the previous release's code, so a
  rollback is just "run the previous image": add nullable columns or columns with defaults, new tables and indexes;
  never rename or drop in the same release that stops using something. Drop columns one release later. Large index
  builds should use `CREATE INDEX CONCURRENTLY` in their own migration, with a `V<n>__name.sql.conf` file next to it
  containing `executeInTransaction=false`.
- **Upgrade steps.** Take a fresh dump (`docker compose ... exec pgbackup sh -c 'pg_dump -Fc -f /backups/pre-upgrade.dump'`),
  then `git pull && docker compose -f deploy/docker-compose.prod.yml --env-file deploy/.env up -d --build backend web`.
- **Rollback.** Redeploy the previous image tag (`STRATA_BACKEND_IMAGE=…:previous`). Because migrations are additive, the
  old code runs against the new schema. Flyway won't downgrade; if a migration itself was wrong, restore the
  pre-upgrade dump (accepting the data written since) or ship a forward fix.

## 6. Zero-downtime notes

With one backend container, `up -d` restarts it and requests fail for the ~20–40 s the JVM needs to boot. To avoid that:
1. Run two backend replicas (`deploy.replicas: 2` or a second service) behind nginx (`upstream` with both), and
   restart them one at a time; `/readyz` turns ready only after migrations and startup, and nginx/Caddy retry on the
   other instance.
2. Before doing so, apply the scaling items below: WebSocket broadcasts and presence are per instance today.
3. Expand/contract migrations (above) make it safe for old and new code to run side by side during the roll.
Graceful shutdown is on by default in Spring Boot 3: in-flight requests finish (up to 30 s) before the JVM exits;
WebSocket clients reconnect with backoff and refetch.

## 7. Scaling beyond one node

- **Real-time (D-017).** Switch `enableSimpleBroker` to `enableStompBrokerRelay` in `WebSocketConfig` and run RabbitMQ
  with the STOMP plugin, so a message published on node A reaches a socket on node B. Sticky sessions aren't required
  for STOMP over plain WebSocket, but they reduce reconnect churn.
- **Presence.** Move `PresenceTracker`'s per-user session counts to Redis (`INCR`/`DECR` with a per-node heartbeat key, so a
  crashed node's sessions expire).
- **Account-status cache** is per node (≤ 30 s delay when suspending, D-021); fine, or replace with Redis pub/sub.
- **Feed cache and rate limits** already live in Redis and work across nodes.
- **Database.** Raise `DATABASE_POOL_SIZE` carefully (total connections across nodes < `max_connections`), add a
  PgBouncer in transaction mode if you run many nodes, and move to a managed PostgreSQL with point-in-time recovery
  when the dump-based backup isn't enough.
- **Storage** is already shared (S3 API: SeaweedFS here, or a managed bucket). For heavy media traffic, presigned GET redirects for podcast audio would cut
  API bandwidth (see D-023); that's a change in `MediaController`, not built.
- **Jobs.** The moderator-recognition recompute takes a PostgreSQL advisory lock and funding cycles lock their rows,
  so they're safe on several nodes. Review the other `@Scheduled` jobs (reputation queue, live counters) before scaling
  out.

## 8. Health checks and monitoring

- **Probes.** `GET /livez` and `GET /readyz` on port 8080 (used by the backend image's `HEALTHCHECK`). nginx answers
  `GET /healthz`. Readiness includes the database and storage (`StorageHealthIndicator`: bucket reachable or
  upload directory writable); Redis is left out on purpose, since rate limits and the feed cache degrade without it.
- **Metrics.** `GET /actuator/prometheus` on the management port 8081 (internal only). Start Prometheus and Grafana with
  `--profile monitoring`; Grafana provisions the Prometheus data source and the **Strata overview** dashboard
  (`deploy/grafana/dashboards/strata-overview.json`). To reach Grafana, uncomment the `grafana.` block in the Caddyfile
  (it has its own login) or use an SSH tunnel (`ssh -L 3000:localhost:3000` after publishing it on 127.0.0.1).
- **Suggested alerts.** `up{job="strata-backend"} == 0` for 2 min; 5xx rate > 2 % for 10 min; HTTP p95 > 1 s for 15 min;
  `hikaricp_connections_pending > 0` for 5 min; a spike in `strata_spam_flags_total`; disk usage of the host > 80 %; the
  newest file in `deploy/backups` older than 26 h.
- **Logs.** The backend logs one JSON object per line (ECS) with `traceId`; every API response carries `X-Trace-Id`, so a
  user's error report can be matched to its log lines. Ship container logs with Docker's `local` driver plus
  Vector/Promtail/Fluent Bit if you want search.

## 9. Security checklist

- Only ports 80/443 open (`ufw allow 80,443/tcp && ufw allow 443/udp` plus SSH). Postgres, Redis, the S3 service and 8081 stay
  internal.
- `deploy/.env` is `chmod 600` and not in git.
- Prod profile: secure cookies, HSTS, no Swagger exposure beyond the read-only docs (remove the Swagger location from
  `frontend/nginx.conf` if you prefer), seed off, prod safety check on.
- The SPA's CSP (in `frontend/nginx.conf`) allows scripts only from the same origin, WebSockets to the same host, and
  inline styles (Recharts/Radix); see D-026.
- Keep images patched: rebuild monthly (`docker compose ... build --pull`).

## 10. Stripe payments for moderator funding (D-034)
Test mode first. Nothing here moves real money until you deliberately switch to a live key and set
`FUNDING_LIVE_MODE=true` and `FUNDING_COMPLIANCE_ACK` after the compliance review in D-022/D-034.

1. **Keys.** In the Stripe dashboard (test mode on), open Developers → API keys and copy the **secret** key (`sk_test_…`).
   No publishable key is needed: Checkout is hosted by Stripe. Put the keys in the git-ignored files, never in the repo:
   - local backend (`./mvnw spring-boot:run`): `backend/.env.local` with `FUNDING_PROVIDER=stripe`, `STRIPE_SECRET_KEY=sk_test_…`
     and optionally `STRIPE_WEBHOOK_SECRET=whsec_…`;
   - Docker: the same variables in `.env` (local stack) or `deploy/.env` (production). Both compose files pass them through.
2. **Webhook endpoint (deployed).** Developers → Webhooks → Add endpoint: `https://<your domain>/api/v1/funding/webhooks/stripe`,
   with the events `checkout.session.completed`, `checkout.session.async_payment_succeeded`, `checkout.session.async_payment_failed`,
   `checkout.session.expired`, `invoice.paid`, `invoice.payment_failed`, `customer.subscription.deleted`, `charge.refunded`.
   Copy its signing secret into `STRIPE_WEBHOOK_SECRET`.
3. **Local webhooks (optional).** Contributions are confirmed without webhooks when you come back from Checkout (the
   server asks Stripe). For renewals, refunds and cancellations made in the dashboard, forward events with the Stripe CLI:
   `stripe listen --forward-to localhost:8080/api/v1/funding/webhooks/stripe` and use the `whsec_…` it prints as
   `STRIPE_WEBHOOK_SECRET`. `stripe trigger checkout.session.completed` sends a sample event; it will be acknowledged
   as "unknown checkout session" because it doesn't belong to a pending contribution.
4. **Try it.** Restart the backend; the funding banner says "TEST MODE: no real money". Create a pool, contribute, pay with
   the test card `4242 4242 4242 4242` (any future date, any CVC), and you land back on the pool page, which confirms the payment.
5. **Owner's live check (optional).** From `backend/`: `./mvnw test -Dtest=StripeLiveCheckTest -Dstripe.live=true
   -Dfunding.excludedGroups=none -Dsurefire.failIfNoSpecifiedTests=false`. It reads `STRIPE_SECRET_KEY` from
   `backend/.env.local` (never printed), refuses anything but `sk_test_`, creates a 5.00 USD Checkout Session and prints only
   the session id prefix and its URL.

Pools remember the provider and mode they were created with: demo pools keep their demo labels and stop taking contributions
after you switch to Stripe; create new pools for Stripe. The demo seed doesn't create a funding pool when Stripe is active.
