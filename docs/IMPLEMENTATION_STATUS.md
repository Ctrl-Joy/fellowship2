# Implementation status

_Last updated: 2026-11-23 (Phases 0–2, layout pass, Phase 3 moderation core, Phases 4–6; Phase 10 e2e suite and accessibility gate)._

## Implemented and verified
| Feature | Backend | Frontend | Verified by |
|---|---|---|---|
| Register / login / logout | ✅ | ✅ | ApiSecurityIT, API smoke run, browser |
| Refresh rotation + reuse detection, Origin check | ✅ | ✅ single-flight refresh | ApiSecurityIT, apiClient tests |
| Email verification / password reset (log-mode mail) | ✅ | ✅ | API smoke run |
| Sessions list / revoke | ✅ | ✅ | — |
| Profile, avatar/banner, settings, privacy, deactivate, delete (anonymise) | ✅ | ✅ | API smoke run |
| Onboarding (topics → communities → people → theme) | ✅ | ✅ | API smoke run |
| Communities: create, visibility, rules, flairs, icon/banner, join/leave, members, roles | ✅ | ✅ | ApiSecurityIT (private isolation, role escalation) |
| Posts: text/link/image/poll, sanitised HTML, tags, flair, edit, delete, duplicate guard | ✅ | ✅ | TextSafetyTest, API smoke run |
| Voting + karma (posts and comments), concurrency-safe | ✅ | ✅ optimistic | VoteConcurrencyIT, ScoreMathTest |
| Nested comments: sort, lazy replies, edit/delete, mentions | ✅ | ✅ | API smoke run, browser |
| Feeds: For You, Following, Latest, Popular, Rising, Top(t); community sorts + pins | ✅ | ✅ infinite scroll | API smoke run, browser |
| Search (posts/communities/users) + suggestions | ✅ | ✅ | API smoke run |
| Saved / hidden posts | ✅ | ✅ | API smoke run |
| Notifications (reply, mention, follower, milestone, role; grouped) | ✅ | ✅ live badge over WebSocket, 60 s poll only while disconnected | API smoke run, RealtimeIT |
| Rate limiting (Redis, in-memory fallback) | ✅ | — | — |
| Health (db, redis, storage), trace ids, OpenAPI | ✅ | — | manual |
| Demo seed (12 users, 10 communities, threads, real vote rows) | ✅ | — | startup |
| Familiar layout: card/compact views, sort menu, pill actions, collapsible nav (Recent/Communities), recent-posts rail, community banner, avatar thread lines, OP badge | — | ✅ | browser (desktop + 375px) |
| Context Mode: stance groups, evidence, open questions, agreement/disagreement, discussion map, reader relabelling | ✅ | ✅ | KnowledgeLayerIT, TextSignalsTest, browser |
| Thread Digest: 3 lengths × 5 focuses, attributed quotes, fact/opinion/anecdote/speculation tags, honest consensus, cache + staleness + refresh | ✅ | ✅ | KnowledgeLayerIT, browser |
| Community AI: chat with history, community/thread/date scope, permission-filtered retrieval, numbered citations validated server-side, source cards, follow-ups, copy, feedback; Claude via official SDK or offline fallback | ✅ | ✅ | CommunityAiIT, citations.test.ts, browser (offline mode and live Claude `claude-opus-5-5`: ~7–9 s per answer, ~0.8–1.5k input / ~0.5k output tokens, all citations valid) |
| Moderation: reports (dedup per reporter), grouped mod queue, remove/approve/lock/pin (≤3 pins), bans with expiry and rank rules, appeals that reverse actions, append-only audit log, mod stats, affected-user page, user blocking | ✅ | ✅ | ModerationAndPulseIT, live API smoke (23 checks), browser |
| Community Pulse: polls, predictions, opinion and debate prompts; results hidden until you vote; change-vote option; anonymous reasoning; close, resolve with accuracy, history; home and community rails | ✅ | ✅ | ModerationAndPulseIT, live API smoke, browser |
| Podcasts: discovery (featured, continue listening, recent, saved, categories, history), episode page with chapters, searchable synced transcript and sources, global mini player that keeps playing across pages, resume, speed, queue, Media Session; studio with upload (magic-byte checked, range-served), VTT/SRT/timestamped transcripts, chapters, sources, review → publish workflow; Claude script drafts with validated citations | ✅ | ✅ | PodcastIT, TranscriptParserTest, live run with a real 43 s two-voice MP3 and a real Claude draft, browser |
| Direct messages: get-or-create 1:1 conversations, text + up to 4 own images, history paging, unread counts, read receipts ("Seen"), typing, presence, soft delete, search, `dmPolicy` (everyone / people I follow / nobody, reply always allowed), blocks in both directions | ✅ | ✅ | DirectMessagesIT (4), RealtimeIT, browser: two users chatting live (message, unread badge, reply, "Seen" and incoming reply without reload) |
| Real-time channel: STOMP over WebSocket at `/ws`, token checked on CONNECT, permission-checked SUBSCRIBE, events sent after commit; live Pulse and mod-queue refresh | ✅ | ✅ auto-reconnect with backoff | RealtimeIT (live delivery, typing, bad token rejected, foreign mod queue refused) |
| Live feeds and threads: "N new posts" pill on home and community feeds (never auto-inserts), new comments inserted live on post pages, post and comment scores batched every ~2 s; private communities only on members' topics | ✅ | ✅ | RealtimeIT (new post/comment/score delivery, private isolation), live/logic.test.ts, tree.test.ts, browser: pill appeared and loaded a post made by another user; open post page showed a new comment (highlighted) and score 0→2 without reload |
| Moderator funding (**demo mode, no real money**): pools with purpose/goal/period, one-time and monthly contributions (recurring job, cancel), owner-set allocation policy (equal among active mods, activity-weighted from resolved reports and appeals with caps, manual with a required note), period close job, append-only double-entry ledger, public transparency report that reconciles, anonymous-by-default contributors, `PaymentProvider` with HMAC-signed, replay-protected demo webhooks; a non-demo provider refuses to start | ✅ | ✅ | FundingIT (6), FundingUnitTest (7), money.test.ts; not verified in a browser |
| Private files: upload (streamed, magic-byte typed, executables/scripts/macro formats blocked, zip bomb and embedded-executable heuristics), per-user quota with usage meter, name search, rename, soft delete with storage cleanup, owner downloads with Range, attachment-only serving with nosniff and sandbox CSP | ✅ | ✅ | FilesIT (4), DefaultFileScannerTest (4) |
| Share links: expiry, bcrypt password with per-visitor lockout, download limit (atomic), revocation, signed 5-minute download grants, access log with signed-in viewer or daily salted visitor hash (no IPs), public `/s/:token` page working signed out | ✅ | ✅ | FilesIT |
| End-to-end encrypted files: in-browser AES-256-GCM chunked container with encrypted name/type, key in URL fragment or PBKDF2-SHA256 (600k) wrapped passphrase, server stores ciphertext only (UNSCANNED), recovery key, optional per-device key memory, threat model page | ✅ | ✅ | crypto.test.ts (11), FilesIT |
| Topic reputation v1: per-topic points from votes (community topic + topic tags), quality-weighted by voter expertise, self-votes ignored, young/low-karma voters discounted, per-pair and per-topic daily caps, removal reversal; queued on vote, recomputed by a job, deterministic nightly/startup rebuild; profile badges, `/topics/:slug/leaders`, thread expertise chips | ✅ | ✅ | ReputationAndRecognitionIT, ReputationMathTest, reputation/logic.test.ts |
| Moderator recognition: monthly quality-weighted score per community (consistency, response time, appeal outcomes, community health, peer endorsements; never removal counts), transparent formula and per-row breakdown, badges, global and per-community leaderboard, profile section, peer "+1 helpful", ranking opt-out | ✅ | ✅ | ReputationAndRecognitionIT, ModeratorScoreMathTest, reputation/logic.test.ts |
| Admin dashboard: overview with real counts and a 14-day sign-up chart, user search/detail, suspend/unsuspend with reason (sessions revoked, live tokens cut on the next request), promote/demote (never yourself), community quarantine/ban/restore with banner and moderator notification, site-wide spam flags and reports, append-only audit log; demo admin `admin_demo` | ✅ | ✅ | AdminIT (3), helpers.test.ts |
| Mute (private): users (feeds, collapsed comments, no notifications) and communities (home feeds); Settings → Muted | ✅ | ✅ | TrustAndSafetyIT, mutes.test.ts |
| Spam heuristics at write time (link velocity, duplicates across communities, new-account bursts, excessive mentions): hold for review or reject, flags in the mod queue ("Held" tab) and admin Flags; approve releases with deferred notifications; thresholds in `strata.spam.*` | ✅ | ✅ | TrustAndSafetyIT (each heuristic, hold/approve/remove), SpamSignalsTest |
| Feed rows carry `viewer.canModerate` (one batched query per page); mod actions in feed card menus | ✅ | ✅ | TrustAndSafetyIT |
| Onboarding skip persists | ✅ | ✅ | TrustAndSafetyIT |
| Smart Collections: private by default, notes, tags, search, type filter, move, reorder (drag + arrows), share link / revoke, shared view | ✅ | ✅ | KnowledgeLayerIT, tags.test.ts, browser |
| S3/MinIO storage driver (`STORAGE_DRIVER=s3`): private bucket (no ACLs, reads proxied), streamed uploads with 8 MB multipart parts and abort on failure, ranged reads turned into S3 Range GETs, zip scan staging, MinIO + bucket init in docker-compose (D-023) | ✅ | — | S3StorageServiceTest (6, in-memory S3Client + Spring's Range converter); **not run against a real MinIO** |
| Image variants: 320/960 px PNG/JPEG copies without metadata, EXIF orientation applied, GIF/WebP original only; `thumbnails` in media DTOs, `?w=` on `/files/**`; `srcset`/`sizes`, lazy loading and async decoding in post cards, rows, post page, avatars/icons, rail and collection thumbnails (D-024) | ✅ | ✅ | ImageVariantsTest (3), ImageVariantsIT, images.test.ts |
| Private DM images: `/me/dm-media` uploads under `private/`, per-reader signed URLs re-checked on every request (participant, message not deleted), per-recipient realtime payloads, refetch on expiry; DM images can't go into posts (D-024) | ✅ | ✅ | DmMediaIT |
| Redis first-page feed id cache (Popular/Latest/Rising/Top, public community feeds), per-viewer filter + hydration, 30 s TTL, step-aside when Redis is down (D-025) | ✅ | — | FeedCacheIT (3) |
| Observability: Prometheus registry, custom metrics (votes, posts, comments, DMs, WebSocket sessions, AI latency/tokens, spam flags, feed cache), management port 8081 in prod, `/livez` `/readyz`, ECS JSON logs with traceId, Grafana dashboard (D-026) | ✅ | — | DemoSeedIT (scrapes `/actuator/prometheus`); dashboard JSON validated, not loaded into Grafana |
| Production guard rails: prod refuses dev JWT/webhook secrets, weak DB password, missing visitor salt, insecure cookies, default S3 secret, public metrics; HSTS; CSP checked (`ws(s)://$host`, inline styles for Recharts); `docs/DEPLOYMENT.md`, `deploy/docker-compose.prod.yml`, `deploy/Caddyfile`, Prometheus + Grafana provisioning | ✅ | — | ProductionSafetyCheckTest (3); compose/Caddy/Grafana files YAML/JSON-validated only (**not executed**) |
| Richer demo seed: moderator teams in 3 communities with reports, removals, approvals and two appeals (one overturned) written through the moderation service, recomputed leaderboard, two shared collections, a demo funding pool with 5 demo contributions; idempotent, `strata.seed.activity`; dismissible "Demo data" notice from `GET /demo/status` | ✅ | ✅ | DemoSeedIT (own embedded PostgreSQL), demo api.test.ts |

## Tests (latest run)
- Backend `./mvnw test`: **120 passed, 0 failed** (Phase 10 added ProductionSafetyCheckTest 3, FeedCacheIT 3, DmMediaIT 1,
  DemoSeedIT 1, ImageVariantsIT 1, ImageVariantsTest 3, S3StorageServiceTest 6; previously: AdminIT 3, CommunityAiIT 4, ApiSecurityIT 6, TextSafetyTest 3, DefaultFileScannerTest 4, FilesIT 4, FundingIT 6, FundingUnitTest 7, KnowledgeLayerIT 4, TextSignalsTest 3, DirectMessagesIT 4, RealtimeIT 5, ModerationAndPulseIT 4, PodcastIT 3, TranscriptParserTest 5, ModeratorScoreMathTest 5, ReputationAndRecognitionIT 4, ReputationMathTest 8, SpamSignalsTest 3, TrustAndSafetyIT 10, ScoreMathTest 6, VoteConcurrencyIT 1).
- Frontend `npx vitest run`: **132 passed** across 20 files (Phase 10 added images.test.ts and demo/api.test.ts); `npm run build` passes with zero type errors.
| Playwright end-to-end suite (D-027): the 23 owner flows against the real stack, Chromium, repeatable on one database; `npm run e2e`; CI job with PostgreSQL/Redis services, seeded backend jar, offline AI, `vite preview` | — | ✅ | 37 tests passing against the local dev stack (5174 → 8080) |
| Accessibility gate (D-027): axe-core on 10 pages × 2 themes × desktop/375 px, keyboard checks (skip link, focus ring, dialog trap and focus return); contrast tokens, labels, roles, target sizes fixed | — | ✅ | 08-accessibility (0 serious/critical), 09-keyboard; docs/ACCESSIBILITY.md |
| Affected user's moderation page never names the individual moderator (D-014/D-027) | ✅ | ✅ | ModerationAndPulseIT, e2e flow 22 (frontend side; the backend change isn't deployed on the shared dev server) |

