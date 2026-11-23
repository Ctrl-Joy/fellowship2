# Implementation status

_Last updated: 2026-11-23 (Phases 0–2, layout pass, Phase 3 moderation core, Phase 4, Phase 5 Community AI)._

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
| Notifications (reply, mention, follower, milestone, role; grouped) | ✅ | ✅ polled | API smoke run |
| Rate limiting (Redis, in-memory fallback) | ✅ | — | — |
| Health (db, redis, storage), trace ids, OpenAPI | ✅ | — | manual |
| Demo seed (12 users, 10 communities, threads, real vote rows) | ✅ | — | startup |
| Familiar layout: card/compact views, sort menu, pill actions, collapsible nav (Recent/Communities), recent-posts rail, community banner, avatar thread lines, OP badge | — | ✅ | browser (desktop + 375px) |
| Context Mode: stance groups, evidence, open questions, agreement/disagreement, discussion map, reader relabelling | ✅ | ✅ | KnowledgeLayerIT, TextSignalsTest, browser |
| Thread Digest: 3 lengths × 5 focuses, attributed quotes, fact/opinion/anecdote/speculation tags, honest consensus, cache + staleness + refresh | ✅ | ✅ | KnowledgeLayerIT, browser |
| Community AI: chat with history, community/thread/date scope, permission-filtered retrieval, numbered citations validated server-side, source cards, follow-ups, copy, feedback; Claude via official SDK or offline fallback | ✅ | ✅ | CommunityAiIT, citations.test.ts, browser (offline mode and live Claude `claude-opus-5-5`: ~7–9 s per answer, ~0.8–1.5k input / ~0.5k output tokens, all citations valid) |
| Moderation: reports (dedup per reporter), grouped mod queue, remove/approve/lock/pin (≤3 pins), bans with expiry and rank rules, appeals that reverse actions, append-only audit log, mod stats, affected-user page, user blocking | ✅ | ✅ | ModerationAndPulseIT, live API smoke (23 checks), browser |
| Community Pulse: polls, predictions, opinion and debate prompts; results hidden until you vote; change-vote option; anonymous reasoning; close, resolve with accuracy, history; home and community rails | ✅ | ✅ | ModerationAndPulseIT, live API smoke, browser |
| Smart Collections: private by default, notes, tags, search, type filter, move, reorder (drag + arrows), share link / revoke, shared view | ✅ | ✅ | KnowledgeLayerIT, tags.test.ts, browser |

## Tests (latest run)
- Backend `./mvnw test`: **31 passed, 0 failed** (ApiSecurityIT 6, CommunityAiIT 4, KnowledgeLayerIT 4, ModerationAndPulseIT 4, ScoreMathTest 6, TextSafetyTest 3, TextSignalsTest 3, VoteConcurrencyIT 1).
- Frontend `npx vitest run`: **48 passed** across 8 files; `npm run build` passes with zero type errors.
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
- Not built yet: admin dashboard, mute, spam heuristics and topic reputation (rest of Phase 3), and Phases 6–9 (podcasts, files, funding, leaderboard).
- Mod tools on individual posts and comments appear on the post page only. Feed rows don't carry moderator rights, so feeds don't show them; the mod queue covers them. The UI lists them under "Coming next" and routes them to one
  informational page, with no non-functional controls.
