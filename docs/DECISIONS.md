# Decision log

**D-001 Modular monolith.** One Spring Boot app with package-level modules. Current scale doesn't justify
network boundaries, distributed transactions or a message broker. Extraction path is documented in ARCHITECTURE.md.

**D-002 Spring Boot 3.5 (not 4.x).** 3.5.16 is mature, and the ecosystem (springdoc, embedded-postgres) is
stable on it. A 4.x migration (Jackson 3, modular starters) is a contained follow-up.

**D-003 JPA for aggregates, SQL for read models and counters.** Feeds, comment trees and votes need keyset
predicates, window functions, `ON CONFLICT` and advisory locks; hand-written SQL is clearer and faster than
fighting the ORM. Flyway owns the schema.

**D-004 Voting concurrency = advisory lock per (target, voter) + relative counter updates.** It serialises one
user's double-clicks without serialising different users. The PK on vote tables prevents duplicates. It's verified
by a 26-thread integration test.

**D-005 Tokens.** Access JWT lives in memory only (XSS can't read a cookie it doesn't have, and the token is
short-lived). The refresh token is an HttpOnly, SameSite=Strict, path-scoped cookie with rotation and reuse
detection. No CSRF token is needed because no state-changing endpoint is authorised by a cookie except
refresh/logout, which also check Origin.

**D-006 Rich text = sanitised HTML for posts, Markdown for comments.** Post bodies come from TipTap and are
sanitised server-side with an allow-list (jsoup). Comments are Markdown rendered client-side without raw HTML.

**D-007 Storage: local filesystem driver first.** `StorageService` abstracts storage; images are validated by
magic bytes and stored under random keys. An S3/MinIO driver is planned for Phase 6/7, when media and
private files arrive. Until then, Docker Compose persists uploads in a volume, and MinIO is deliberately not
shipped as an unused container.

**D-008 Embedded PostgreSQL for tests.** Integration tests run against real PostgreSQL binaries (zonky), with no
Docker required, so CI and contributors without Docker run the same tests.

**D-009 Notifications are pulled in Phase 2.** The unread count is polled every 30 s. WebSocket/STOMP arrives
with messaging (Phase 3+), where push is genuinely needed.

**D-010 Reddit research boundary.** reddit.com was inspected read-only with REA. It served a bot-challenge
page to the automated browser, and that challenge was not bypassed. No Reddit binary exists to analyse with
Ghidra. The design draws only on publicly known concepts (communities, threaded comments, vote ranking).
Brand, visual design, ranking formula and copy are original.

**D-011 Digest and Context Mode run offline first.** The `DigestEngine` interface has one built-in
implementation, `extractive-v2`. It selects and quotes real sentences and never paraphrases, so it can't invent
claims. Stance suggestions come from transparent rules (`TextSignals`), are always flagged as "suggested", and
people can correct them. An LLM-backed engine can plug in behind the same interface (keys stay server-side) when
Community AI lands in Phase 5. The engine id is part of the cache key, so changing engines regenerates digests.

**D-012 Familiar layout, original identity.** The UI follows widely used community-site conventions so
newcomers feel at home: left nav with collapsible sections, card/compact views, pill vote and action buttons,
a sort dropdown, a community banner with an overlapping icon, and avatar thread lines. It does not reuse
Reddit's name, logo, mascot, colours or assets. reddit.com could not be opened for side-by-side study (the built-in
browser blocks it, and automated sessions get a bot challenge, which we do not bypass), so the layout follows these
generic patterns rather than copying any page.

**D-013 Community AI = permission-filtered retrieval + Claude, with an offline fallback.**
- **Retrieval.** PostgreSQL full-text search over posts and comments, using OR-of-keywords tsqueries built only from
  `[a-z0-9]` tokens, so there's no query injection. Results are ranked by text relevance × log(score) with intent boosts, then
  filtered by a relevance floor, a per-thread diversity cap and an 18k-character budget. For the two strongest matching
  threads, their best replies come along even without keyword overlap, because replies rarely repeat the question's terms. Visibility (private communities,
  deleted and removed content) is enforced in the SQL, before ranking, so the model never sees what the reader can't.
  pgvector semantic search was deferred: FTS is index-backed and good enough at current scale, and it plugs in behind
  `Retriever` without API changes.
- **Generation.** `ClaudeProvider` uses the official Anthropic Java SDK (`anthropic-java` 3.12.0) and calls
  `claude-opus-5-5` at effort `medium`. The static system prompt is cached, and sources go in the final user turn. Server-side
  refusal fallbacks are on (`fallbacks: "default"`). The key comes from `ANTHROPIC_API_KEY` on the server only (env, or git-ignored `backend/.env.local` in local development).
- **Offline.** Without a key, `OfflineProvider` returns a cited, stance-grouped summary of the retrieved sentences and
  labels itself as not generated.
- **Trust.** Every answer stores the exact sources shown to the model. Citations that don't map to a source are stripped.
  When an old answer is re-read, sources that were later deleted or made private are redacted. The model call runs outside
  any DB transaction.

**D-014 Moderation is community-scoped and auditable.** Every moderator action is appended to `moderation_actions`
with moderator, target, reason, expiry and a reversal flag. Rows are never deleted: approvals, unbans and accepted
appeals mark the original action as reversed. Bans are enforced in `CommunityAccess`, which every participation path
(post, comment, vote, pulse vote, join) calls, so a ban can't be bypassed through another endpoint. Rank rules: nobody
bans the owner, and moderators are only banned by someone strictly above them. Notifications about removals and bans
carry no moderator identity, because actions are taken on behalf of the team. Moderators see the text of removed
comments so they can review appeals; everyone else sees `[removed]`.

**D-015 Community Pulse has no stakes, and results show only after you vote.** Pulses are created by moderators. Status is
derived on read (closes_at / closed early / resolved), so no scheduler is needed. Tallies stay hidden until you vote or the
pulse closes, so early results don't anchor later voters. Votes reuse the same advisory-lock and relative-counter pattern as
post votes. Predictions record accuracy only, never money or points (per the brief). Live results refetch every 15 s; a
WebSocket push is deferred until messaging adds STOMP.