## Tests (latest run)
- Backend `./mvnw test`: **102 passed, 0 failed** (AdminIT 3, CommunityAiIT 4, ApiSecurityIT 6, TextSafetyTest 3, DefaultFileScannerTest 4, FilesIT 4, FundingIT 6, FundingUnitTest 7, KnowledgeLayerIT 4, TextSignalsTest 3, DirectMessagesIT 4, RealtimeIT 5, ModerationAndPulseIT 4, PodcastIT 3, TranscriptParserTest 5, ModeratorScoreMathTest 5, ReputationAndRecognitionIT 4, ReputationMathTest 8, SpamSignalsTest 3, TrustAndSafetyIT 10, ScoreMathTest 6, VoteConcurrencyIT 1).
- Frontend `npx vitest run`: **126 passed** across 18 files; `npm run build` passes with zero type errors.
- Playwright `npm run e2e` (D-027): **37 passed** (23 flows + the E2EE round trip, 10 axe page audits × 4 theme/viewport
  combinations, 3 keyboard checks) against the local dev stack, 2026-11-23. The CI `e2e` job hasn't run yet.
- API smoke script (47 checks across auth, communities, posts, polls, votes, comments, feeds, search,
  notifications, permissions): **47/47 after fixing the Rising-feed SQL bug it found**.

