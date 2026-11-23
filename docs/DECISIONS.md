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
post votes. Predictions record accuracy only, never money or points (per the brief). Live results arrive as a WebSocket
nudge (D-017), with a 15 s poll only while the socket is down.

**D-016 Podcasts are upload-first, with AI writing scripts and never audio.** Following the brief's "do not fake generated media":
- **Media.** An episode needs real uploaded audio or video. Uploads are validated by magic bytes and streamed to storage
  without being buffered in memory, and the client reports the measured duration. Files are served as a seekable
  `Resource`, so Spring answers HTTP Range requests (206) for scrubbing.
- **Transcripts.** These are imported from WebVTT, SRT or timestamped text, and the timing always comes from the input. Search runs
  in SQL, and `/transcript.vtt` feeds the video subtitle track.
- **Publishing workflow.** DRAFT → IN_REVIEW → PUBLISHED / ARCHIVED, by community moderators only. Publishing requires media and a
  transcript, and records who published. Media and transcript can't change while published.
- **AI scripts.** `ScriptDrafter` retrieves permission-filtered sources for a topic and streams a two-host script from Claude
  (`createStreaming` + `BetaMessageAccumulator`, since long outputs need streaming). It strips citations that don't map to a source,
  attaches the cited threads as episode sources and stores the result as an *AI draft*. It runs on a small background executor, and
  restarts mark in-flight drafts as failed. The prompt tells the model to write a shorter episode rather than pad to the target length,
  and in testing it did exactly that when the sources were thin.
- **Listening state.** One row per user and episode holds resume position, completion and history. Play count increments on a
  user's first play only. The player is a single app-wide `<audio>` element, so listening continues across navigation.
- **Known limit.** Media URLs are unguessable but not access-controlled, the same as images. Private file access arrives with Phase 7.

**D-017 Real-time is a thin STOMP layer over REST, on a single node for now.** Direct messages and live updates use STOMP over
a plain WebSocket at `/ws` with Spring's in-memory simple broker.
- **REST stays the source of truth.** Events are small nudges (`{type, payload}`) that patch or invalidate client caches.
  A client that misses events while reconnecting simply refetches, so there is no replay log to keep consistent.
- **Auth at the STOMP layer.** Browsers can't set headers on the WebSocket handshake, so the access token travels in the
  CONNECT frame and is verified there. The handshake itself is open but origin-checked against the CORS allow-list. Every
  SUBSCRIBE is checked: your own user queue, pulses you can view, mod queues you moderate. Clients may only SEND to `/app/**`.
- **After commit.** `RealtimePublisher` defers sends until the transaction commits, so no one is told about data they
  can't read yet, and rolled-back changes are never announced.
- **Privacy.** Pulse events carry only the voter count (tallies stay hidden until you vote). Presence is pushed only to people
  you share a conversation with. Typing is forwarded only between participants who may still message each other.
- **DM rules.** Conversations are keyed by the sorted user-id pair, so get-or-create is idempotent and race-free. Blocks
  (either direction) always win. `dmPolicy` (EVERYONE / FOLLOWING / NOBODY) governs who may write first, and you can always
  reply to someone who wrote to you. Messages are plain text, never HTML. They are not end-to-end encrypted and the UI doesn't claim so.
- **Scaling path.** The broker and presence counts are per instance. Running several backend nodes means switching
  `enableSimpleBroker` to `enableStompBrokerRelay` (RabbitMQ STOMP plugin) and moving presence counts to Redis. Both are
  local changes in `WebSocketConfig` and `PresenceTracker`.

**D-018 Live feeds announce, they don't insert; live counts are batched.**
- **Feeds.** A new post is announced on `/topic/community.{id}`, and also on `/topic/posts` unless the community is private.
  The client shows an "N new posts" pill and loads them only when clicked, so the list never jumps under the reader. Blocked
  authors and hidden posts are filtered when the feed is refetched, so the pill's count is an upper bound.
- **Threads.** `/topic/post.{id}` announces new comments by id. The client fetches each with `GET /comments/{id}` under
  the normal visibility and block rules, so events never carry content.
- **Scores.** Votes mark a post or comment as dirty. `LiveCounters` sends committed totals at most every ~2 s per post. A hot
  thread costs one small query and one frame per interval, not one per vote.
