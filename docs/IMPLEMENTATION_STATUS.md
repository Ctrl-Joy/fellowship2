# Implementation status

_Last updated: 2026-11-23 (Phases 0–2, layout pass, Phase 3 moderation core, Phases 4–6)._

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
| Topic reputation v1: per-topic points from votes (community topic + topic tags), quality-weighted by voter expertise, self-votes ignored, young/low-karma voters discounted, per-pair and per-topic daily caps, removal reversal; queued on vote, recomputed by a job, deterministic nightly/startup rebuild; profile badges, `/topics/:slug/leaders`, thread expertise chips | ✅ | ✅ | ReputationAndRecognitionIT, ReputationMathTest, reputation/logic.test.ts |
| Moderator recognition: monthly quality-weighted score per community (consistency, response time, appeal outcomes, community health, peer endorsements; never removal counts), transparent formula and per-row breakdown, badges, global and per-community leaderboard, profile section, peer "+1 helpful", ranking opt-out | ✅ | ✅ | ReputationAndRecognitionIT, ModeratorScoreMathTest, reputation/logic.test.ts |
| Smart Collections: private by default, notes, tags, search, type filter, move, reorder (drag + arrows), share link / revoke, shared view | ✅ | ✅ | KnowledgeLayerIT, tags.test.ts, browser |

## Tests (latest run)
- Backend `./mvnw test`: **48 passed, 0 failed** (ApiSecurityIT 6, CommunityAiIT 4, DirectMessagesIT 4, KnowledgeLayerIT 4, ModerationAndPulseIT 4, PodcastIT 3, RealtimeIT 5, TranscriptParserTest 5, ScoreMathTest 6, TextSafetyTest 3, TextSignalsTest 3, VoteConcurrencyIT 1).
- Frontend `npx vitest run`: **84 passed** across 12 files; `npm run build` passes with zero type errors.
- API smoke script (47 checks across auth, communities, posts, polls, votes, comments, feeds, search,
  notifications, permissions): **47/47 after fixing the Rising-feed SQL bug it found**.

## Known gaps / not yet verified
- `docker compose up` and the Dockerfiles were **not executed**: Docker isn't installed on the dev machine.
  CI builds the images.
- Pinning and locking posts exist in the schema, but they have no endpoints yet (Phase 3 moderation).
- Skipping onboarding doesn't persist, so the user is offered it again on the next login.
- No Playwright suite yet (Phase 10). No ESLint (the frontend `lint` script is a type-check only).
- Email delivery is log-mode unless SMTP is configured.
- Digest/Context Mode use the offline extractive engine and rule-based stance suggestions; no LLM provider is wired yet (Phase 5).
  On the seeded threads most comments are "neutral" until readers label them, which is expected and honest.
- reddit.com could not be opened for side-by-side comparison (built-in browser blocks it; Claude in Chrome was not connected).
- Not built yet: admin dashboard, mute, spam heuristics and topic reputation (rest of Phase 3), and Phases 7–9 (files, funding, leaderboard). Podcast audio/video generation (TTS) isn't configured, and the S3 storage driver is still pending.
- Real-time broker and presence are in-memory, per instance (D-017): fine for one backend node, not for several.
- DM images use the same unguessable-but-public media URLs as post images (see D-016); messages are not end-to-end encrypted.
- Mod tools on individual posts and comments appear on the post page only. Feed rows don't carry moderator rights, so feeds don't show them; the mod queue covers them. The UI lists them under "Coming next" and routes them to one
  informational page, with no non-functional controls.
- Topic reputation and the moderator leaderboard are built (D-020), which supersedes their mention in the "Not built yet" line above. The demo seed has no moderation history, so moderator leaderboards are empty until moderators act; endorsement notes are stored but only shown to their author. Not verified in a browser (no servers were run for this change).