## Known gaps / not yet verified
- `deploy/docker-compose.prod.yml`, the Caddyfile, Prometheus and Grafana were validated and reviewed but not run (no public domain/TLS on the dev machine).
- Pinning and locking posts exist in the schema, but they have no endpoints yet (Phase 3 moderation).
- No ESLint (the frontend `lint` script is a type-check only). The Playwright suite exists (D-027); its CI job was
  written but not executed here (no GitHub runner from this machine).
- Email delivery is log-mode unless SMTP is configured.
- Digest/Context Mode use the offline extractive engine and rule-based stance suggestions; no LLM provider is wired yet (Phase 5).
  On the seeded threads most comments are "neutral" until readers label them, which is expected and honest.
- reddit.com could not be opened for side-by-side comparison (built-in browser blocks it; Claude in Chrome was not connected).
- Not built yet: real payment provider for moderator funding (demo-only by design, D-022) and podcast video generation.
- Podcast audio generation (ElevenLabs TTS, D-036/D-037) is built and runs only when `ELEVENLABS_API_KEY` is set; the suite uses a
  fake provider and never calls ElevenLabs. No RSS feed exists yet to carry the AI-voice label.
- Docker: `docker compose up --build` runs locally (Docker Desktop 29.9). Verified: all five services healthy (PostgreSQL, Redis, SeaweedFS S3, backend, web), demo login, feeds, Claude AI and the WebSocket through nginx on http://localhost:8088; uploads go to the private bucket (image with 320/960 variants, private file with a ranged 206 read, owner-only access, delete removes the object, anonymous bucket access 403). MinIO no longer publishes public images, so the bundled S3 service is SeaweedFS.
- Accessibility: no manual screen-reader pass yet; moderate/minor axe findings not triaged; pages outside the audited ten
  not scanned (see docs/ACCESSIBILITY.md).