- **Subscriptions.** Community and post topics are checked with the same `CommunityAccess` rules as REST, so members-only
  content never reaches non-members over the socket.

**D-021 Admin dashboard, mutes and write-time spam heuristics (rest of Phase 3).**
- **Admin access is checked three times.** `/api/v1/admin/**` needs the ADMIN role (the matcher now sits before the
  public-GET rule, which previously let admin GETs through to the service), the service re-checks it, and the role
  itself is read from the account on every request. `AccountStatusCache` keeps each account's status and role for 30 s
  and admin actions evict it, so a suspension or demotion applies on the next request instead of when the 15-minute
  access token expires. Suspension also revokes every refresh token, and login refuses suspended accounts (already did).
  On several nodes the cache means up to 30 s of delay. WebSocket sessions are authenticated at CONNECT and are not cut
  by a suspension until they reconnect (the next REST call fails, and the client then signs out).
- **Guard rails.** Admins can't suspend, promote or demote themselves, and an admin must be demoted before being
  suspended, so the dashboard can't be used to lock out the last admin by accident.
- **Append-only audit.** `admin_actions` has a trigger that rejects DELETE and any UPDATE except foreign keys being set
  to NULL (when a referenced row is deleted). Moderator decisions on held content also go to the community's
  `moderation_actions` log, so a community's own log stays complete.
- **Community status.** Quarantine hides a community from discovery (Popular/All tabs, trending, site search,
  suggestions, directory) but keeps it reachable with a banner; ban closes it to everyone but admins and drops its
  posts from every feed. Joined members still see a quarantined community in For You/Following, because they opted in.
  Moderators are notified with the reason.
- **Mute vs block.** Block stays the strong, two-way tool (hides content, stops DMs, removes follows). Mute is private and
  one-way: the muted person's posts leave the muter's feeds, their comments are collapsed (not erased) and they can't
  notify the muter, but they can still interact. A muted community leaves the Home tabs only. Mutes are never exposed on
  public payloads; the client learns them only from `/me/mutes`.
- **Spam heuristics at write time, no ML.** Four cheap checks run inside the post/comment transaction, each a couple of
  indexed lookups on the author's own recent rows:
  - **Link velocity** (new accounts): more than 5 links across posts and comments within 10 min → hold; 20 or more → reject.
  - **Duplicate content**: the same normalised title+body (sha256 of lower-cased text with punctuation and spacing
    collapsed), or the same normalised link from the same author, in 3 or more communities within 1 h → hold; 6 or more → reject.
    Identical bodies of 40+ characters also match across different accounts, which catches simple copy-paste rings.
  - **Posting burst** (new accounts): more than 8 posts and comments within 5 min → hold; more than 30 → reject.
  - **Excessive mentions**: more than 8 distinct @mentions in one item → hold; more than 25 → reject.
  "New" means younger than 7 days. Community moderators and platform admins are exempt. Every number is configurable
  under `strata.spam.*` (`SPAM_HEURISTICS_ENABLED=false` turns them off), and the test profile disables them unless a
  test switches one on.
- **Held, not deleted.** Held content is stored with `pending = TRUE` and `removed = TRUE`, so every existing
  `removed = FALSE` filter (feeds and their partial indexes, AI retrieval, digests, collections, votes) already hides it.
  Only the author and the community's moderators can open it. Counters, notifications and live events are deferred
  until release, so nothing leaks that the content exists. A rejection rolls the write back, but its flag is written in
  its own transaction so admins can see what was refused.
- **What it does not catch.** These are rate and repetition rules. They don't judge content, so a slow, patient spammer,
  rotating links on aged accounts, paraphrased copies, or image-only spam all pass. They can also hold an honest new
  user who shares many links in one go, which is why the outcome is a hold for human review rather than a removal.
  Known gap: an author's pending reply under a comment with no other replies isn't re-shown to them after a reload
  (the reply count is only bumped on release); it still appears in the mod queue and is released normally.
- **Feed mod tools.** `PostSummary.viewer.canModerate` is computed for the whole page with one query (memberships with a
  moderator role in the page's communities, or the viewer is an admin), so feed cards show the existing
  remove/approve/lock/pin menu without per-row requests.
- **Onboarding skip** is now persisted (`POST /onboarding/skip` marks the account as onboarded without changing anything else).