- Real-time broker and presence are in-memory, per instance (D-017): fine for one backend node, not for several.
- DM images sent since Phase 10 are private (participant-signed URLs, D-024). Images sent earlier keep their
  unguessable-but-public `/files/media/...` URLs (no data migration). Messages are not end-to-end encrypted.
- Image originals keep their EXIF metadata (only the 320/960 variants are stripped); WebP and GIF get no variants.
- Feed first pages can be up to 30 s old in order (new posts); counts, votes and removals are always fresh (D-025).
- Files: scanning is structural (type, denylist, archive heuristics), not antivirus; legacy OLE Office files may carry macros. E2EE decryption holds the whole file in memory. Private-file pages were not exercised in a browser in this phase (tests and build only).
- Mod tools on individual posts and comments appear on the post page only. Feed rows don't carry moderator rights, so feeds don't show them; the mod queue covers them. The UI lists them under "Coming next" and routes them to one
  informational page, with no non-functional controls.
- Spam heuristics are rate and repetition rules only (no content judgement): see D-021 for what they don't catch. A
  suspended user's open WebSocket isn't closed until it reconnects (REST calls fail at once). The account-status cache is
  per node (up to 30 s delay across several nodes).
- Admin dashboard, mutes and holds were verified by integration tests and builds only; not exercised in a browser.
